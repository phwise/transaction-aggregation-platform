package com.capitec.providers;

import com.capitec.base.dto.ProviderTransactionDTO;
import com.capitec.base.constant.MockMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock provider 3: reference-style IDs (SB-REF-), interest credits and a reversed transaction.
 */
@Component
public class SnapBankProvider implements TransactionProvider {

    private static final String PROVIDER_ID = "snapbank";

    private final MockMode mode;

    public SnapBankProvider(@Value("${app.mock-providers.snapbank.mode}") MockMode mode) {
        this.mode = mode;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderResponse fetchTransactions(String customerId) {
        return MockBehavior.respond(mode, "SnapBank", this::scriptedRecords);
    }

    private List<ProviderTransactionDTO> scriptedRecords() {
        List<ProviderTransactionDTO> transactions = new ArrayList<>();
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-10-06-A001",
                Instant.parse("2024-10-06T11:30:00Z"),
                "SHELL PETROL STATION",
                "Shell South Africa",
                new BigDecimal("650.00"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-10-05-A002",
                Instant.parse("2024-10-05T14:15:00Z"),
                "PHARMACY PLUS",
                "Pharmacy Plus",
                new BigDecimal("89.50"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-10-03-A003",
                Instant.parse("2024-10-03T07:00:00Z"),
                "AUTO INSURANCE PAYMENT",
                "InsureCorp Ltd",
                new BigDecimal("950.00"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-10-02-A004",
                Instant.parse("2024-10-02T00:00:00Z"),
                "STREAMING SERVICE MONTHLY",
                "StreamFlix Premium",
                new BigDecimal("199.99"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-10-01-A005",
                Instant.parse("2024-10-01T06:00:00Z"),
                "MONTHLY INTEREST",
                "SnapBank Interest",
                new BigDecimal("15.75"),
                "ZAR",
                "CREDIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "SB-REF-2024-09-30-A006",
                Instant.parse("2024-09-30T10:00:00Z"),
                "TRANSACTION REVERSED",
                "Merchant X",
                new BigDecimal("200.00"),
                "ZAR",
                "DEBIT",
                "REVERSED"
        ));
        return transactions;
    }
}