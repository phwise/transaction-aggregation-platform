package com.capitec.base.constant;

import com.capitec.base.exception.InvalidRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Inclusive calendar-day range in UTC. Either bound may be open. A transaction is in range when its
 * timestamp is at or after 00:00 UTC on {@code from} and before 00:00 UTC on the day after {@code to}.
 */
public record UtcDateRange(LocalDate from, LocalDate to) {

    private static final int MIN_YEAR = 1900;
    private static final int MAX_YEAR = 9999;

    public UtcDateRange {
        checkYear(from);
        checkYear(to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("from must not be after to");
        }
    }

    public Instant startInclusive() {
        return from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant endExclusive() {
        return to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static void checkYear(LocalDate date) {
        if (date != null && (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR)) {
            throw new InvalidRequestException("dates must be between " + MIN_YEAR + " and " + MAX_YEAR);
        }
    }
}
