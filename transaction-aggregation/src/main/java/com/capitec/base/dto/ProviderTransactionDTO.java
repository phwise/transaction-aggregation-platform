package com.capitec.base.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized transaction data from a provider.
 * Represents the internal canonical format after provider-specific normalization.
 */
public record ProviderTransactionDTO(
        String providerTransactionId,
        Instant transactionDate,
        String description,
        String merchantName,
        BigDecimal amount,
        String currency,
        String direction,  // "DEBIT" or "CREDIT"
        String status     // "COMPLETED", "PENDING", "FAILED", etc.
) {}
