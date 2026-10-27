package com.capitec.base.dto;

import com.capitec.base.db.entity.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponseDTO(
        UUID id,
        String customerId,
        String providerId,
        String providerTransactionId,
        Instant transactionDate,
        String description,
        String merchantName,
        BigDecimal amount,
        String currency,
        String direction,
        String status,
        String category,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionResponseDTO fromEntity(Transaction transaction) {
        return new TransactionResponseDTO(
                transaction.getId(),
                transaction.getCustomerId(),
                transaction.getProviderId(),
                transaction.getProviderTransactionId(),
                transaction.getTransactionDate(),
                transaction.getDescription(),
                transaction.getMerchantName(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDirection().name(),
                transaction.getStatus().name(),
                transaction.getCategory() != null ? transaction.getCategory().name() : null,
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}

