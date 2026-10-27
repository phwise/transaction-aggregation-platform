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
 * Mock provider 1: separate amount and currency fields, IDs prefixed FP-.
 * Its scripted data includes one record repeated within the same response.
 */
@Component
public class FastPayProvider implements TransactionProvider {

    private static final String PROVIDER_ID = "fastpay";

    private final MockMode mode;

    public FastPayProvider(@Value("${app.mock-providers.fastpay.mode}") MockMode mode) {
        this.mode = mode;
    }

    @Override
    public String getProviderId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderResponse fetchTransactions(String customerId) {
        return MockBehavior.respond(mode, "FastPay", () -> scriptedRecords(customerId));
    }

    private List<ProviderTransactionDTO> scriptedRecords(String customerId) {
        List<ProviderTransactionDTO> transactions = new ArrayList<>();
        transactions.add(new ProviderTransactionDTO(
                "FP-" + customerId + "-20241015-001",
                Instant.parse("2024-10-15T08:30:00Z"),
                "SPAR HYPER MARKET",
                "Spar Hypermarket",
                new BigDecimal("125.50"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "FP-" + customerId + "-20241014-002",
                Instant.parse("2024-10-14T06:00:00Z"),
                "EMPLOYER DIRECT DEPOSIT",
                "ABC Corporation",
                new BigDecimal("5000.00"),
                "ZAR",
                "CREDIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "FP-" + customerId + "-20241013-003",
                Instant.parse("2024-10-13T19:45:00Z"),
                "RESTAURANT ITALIA",
                "Restaurant Italia",
                new BigDecimal("285.75"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "FP-" + customerId + "-20241015-001",
                Instant.parse("2024-10-15T08:30:00Z"),
                "SPAR HYPER MARKET",
                "Spar Hypermarket",
                new BigDecimal("125.50"),
                "ZAR",
                "DEBIT",
                "COMPLETED"
        ));
        transactions.add(new ProviderTransactionDTO(
                "FP-" + customerId + "-20241010-004",
                Instant.parse("2024-10-10T10:00:00Z"),
                "ONLINE TRANSFER",
                "Family Member",
                new BigDecimal("500.00"),
                "ZAR",
                "DEBIT",
                "PENDING"
        ));
        return transactions;
    }
}