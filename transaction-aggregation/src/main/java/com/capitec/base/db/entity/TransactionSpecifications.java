package com.capitec.base.db.entity;


import com.capitec.base.constant.TransactionFilter;
import com.capitec.base.constant.UtcDateRange;
import com.capitec.base.db.entity.Transaction;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private TransactionSpecifications() {
    }

    /** Always constrains to the customer; no filter can widen the result beyond that customer's rows. */
    public static Specification<Transaction> matching(String customerId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("customerId"), customerId));

            UtcDateRange dates = filter.dates();
            if (dates.startInclusive() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("transactionDate"), dates.startInclusive()));
            }
            if (dates.endExclusive() != null) {
                predicates.add(cb.lessThan(root.<Instant>get("transactionDate"), dates.endExclusive()));
            }
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.providerId() != null) {
                predicates.add(cb.equal(root.get("providerId"), filter.providerId()));
            }
            if (filter.direction() != null) {
                predicates.add(cb.equal(root.get("direction"), filter.direction()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("amount"), filter.maxAmount()));
            }
            if (filter.text() != null) {
                predicates.add(textMatches(root, cb, filter.text()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate textMatches(Root<Transaction> root, CriteriaBuilder cb, String text) {
        String pattern = "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        return cb.or(
                cb.like(cb.lower(root.<String>get("description")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.<String>get("merchantName")), pattern, LIKE_ESCAPE)
        );
    }

    private static String escapeLike(String value) {
        return value
                .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
    }
}