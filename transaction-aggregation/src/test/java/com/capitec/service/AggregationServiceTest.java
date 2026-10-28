package com.capitec.service;

import com.capitec.base.constant.AggregationOutcome;
import com.capitec.base.constant.AggregationStatus;
import com.capitec.base.dto.ProviderTransactionDTO;
import com.capitec.base.db.repository.TransactionRepository;
import com.capitec.providers.ProviderResponse;
import com.capitec.providers.TransactionProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AggregationServiceTest {

    private static final String CUSTOMER = "alice-01";
    private static final String KEY = "key-1";
    private static final Duration PROVIDER_TIMEOUT = Duration.ofMillis(300);
    private static final Instant DATE = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private TransactionRepository repository;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void allProvidersSucceedingPersistsEveryRecord() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(
                provider("snapbank", () -> success(valid("s1"), valid("s2"))),
                provider("cashflow", () -> success(valid("c1"), valid("c2"))));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.COMPLETE_SUCCESS);
        assertThat(outcome.newTransactions()).isEqualTo(4);
        assertThat(outcome.invalidTransactions()).isZero();
        assertThat(outcome.duplicateTransactions()).isZero();
        assertThat(outcome.providerOutcomes().get("snapbank").success()).isTrue();
        assertThat(outcome.providerOutcomes().get("snapbank").transactionCount()).isEqualTo(2);
        assertThat(persistedProviderTransactionIds()).containsExactlyInAnyOrder("s1", "s2", "c1", "c2");
    }

    @Test
    void providersReturningNothingIsAnEmptySuccessAndWritesNothing() {
        AggregationService service = serviceWith(
                provider("snapbank", () -> success()),
                provider("cashflow", () -> success()));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.EMPTY_SUCCESS);
        verifyNoInteractions(repository);
    }

    @Test
    void oneFailingProviderIsPartialSuccessAndHealthyRecordsAreStillSaved() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(
                provider("snapbank", () -> success(valid("s1"))),
                provider("cashflow", () -> ProviderResponse.error("connection refused")));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.PARTIAL_SUCCESS);
        assertThat(outcome.newTransactions()).isEqualTo(1);
        assertThat(outcome.providerOutcomes().get("cashflow").success()).isFalse();
        assertThat(outcome.providerOutcomes().get("cashflow").status()).isEqualTo("ERROR");
        assertThat(outcome.providerOutcomes().get("cashflow").errorMessage()).isEqualTo("Provider is unavailable");
        assertThat(persistedProviderTransactionIds()).containsExactly("s1");
    }

    @Test
    void allProvidersFailingIsCompleteFailureAndWritesNothing() {
        AggregationService service = serviceWith(
                provider("snapbank", () -> ProviderResponse.error("timeout upstream")),
                provider("cashflow", () -> ProviderResponse.error("connection refused")));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.COMPLETE_FAILURE);
        verifyNoInteractions(repository);
    }

    @Test
    void slowProviderTimesOutWithoutDelayingTheOthers() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(
                provider("snapbank", () -> success(valid("s1"))),
                provider("cashflow", () -> slowly(Duration.ofSeconds(10), success(valid("late")))));

        long startedNanos = System.nanoTime();
        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.PARTIAL_SUCCESS);
        assertThat(outcome.providerOutcomes().get("cashflow").status()).isEqualTo("TIMEOUT");
        assertThat(outcome.providerOutcomes().get("snapbank").success()).isTrue();
        assertThat(outcome.newTransactions()).isEqualTo(1);
        assertThat(persistedProviderTransactionIds()).containsExactly("s1");
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void providerThatThrowsIsIsolatedFromTheOthers() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(
                provider("snapbank", () -> {
                    throw new IllegalStateException("boom");
                }),
                provider("cashflow", () -> success(valid("c1"), valid("c2"))));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.PARTIAL_SUCCESS);
        assertThat(outcome.providerOutcomes().get("snapbank").success()).isFalse();
        assertThat(outcome.newTransactions()).isEqualTo(2);
    }

    @Test
    void invalidRecordsAreSkippedAndCounted() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(provider("snapbank", () -> success(
                valid("ok"),
                valid("   "),
                valid("x".repeat(101)),
                txn("zero", BigDecimal.ZERO, "ZAR", "DEBIT", "COMPLETED"),
                txn("negative", new BigDecimal("-1.00"), "ZAR", "DEBIT", "COMPLETED"),
                txn("three-dp", new BigDecimal("1.005"), "ZAR", "DEBIT", "COMPLETED"),
                txn("bad-currency", new BigDecimal("10.00"), "za", "DEBIT", "COMPLETED"),
                txn("bad-direction", new BigDecimal("10.00"), "ZAR", "SIDEWAYS", "COMPLETED"),
                txn("bad-status", new BigDecimal("10.00"), "ZAR", "DEBIT", "LOST"),
                new ProviderTransactionDTO("no-date", null, "Checkers", "Checkers",
                        new BigDecimal("10.00"), "ZAR", "DEBIT", "COMPLETED"),
                txn("long-text", new BigDecimal("10.00"), "ZAR", "DEBIT", "COMPLETED", "x".repeat(501)))));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.status()).isEqualTo(AggregationStatus.COMPLETE_SUCCESS);
        assertThat(outcome.newTransactions()).isEqualTo(1);
        assertThat(outcome.invalidTransactions()).isEqualTo(10);
        assertThat(persistedProviderTransactionIds()).containsExactly("ok");
    }

    @Test
    void duplicateRecordsWithinOneRunArePersistedOnce() {
        givenEveryWriteInserts();
        AggregationService service = serviceWith(provider("snapbank",
                () -> success(valid("dup"), valid("dup"))));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.newTransactions()).isEqualTo(1);
        assertThat(outcome.duplicateTransactions()).isEqualTo(1);
        assertThat(persistedProviderTransactionIds()).containsExactly("dup");
    }

    @Test
    void changedRecordsAreUpdatedAndUnchangedRecordsAreCountedAsDuplicates() {
        when(repository.upsertFromSource(any(), any(), eq("changed"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(false));
        when(repository.upsertFromSource(any(), any(), eq("same"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        AggregationService service = serviceWith(provider("snapbank",
                () -> success(valid("changed"), valid("same"))));

        AggregationOutcome outcome = service.aggregateTransactions(CUSTOMER, KEY);

        assertThat(outcome.updatedTransactions()).isEqualTo(1);
        assertThat(outcome.duplicateTransactions()).isEqualTo(1);
        assertThat(outcome.newTransactions()).isZero();
    }

    private AggregationService serviceWith(TransactionProvider... providers) {
        return new AggregationService(
                List.of(providers),
                executor,
                PROVIDER_TIMEOUT,
                repository,
                new CategorizationService(),
                new SimpleMeterRegistry());
    }

    private void givenEveryWriteInserts() {
        when(repository.upsertFromSource(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(true));
    }

    private List<String> persistedProviderTransactionIds() {
        ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
        verify(repository, atLeast(0)).upsertFromSource(
                any(), any(), ids.capture(), any(), any(), any(), any(), any(), any(), any(), any());
        return ids.getAllValues();
    }

    private static TransactionProvider provider(String id, Supplier<ProviderResponse> behaviour) {
        return new FakeProvider(id, behaviour);
    }

    private static ProviderResponse success(ProviderTransactionDTO... records) {
        return ProviderResponse.success(List.of(records));
    }

    private static ProviderTransactionDTO valid(String providerTransactionId) {
        return txn(providerTransactionId, new BigDecimal("125.50"), "ZAR", "DEBIT", "COMPLETED");
    }

    private static ProviderTransactionDTO txn(String providerTransactionId, BigDecimal amount,
                                              String currency, String direction, String status) {
        return txn(providerTransactionId, amount, currency, direction, status, "Checkers Sixty60");
    }

    private static ProviderTransactionDTO txn(String providerTransactionId, BigDecimal amount,
                                              String currency, String direction, String status, String description) {
        return new ProviderTransactionDTO(providerTransactionId, DATE, description, "Checkers",
                amount, currency, direction, status);
    }

    private static ProviderResponse slowly(Duration delay, ProviderResponse response) {
        try {
            Thread.sleep(delay.toMillis());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("provider call interrupted", e);
        }
    }

    private record FakeProvider(String providerId, Supplier<ProviderResponse> behaviour)
            implements TransactionProvider {

        @Override
        public String getProviderId() {
            return providerId;
        }

        @Override
        public ProviderResponse fetchTransactions(String customerId) {
            return behaviour.get();
        }
    }
}