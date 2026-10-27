package com.capitec.base.db.repository;

import com.capitec.base.db.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndCustomerId(UUID id, String customerId);

    Page<Transaction> findByCustomerId(String customerId, Pageable pageable);

    /**
     * Inserts a source transaction or updates it when its source data changed. Returns one row:
     * TRUE if inserted, FALSE if updated, and no row if the stored record already matched (unchanged).
     * Category is recalculated only when source fields change, so a rule change does not rewrite history.
     */
    @Transactional
    @Query(value = """
        INSERT INTO transactions (customer_id, provider_id, provider_transaction_id, transaction_date,
                                  description, merchant_name, amount, currency, direction, status, category,
                                  created_at, updated_at)
        VALUES (:customerId, :providerId, :providerTransactionId, :transactionDate,
                :description, :merchantName, :amount, :currency, :direction, :status, :category,
                now(), now())
        ON CONFLICT (customer_id, provider_id, provider_transaction_id) DO UPDATE SET
            transaction_date = EXCLUDED.transaction_date,
            description = EXCLUDED.description,
            merchant_name = EXCLUDED.merchant_name,
            amount = EXCLUDED.amount,
            currency = EXCLUDED.currency,
            direction = EXCLUDED.direction,
            status = EXCLUDED.status,
            category = EXCLUDED.category,
            updated_at = now()
        WHERE (transactions.transaction_date, transactions.description, transactions.merchant_name,
               transactions.amount, transactions.currency, transactions.direction, transactions.status)
              IS DISTINCT FROM
              (EXCLUDED.transaction_date, EXCLUDED.description, EXCLUDED.merchant_name,
               EXCLUDED.amount, EXCLUDED.currency, EXCLUDED.direction, EXCLUDED.status)
        RETURNING (xmax = 0)
        """, nativeQuery = true)
    List<Boolean> upsertFromSource(
            @Param("customerId") String customerId,
            @Param("providerId") String providerId,
            @Param("providerTransactionId") String providerTransactionId,
            @Param("transactionDate") Instant transactionDate,
            @Param("description") String description,
            @Param("merchantName") String merchantName,
            @Param("amount") BigDecimal amount,
            @Param("currency") String currency,
            @Param("direction") String direction,
            @Param("status") String status,
            @Param("category") String category
    );
}
