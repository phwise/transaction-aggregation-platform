package com.capitec.service;

import com.capitec.base.constant.IdempotentResult;
import com.capitec.base.constant.StoredResponse;

import com.capitec.base.exception.IdempotencyConflictException;
import com.capitec.base.exception.IdempotencyKeyReusedException;
import com.capitec.base.db.entity.IdempotencyRecord;
import com.capitec.base.db.repository.IdempotencyRecordRepository;
import com.capitec.base.constant.IdempotencyStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Idempotency for customer operations, scoped by (customer, operation, key).
 *
 * - First request claims the key, runs the operation and stores the response.
 * - Same key and same request fingerprint after completion: the stored response is replayed.
 * - Same key with a different fingerprint: rejected with 422.
 * - Same key while the first request is in flight: rejected with 409.
 * - Server errors (5xx) and exceptions release the key, so a retry runs the operation again.
 * - A claim whose lease expired (the process died mid-operation) can be taken over by a retry with the same fingerprint.
 *
 * Idempotency keys are not held in a database transaction while the operation runs.
 */
@Service
@Slf4j
public class IdempotencyService {

    private static final int MAX_CLAIM_ATTEMPTS = 3;

    private final IdempotencyRecordRepository repository;
    private final int leaseSeconds;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            @Value("${app.idempotency.lease-seconds}") int leaseSeconds
    ) {
        this.repository = repository;
        this.leaseSeconds = leaseSeconds;
    }

    public IdempotentResult execute(
            String customerId,
            String operation,
            String idempotencyKey,
            String canonicalRequest,
            Supplier<StoredResponse> action
    ) {

        log.atInfo()
                .addKeyValue("id",idempotencyKey).
                log("Entered IdempotencyService.execute");

        if (idempotencyKey == null) {
            log.atInfo()
                    .addKeyValue("id",idempotencyKey).
                    log("key is null");
            return new IdempotentResult(action.get(), false);
        }
        String fingerprint = sha256Hex(canonicalRequest);

        for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS; attempt++) {
            log.atInfo()
                    .addKeyValue("id",idempotencyKey).
                    log("Attempting idempotency claim {}", attempt + 1);

            Optional<UUID> claimed = claim(customerId, operation, idempotencyKey, fingerprint);

            log.atInfo()
                    .addKeyValue("id",idempotencyKey).
                    log("Claim acquired={}", claimed.isPresent());

            if (claimed.isPresent()) {
                log.atInfo()
                        .addKeyValue("id",idempotencyKey).
                        log("Claim acquired; executing action");
                return run(claimed.get(), action);
            }
            log.info("Looking up existing idempotency record");
            Optional<IdempotencyRecord> existing = repository.findByCustomerIdAndOperationAndIdempotencyKey(
                    customerId, operation, idempotencyKey);

            log.atInfo()
                    .addKeyValue("id",idempotencyKey).
                    log("Existing record found={}", existing.isPresent());

            if (existing.isEmpty()) {

                continue;
            }
            IdempotencyRecord record = existing.get();
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                log.atWarn()
                        .addKeyValue("id",idempotencyKey).
                        log("Idempotency key reused with a different request");

                throw new IdempotencyKeyReusedException();
            }
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                log.atInfo()
                        .addKeyValue("id",idempotencyKey).
                        log("Returning previously completed response");
                return new IdempotentResult(new StoredResponse(record.getResponseStatus(), record.getResponseBody()), true);
            }
            log.atInfo()
                    .addKeyValue("id",idempotencyKey).
                    log("Idempotency record is still in progress");
            throw new IdempotencyConflictException();
        }
        log.atWarn()
                .addKeyValue("id",idempotencyKey).
                log("Could not acquire idempotency claim after retries");

        throw new IdempotencyConflictException();
    }

    private Optional<UUID> claim(String customerId, String operation, String idempotencyKey, String fingerprint) {
        UUID id = UUID.randomUUID();
        return repository.tryAcquire(id,customerId, operation, idempotencyKey, fingerprint, leaseSeconds)
                .stream()
                .findFirst();
    }

    private IdempotentResult run(UUID recordId, Supplier<StoredResponse> action) {
        StoredResponse response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            repository.release(recordId);
            throw e;
        }
        if (response.status() >= 500) {
            repository.release(recordId);
        } else {
            repository.markCompleted(recordId, response.status(), response.body());
        }
        return new IdempotentResult(response, false);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
