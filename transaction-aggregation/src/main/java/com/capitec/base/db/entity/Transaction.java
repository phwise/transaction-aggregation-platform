package com.capitec.base.db.entity;


import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.domain.TransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_customer_created", columnList = "customer_id,created_at"),
                @Index(name = "idx_provider_provider_id", columnList = "provider_id,provider_transaction_id"),
                @Index(name = "idx_customer_date", columnList = "customer_id,transaction_date"),
                @Index(name = "idx_customer_category", columnList = "customer_id,category")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_customer_provider_tx_id",
                        columnNames = {"customer_id", "provider_id", "provider_transaction_id"}
                )
        }
)
@Data
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false, length = 50)
    private String customerId;

    @Column(name = "provider_id", nullable = false, updatable = false, length = 50)
    private String providerId;

    @Column(name = "provider_transaction_id", nullable = false, updatable = false, length = 100)
    private String providerTransactionId;

    @Column(name = "transaction_date", nullable = false)
    private Instant transactionDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "merchant_name", length = 500)
    private String merchantName;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 50)
    private TransactionCategory category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}