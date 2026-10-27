package com.capitec.base.constant;

public record ProviderOutcome(
        String providerId,
        boolean success,
        int transactionCount,
        String status,
        String errorMessage
) {
    public static ProviderOutcome success(String providerId, int transactionCount) {
        return new ProviderOutcome(providerId, true, transactionCount, "SUCCESS", null);
    }

    public static ProviderOutcome timeout(String providerId) {
        return new ProviderOutcome(providerId, false, 0, "TIMEOUT", "Provider request timed out");
    }

    public static ProviderOutcome error(String providerId, String errorMessage) {
        return new ProviderOutcome(providerId, false, 0, "ERROR", errorMessage);
    }
}