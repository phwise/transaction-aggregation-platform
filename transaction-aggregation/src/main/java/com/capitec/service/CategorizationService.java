package com.capitec.service;

import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based categorization. Rules are evaluated in the order declared below and the
 * first match wins. Keywords match whole words only, so "fee" does not match "coffee".
 * INCOME applies only to credits: a credit without an income keyword is not treated as income.
 */
@Service
public class CategorizationService {

    private record Rule(TransactionCategory category, TransactionDirection requiredDirection, List<Pattern> patterns) {
    }

    private static final List<Rule> RULES = List.of(
            rule(TransactionCategory.INCOME, TransactionDirection.CREDIT,
                    "salary", "wage", "employer", "deposit", "interest", "income"),
            rule(TransactionCategory.GROCERIES, null,
                    "spar", "shoprite", "checkers", "pick n pay", "grocery", "supermarket"),
            rule(TransactionCategory.TRANSPORT, null,
                    "shell", "bp", "petrol", "fuel", "uber", "taxi", "bus", "train", "transport"),
            rule(TransactionCategory.RESTAURANTS, null,
                    "restaurant", "cafe", "pizza", "burger", "kfc", "steers", "nandos", "takeaway"),
            rule(TransactionCategory.SHOPPING, null,
                    "mall", "store", "shop", "retail", "clothing", "amazon", "takealot"),
            rule(TransactionCategory.HEALTHCARE, null,
                    "pharmacy", "doctor", "hospital", "clinic", "medical", "dental"),
            rule(TransactionCategory.BILLS_UTILITIES, null,
                    "eskom", "electricity", "water", "bill", "municipal", "internet", "fibre"),
            rule(TransactionCategory.ENTERTAINMENT, null,
                    "cinema", "movie", "streaming", "netflix", "spotify", "gaming"),
            rule(TransactionCategory.FEES, null,
                    "fee", "charge", "atm", "commission"),
            rule(TransactionCategory.TRANSFERS, null,
                    "transfer", "send money", "family member")
    );

    public TransactionCategory categorize(String description, String merchantName, TransactionDirection direction) {
        String text = normalize(description) + " " + normalize(merchantName);
        for (Rule rule : RULES) {
            if (rule.requiredDirection() != null && rule.requiredDirection() != direction) {
                continue;
            }
            for (Pattern pattern : rule.patterns()) {
                if (pattern.matcher(text).find()) {
                    return rule.category();
                }
            }
        }
        return TransactionCategory.OTHER;
    }

    private static Rule rule(TransactionCategory category, TransactionDirection direction, String... keywords) {
        return new Rule(category, direction, Arrays.stream(keywords)
                .map(keyword -> Pattern.compile("(?<![a-z0-9])" + Pattern.quote(keyword) + "(?![a-z0-9])"))
                .toList());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
