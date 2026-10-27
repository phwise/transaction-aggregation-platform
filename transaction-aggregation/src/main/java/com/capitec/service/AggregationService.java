package com.capitec.service;

import com.capitec.base.constant.AggregationOutcome;
import com.capitec.base.constant.ProviderOutcome;
import com.capitec.base.constant.AggregationStatus;
import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.domain.TransactionStatus;
import com.capitec.base.db.repository.TransactionRepository;
import com.capitec.providers.ProviderResponse;
import com.capitec.base.constant.ProviderStatus;
import com.capitec.base.dto.ProviderTransactionDTO;
import com.capitec.providers.TransactionProvider;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Aggregation workflow.
 *
 * Providers are called concurrently on a bounded pool under one deadline, so a slow provider cannot
 * delay the others past the timeout. Each provider is isolated: a failure or timeout in one does not
 * discard another's records. No database transaction is open during provider calls; each record is
 * written by a single upsert, which is atomic and safe under concurrent runs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    private static final int MAX_PROVIDER_TRANSACTION_ID_LENGTH = 100;
    private static final int MAX_TEXT_LENGTH = 500;

    private final List<TransactionProvider> providers;
    private final ExecutorService providerExecutor;
    private final Duration providerTimeout;
    private final TransactionRepository repository;
    private final CategorizationService categorizationService;
    private final MeterRegistry meters;


    public AggregationOutcome aggregateTransactions(final String customerId,final String idempotencyKey ) {
        log.atInfo()
                .addKeyValue("idempotencyKey", idempotencyKey)
                .log("Transaction aggregation started");
        try{

            Map<String, InFlightCall> calls = submitAll(customerId,idempotencyKey);
            long deadline = System.nanoTime() + providerTimeout.toNanos();

            Map<String, ProviderOutcome> outcomes = new LinkedHashMap<>();
            Map<String, List<ProviderTransactionDTO>> received = new LinkedHashMap<>();
            for (TransactionProvider provider : providers) {

                String providerId = provider.getProviderId();
                log.atDebug()
                        .addKeyValue("idempotencyKey", idempotencyKey)
                        .log("Collecting provider result {}",providerId);

                ProviderResponse response = collect(providerId,
                        idempotencyKey,
                        calls.get(providerId),
                        deadline);

                meters.counter("provider.calls",
                        "provider",
                        providerId,
                        "outcome",
                        response.status().name()
                ).increment();

                if (response.status() == ProviderStatus.SUCCESS) {

                    outcomes.put(providerId,
                            ProviderOutcome.success(providerId,
                                    response.transactions().size()));
                    received.put(providerId,
                            response.transactions());
                    log.atInfo()
                            .addKeyValue("idempotencyKey", idempotencyKey)
                            .log("Provider transactions received");


                } else if (response.status() == ProviderStatus.TIMEOUT) {
                    outcomes.put(providerId, ProviderOutcome.timeout(providerId));
                    log.atWarn()
                            .addKeyValue("idempotencyKey", idempotencyKey)
                            .log("Provider did not respond before the deadline");

                } else {
                    outcomes.put(providerId, ProviderOutcome.error(providerId, "Provider is unavailable"));
                    log.atWarn()
                            .addKeyValue("idempotencyKey", idempotencyKey)
                            .log("Provider returned an unsuccessful result");

                }
            }

            Tally tally = new Tally();

            received.forEach((providerId, records) -> persistAll(customerId, providerId,idempotencyKey, records, tally));

            AggregationStatus status = determineStatus(outcomes, tally);

            meters.counter("aggregation.outcomes",
                    "status",
                    status.name()).increment();
            recordTallies(tally);

            log.atInfo()
                    .addKeyValue("idempotencyKey", idempotencyKey)
                    .log("Transaction aggregation completed");


            return new AggregationOutcome(status,
                    tally.created,
                    tally.updated,
                    tally.duplicates,
                    tally.invalid,
                    outcomes);
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("idempotencyKey", idempotencyKey)

                    .setCause(e)
                    .log("Transaction aggregation failed unexpectedly");
            throw e;
        }


    }

    private Map<String, InFlightCall> submitAll(final String customerId,final String idempotencyKey) {

        log.atInfo()
                .addKeyValue("idempotencyKey",idempotencyKey)
                .log("Submitting transaction aggregation");

        Map<String, InFlightCall> calls = new LinkedHashMap<>();

        for (TransactionProvider provider : providers) {

            long startedNanos = System.nanoTime();
            Future<ProviderResponse> future;
            try {
                future = providerExecutor.submit(() -> invoke(provider, customerId,idempotencyKey));
            } catch (RejectedExecutionException e) {
                log.atWarn()
                        .addKeyValue("idempotencyKey",idempotencyKey)
                        .log("Provider {} rejected: aggregation capacity exhausted", provider.getProviderId());
                future = CompletableFuture.completedFuture(ProviderResponse.error("capacity exhausted"));
            }
            calls.put(provider.getProviderId(), new InFlightCall(future, startedNanos));
        }
        return calls;
    }

    private ProviderResponse collect(final String providerId,final String idempotencyKey, InFlightCall call, long deadlineNanos) {
        try {

            long remainingNanos = Math.max(0L, deadlineNanos - System.nanoTime());
            ProviderResponse response = call.future().get(remainingNanos, TimeUnit.NANOSECONDS);
            meters.timer("provider.call.duration", "provider", providerId)
                    .record(Duration.ofNanos(System.nanoTime() - call.startedNanos()));
            return response;
        } catch (TimeoutException e) {
            call.future().cancel(true);
            log.atWarn()
            .addKeyValue("idempotencyKey",idempotencyKey)
            .log("Provider {} exceeded the {} timeout", providerId, providerTimeout);
            return ProviderResponse.timeout();
        } catch (ExecutionException e) {
            log.atError()
                    .addKeyValue("idempotencyKey",idempotencyKey)
                    .log("Provider {} failed unexpectedly", providerId, e.getCause());
            return ProviderResponse.error("unexpected failure");
        } catch (CancellationException e) {
            return ProviderResponse.error("cancelled");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.future().cancel(true);
            return ProviderResponse.error("interrupted");
        }
    }

    private ProviderResponse invoke(final TransactionProvider provider, final String customerId, final String idempotencyKey ) {
        try {
            log.atInfo()
                    .addKeyValue("idempotencyKey",idempotencyKey)
                    .log("Invoking transaction aggregation for provider {}",provider.getClass());
            ProviderResponse response = provider.fetchTransactions(customerId);
            log.atDebug()
                    .addKeyValue("idempotencyKey",idempotencyKey)
                    .log("Response from provider {}",response);
            return response == null ? ProviderResponse.error("null response") : response;
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue("idempotencyKey",idempotencyKey)
                    .log("Provider {} threw an exception", provider.getProviderId(), e);
            return ProviderResponse.error(e.getClass().getSimpleName());
        }
    }

    private void persistAll(final String customerId, final String providerId,final String idempotencyKey,  List<ProviderTransactionDTO> records, Tally tally) {
        log.atInfo()
                .addKeyValue("idempotencyKey",idempotencyKey)
                .log("Persisting all transaction for provider  {}",providerId);
        Set<String> seenInThisRun = new HashSet<>();
        for (ProviderTransactionDTO record : records) {
            tally.returned++;
            if (!isValid(record)) {
                tally.invalid++;
                continue;
            }
            if (!seenInThisRun.add(record.providerTransactionId())) {
                tally.duplicates++;
                continue;
            }
            persist(customerId, providerId,idempotencyKey, record, tally);
        }
    }

    private void persist(final String customerId, final String providerId,final String idempotencyKey, ProviderTransactionDTO record, Tally tally) {
        log.atInfo()
                .addKeyValue("idempotencyKey",idempotencyKey)
                .log("Persisting all transaction for provider  {}",providerId);

        TransactionDirection direction = TransactionDirection.valueOf(record.direction());
        TransactionStatus status = TransactionStatus.valueOf(record.status());
        TransactionCategory category = categorizationService.categorize(record.description(), record.merchantName(), direction);

        List<Boolean> inserted = repository.upsertFromSource(
                customerId,
                providerId,
                record.providerTransactionId(),
                record.transactionDate(),
                record.description(),
                record.merchantName(),
                record.amount(),
                record.currency(),
                direction.name(),
                status.name(),
                category.name()
        );

        if (inserted.isEmpty()) {
            tally.duplicates++;
        } else if (inserted.get(0)) {
            tally.created++;
        } else {
            tally.updated++;
        }
    }

    private void recordTallies(Tally tally) {
        meters.counter("transactions.processed", "result", "created").increment(tally.created);
        meters.counter("transactions.processed", "result", "updated").increment(tally.updated);
        meters.counter("transactions.processed", "result", "duplicate").increment(tally.duplicates);
        meters.counter("transactions.processed", "result", "invalid").increment(tally.invalid);
    }

    private boolean isValid(ProviderTransactionDTO record) {
        return isNonBlankWithin(record.providerTransactionId(), MAX_PROVIDER_TRANSACTION_ID_LENGTH)
                && isWithin(record.description(), MAX_TEXT_LENGTH)
                && isWithin(record.merchantName(), MAX_TEXT_LENGTH)
                && record.transactionDate() != null
                && isPositiveMoney(record.amount())
                && record.currency() != null && record.currency().matches("[A-Z]{3}")
                && isEnumValue(TransactionDirection.class, record.direction())
                && isEnumValue(TransactionStatus.class, record.status());
    }

    private static boolean isNonBlankWithin(String value, int maxLength) {
        return value != null && !value.isBlank() && value.length() <= maxLength;
    }

    private static boolean isWithin(String value, int maxLength) {
        return value == null || value.length() <= maxLength;
    }

    private static boolean isPositiveMoney(BigDecimal amount) {
        return amount != null && amount.signum() > 0 && amount.scale() <= 2;
    }

    private static <E extends Enum<E>> boolean isEnumValue(Class<E> type, String value) {
        if (value == null) {
            return false;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return true;
            }
        }
        return false;
    }

    private AggregationStatus determineStatus(Map<String, ProviderOutcome> outcomes, Tally tally) {
        long succeeded = outcomes.values().stream().filter(ProviderOutcome::success).count();
        if (succeeded == 0) {
            return AggregationStatus.COMPLETE_FAILURE;
        }
        if (succeeded < outcomes.size()) {
            return AggregationStatus.PARTIAL_SUCCESS;
        }
        return tally.returned == 0 ? AggregationStatus.EMPTY_SUCCESS : AggregationStatus.COMPLETE_SUCCESS;
    }

    private record InFlightCall(Future<ProviderResponse> future, long startedNanos) {
    }

    private static final class Tally {
        int returned;
        int created;
        int updated;
        int duplicates;
        int invalid;
    }
}