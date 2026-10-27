package com.capitec.base.constant;


import com.capitec.base.constant.TransactionFilter;
import com.capitec.base.constant.UtcDateRange;
import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Query parameters for listing transactions. Dates are UTC calendar days, both inclusive.
 * {@code q} matches description or merchant name, case-insensitively.
 */
public record TransactionSearchParams(
        LocalDate from,
        LocalDate to,
        TransactionCategory category,
        String provider,
        TransactionDirection direction,
        TransactionStatus status,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String q
) {

    public TransactionFilter toFilter() {
        return new TransactionFilter(
                new UtcDateRange(from, to),
                category,
                provider,
                direction,
                status,
                minAmount,
                maxAmount,
                q
        );
    }
}