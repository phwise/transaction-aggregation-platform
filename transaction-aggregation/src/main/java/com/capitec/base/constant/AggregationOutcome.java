package com.capitec.base.constant;


import com.capitec.base.constant.AggregationStatus;

import java.util.Map;

public record AggregationOutcome(
        AggregationStatus status,
        int newTransactions,
        int updatedTransactions,
        int duplicateTransactions,
        int invalidTransactions,
        Map<String, ProviderOutcome> providerOutcomes
) {
}
