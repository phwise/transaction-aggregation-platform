package com.capitec.service;

import com.capitec.domain.TransactionCategory;
import com.capitec.domain.TransactionDirection;
import com.capitec.service.CategorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CategorizationServiceTest {

    private final CategorizationService service = new CategorizationService();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Checkers Sixty60         |        | DEBIT  | GROCERIES",
            "Pick n Pay Hyper         |        | DEBIT  | GROCERIES",
            "Shell Garage             |        | DEBIT  | TRANSPORT",
            "Uber trip                |        | DEBIT  | TRANSPORT",
            "Steers                   |        | DEBIT  | RESTAURANTS",
            "Pharmacy                 |        | DEBIT  | HEALTHCARE",
            "Eskom prepaid            |        | DEBIT  | BILLS_UTILITIES",
            "Netflix subscription     |        | DEBIT  | ENTERTAINMENT",
            "Monthly account fee      |        | DEBIT  | FEES",
            "Transfer to savings      |        | DEBIT  | TRANSFERS",
            "The Store                |        | DEBIT  | SHOPPING",
            "Something unknown        |        | DEBIT  | OTHER",
            "Coffee house             |        | DEBIT  | OTHER",
            "SHELL                    |        | DEBIT  | TRANSPORT",
            "Card purchase           | Steers | DEBIT  | RESTAURANTS",
            "Salary ACME              |        | CREDIT | INCOME",
            "Salary ACME              |        | DEBIT  | OTHER",
            "Salary shop              |        | CREDIT | INCOME"
    })
    void categorizesByWholeWordKeywordsAndDirection(
            String description,
            String merchantName,
            TransactionDirection direction,
            TransactionCategory expected
    ) {
        assertThat(service.categorize(description, merchantName, direction)).isEqualTo(expected);
    }

    @Test
    void nullDescriptionAndMerchantAreOther() {
        assertThat(service.categorize(null, null, TransactionDirection.DEBIT))
                .isEqualTo(TransactionCategory.OTHER);
    }
}