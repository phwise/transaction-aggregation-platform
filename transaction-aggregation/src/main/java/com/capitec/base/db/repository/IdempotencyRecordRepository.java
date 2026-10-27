package com.capitec.base.db.repository;

import com.capitec.base.db.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Claims the key for this request. Returns the new record ID when claimed. Returns nothing when
     * the key exists with a different fingerprint, is still leased, or is completed. A stale lease is
     * taken over only when the fingerprint matches, so a different request can never reuse it.
     */
    @Transactional
    @Query(value = """
        INSERT INTO idempotency_keys (id ,customer_id, operation, idempotency_key, request_fingerprint,
                                      status, lease_expires_at)
        VALUES (:id ,:customerId, :operation, :idempotencyKey, :fingerprint, 'IN_PROGRESS',
                now() + make_interval(secs => CAST(:leaseSeconds AS double precision)))
        ON CONFLICT (customer_id, operation, idempotency_key) DO UPDATE SET
            status = 'IN_PROGRESS',
            lease_expires_at = EXCLUDED.lease_expires_at
        WHERE idempotency_keys.status = 'IN_PROGRESS'
          AND idempotency_keys.lease_expires_at < now()
          AND idempotency_keys.request_fingerprint = EXCLUDED.request_fingerprint
        RETURNING id
        """, nativeQuery = true)
    List<UUID> tryAcquire(
            @Param("id") UUID id,
            @Param("customerId") String customerId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("fingerprint") String fingerprint,
            @Param("leaseSeconds") int leaseSeconds
    );

    @Transactional
    @Modifying
    @Query(value = """
        UPDATE idempotency_keys
        SET status = 'COMPLETED', response_status = :responseStatus, response_body = :responseBody,
            completed_at = now(), lease_expires_at = now()
        WHERE id = :id AND status = 'IN_PROGRESS'
        """, nativeQuery = true)
    int markCompleted(@Param("id") UUID id, @Param("responseStatus") int responseStatus, @Param("responseBody") String responseBody);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE id = :id AND status = 'IN_PROGRESS'", nativeQuery = true)
    int release(@Param("id") UUID id);

    Optional<IdempotencyRecord> findByCustomerIdAndOperationAndIdempotencyKey(String customerId, String operation, String idempotencyKey);
}
