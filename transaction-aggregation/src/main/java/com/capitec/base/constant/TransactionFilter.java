package com.capitec.base.constant;


import com.capitec.base.exception.InvalidRequestException;
import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.domain.TransactionStatus;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Optional criteria for listing a customer's transactions. Null means "no constraint".
 * Amount bounds are inclusive and apply to the stored (unsigned) amount; direction carries the sign.
 */
public record TransactionFilter(
        UtcDateRange dates,
        TransactionCategory category,
        String providerId,
        TransactionDirection direction,
        TransactionStatus status,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String text
) {

    private static final Pattern PROVIDER_ID = Pattern.compile("[a-z]{1,20}");
    private static final int MAX_TEXT_LENGTH = 100;

    public TransactionFilter {
        dates = dates == null ? new UtcDateRange(null, null) : dates;
        if (providerId != null && !PROVIDER_ID.matcher(providerId).matches()) {
            throw new InvalidRequestException("provider must be a lowercase provider identifier such as fastpay");
        }
        if ((minAmount != null && minAmount.signum() < 0) || (maxAmount != null && maxAmount.signum() < 0)) {
            throw new InvalidRequestException("amount bounds must not be negative");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidRequestException("minAmount must not be greater than maxAmount");
        }
        text = normalizeText(text);
    }

    private static String normalizeText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            throw new InvalidRequestException("q must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return trimmed;
    }
}
