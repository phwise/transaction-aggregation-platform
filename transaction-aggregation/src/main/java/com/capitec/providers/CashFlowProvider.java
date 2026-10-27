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
 * Mock provider 2: transaction IDs prefixed CF-TXN-, includes a failed attempt that must still be stored with its status.
 */
@Component
public class CashFlowProvider implements TransactionProvider {

    private static final String PROVIDER_ID = "cashflow";

    private final MockMode mode;

    public CashFlowProvider(@Value("${app.mock-providers.cashflow.mode}") MockMode mode) {
        this.mode = mode;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderResponse fetchTransactions(String customerId) {
        return MockBehavior.respond(mode, "CashFlow", () -> scriptedRecords(customerId));
    }

    private List<ProviderTransactionDTO> scriptedRecords(String customerId) {
        List<ProviderTransactionDTO> transactions = new ArrayList<>();
        transactions.add(new ProviderTransactionDTO(
                "CF-TXN-" + customerId + "-20241012",
                Instant.parse("2024-10-12T14:20:00Z"),
                "ELECTRICITY BILL ESKOM",
                "Eskom Holdings",
                new BigDecimal("450.00"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "CF-TXN-" + customerId + "-20241011",
                Instant.parse("2024-10-11T16:30:00Z"),
                "ATM WITHDRAWAL",
                "ATM 1234",
                new BigDecimal("500.00"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "CF-TXN-" + customerId + "-20241009",
                Instant.parse("2024-10-09T09:15:00Z"),
                "REFUND - ONLINE PURCHASE",
                "RetailStore XYZ",
                new BigDecimal("149.99"),
                "ZAR",
                "CREDIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "CF-TXN-" + customerId + "-20241008",
                Instant.parse("2024-10-08T08:00:00Z"),
                "GYM MEMBERSHIP MONTHLY",
                "FitLife Gym",
                new BigDecimal("299.99"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "CF-TXN-" + customerId + "-20241007",
                Instant.parse("2024-10-07T18:45:00Z"),
                "ONLINE PURCHASE ATTEMPT",
                "Electronics Store",
                new BigDecimal("2999.00"),
                "ZAR",
                "DEBIT",
                "FAILED"
        ));
        return transactions;
    }
}