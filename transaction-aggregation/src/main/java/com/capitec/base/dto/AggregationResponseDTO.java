package com.capitec.base.dto;

import com.capitec.base.constant.AggregationOutcome;
import com.capitec.base.constant.ProviderOutcome;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record AggregationResponseDTO(
        String status,
        int newTransactions,
        int updatedTransactions,
        int duplicateTransactions,
        int invalidTransactions,
        Map<String, ProviderOutcomeDTO> providerOutcomes,
        String message,
        Instant timestamp
) {
    public static AggregationResponseDTO fromOutcome(AggregationOutcome outcome) {
        Map<String, ProviderOutcomeDTO> providers = new LinkedHashMap<>();
        outcome.providerOutcomes().forEach((id, p) -> providers.put(id, ProviderOutcomeDTO.fromOutcome(p)));
        return new AggregationResponseDTO(
                outcome.status().name(),
                outcome.newTransactions(),
                outcome.updatedTransactions(),
                outcome.duplicateTransactions(),
                outcome.invalidTransactions(),
                providers,
                message(outcome),
                Instant.now()
        );
    }

    private static String message(AggregationOutcome outcome) {
        long failed = outcome.providerOutcomes().values().stream().filter(p -> !p.success()).count();
        return switch (outcome.status()) {
            case COMPLETE_SUCCESS -> "All providers succeeded.";
            case EMPTY_SUCCESS -> "All providers succeeded and returned no transactions.";
            case PARTIAL_SUCCESS -> "Incomplete result: " + failed + " of " + outcome.providerOutcomes().size()
                    + " providers failed. Transactions from the successful providers were saved.";
            case COMPLETE_FAILURE -> "No provider could be aggregated; nothing was saved.";
        };
    }

    public record ProviderOutcomeDTO(
            String providerId,
    boolean success,
    int transactionCount,
    String status,
    String errorMessage
    ) {
        static ProviderOutcomeDTO fromOutcome(ProviderOutcome outcome) {
            return new ProviderOutcomeDTO(
                    outcome.providerId(),
                    outcome.success(),
                    outcome.transactionCount(),
                    outcome.status(),
                    outcome.errorMessage()
            );
        }
    }
}
