package com.capitec.service;

import com.capitec.base.constant.IdempotencyStatus;
import com.capitec.base.constant.IdempotentResult;
import com.capitec.base.constant.StoredResponse;
import com.capitec.base.db.entity.IdempotencyRecord;
import com.capitec.base.db.repository.IdempotencyRecordRepository;
import com.capitec.base.exception.IdempotencyConflictException;
import com.capitec.base.exception.IdempotencyKeyReusedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String CUSTOMER = "alice-01";
    private static final String OPERATION = "AGGREGATE_TRANSACTIONS";
    private static final String KEY = "key-1";
    private static final String REQUEST = "AGGREGATE_TRANSACTIONS;v1;no-body";
    private static final int LEASE_SECONDS = 300;

    @Mock
    private IdempotencyRecordRepository repository;

    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository, LEASE_SECONDS);
    }

    @Test
    void withoutAKeyTheActionRunsAndNothingIsStored() {
        IdempotentResult result = service.execute(CUSTOMER, OPERATION, null, REQUEST,
                () -> new StoredResponse(200, "{}"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(new StoredResponse(200, "{}"));
        verifyNoInteractions(repository);
    }

    @Test
    void newKeyClaimsRunsAndStoresTheResponse() {
        UUID recordId = UUID.randomUUID();
        when(repository.tryAcquire(any(), eq(CUSTOMER), eq(OPERATION), eq(KEY), anyString(), eq(LEASE_SECONDS)))
                .thenReturn(List.of(recordId));

        IdempotentResult result = service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> new StoredResponse(200, "{\"ok\":true}"));

        assertThat(result.replayed()).isFalse();
        assertThat(result.response()).isEqualTo(new StoredResponse(200, "{\"ok\":true}"));
        verify(repository).markCompleted(recordId, 200, "{\"ok\":true}");
        verify(repository, never()).release(any());
    }

    @Test
    void theRequestFingerprintIsTheSha256OfTheCanonicalRequest() {
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(UUID.randomUUID()));

        service.execute(CUSTOMER, OPERATION, KEY, "abc", () -> new StoredResponse(200, "{}"));

        verify(repository).tryAcquire(any(), eq(CUSTOMER), eq(OPERATION), eq(KEY),
                eq("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), eq(LEASE_SECONDS));
    }

    @Test
    void serverErrorReleasesTheClaimSoARetryCanRunAgain() {
        UUID recordId = UUID.randomUUID();
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(recordId));

        IdempotentResult result = service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> new StoredResponse(503, "{\"error\":\"down\"}"));

        assertThat(result.response().status()).isEqualTo(503);
        verify(repository).release(recordId);
        verify(repository, never()).markCompleted(any(), anyInt(), any());
    }

    @Test
    void clientErrorIsStoredAndReplayed() {
        UUID recordId = UUID.randomUUID();
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(recordId));

        service.execute(CUSTOMER, OPERATION, KEY, REQUEST, () -> new StoredResponse(400, "{}"));

        verify(repository).markCompleted(recordId, 400, "{}");
        verify(repository, never()).release(any());
    }

    @Test
    void exceptionFromTheActionReleasesTheClaimAndIsRethrown() {
        UUID recordId = UUID.randomUUID();
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt()))
                .thenReturn(List.of(recordId));

        assertThatThrownBy(() -> service.execute(CUSTOMER, OPERATION, KEY, REQUEST, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        verify(repository).release(recordId);
        verify(repository, never()).markCompleted(any(), anyInt(), any());
    }

    @Test
    void completedRecordWithTheSameFingerprintReplaysTheStoredResponse() {
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(List.of());
        IdempotencyRecord record = mock(IdempotencyRecord.class);
        when(record.getRequestFingerprint()).thenReturn(sha256(REQUEST));
        when(record.getStatus()).thenReturn(IdempotencyStatus.COMPLETED);
        when(record.getResponseStatus()).thenReturn(201);
        when(record.getResponseBody()).thenReturn("{\"id\":1}");
        when(repository.findByCustomerIdAndOperationAndIdempotencyKey(CUSTOMER, OPERATION, KEY))
                .thenReturn(Optional.of(record));

        IdempotentResult result = service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> {
                    throw new AssertionError("the action must not run on replay");
                });

        assertThat(result.replayed()).isTrue();
        assertThat(result.response()).isEqualTo(new StoredResponse(201, "{\"id\":1}"));
    }

    @Test
    void sameKeyWithADifferentRequestIsRejected() {
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(List.of());
        IdempotencyRecord record = mock(IdempotencyRecord.class);
        when(record.getRequestFingerprint()).thenReturn(sha256("some-other-request"));
        when(repository.findByCustomerIdAndOperationAndIdempotencyKey(CUSTOMER, OPERATION, KEY))
                .thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> new StoredResponse(200, "{}")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void sameKeyWhileTheFirstRequestIsInFlightIsAConflict() {
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(List.of());
        IdempotencyRecord existing = mock(IdempotencyRecord.class);
        when(existing.getRequestFingerprint()).thenReturn(sha256(REQUEST));
        when(existing.getStatus()).thenReturn(IdempotencyStatus.IN_PROGRESS);
        when(repository.findByCustomerIdAndOperationAndIdempotencyKey(CUSTOMER, OPERATION, KEY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> new StoredResponse(200, "{}")))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void claimThatNeverBecomesAvailableFailsAfterBoundedRetries() {
        when(repository.tryAcquire(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(List.of());
        when(repository.findByCustomerIdAndOperationAndIdempotencyKey(CUSTOMER, OPERATION, KEY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(CUSTOMER, OPERATION, KEY, REQUEST,
                () -> new StoredResponse(200, "{}")))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(repository, times(3)).tryAcquire(any(), any(), any(), any(), anyString(), anyInt());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}