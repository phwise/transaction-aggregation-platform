package com.capitec.providers;

import com.capitec.base.dto.ProviderTransactionDTO;
import com.capitec.base.constant.MockMode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

final class MockBehavior {

    private static final Duration SIMULATED_HANG = Duration.ofMinutes(10);

    private MockBehavior() {
    }

    static ProviderResponse respond(MockMode mode, String providerName, Supplier<List<ProviderTransactionDTO>> scripted) {
        return switch (mode) {
            case OK -> ProviderResponse.success(scripted.get());
            case EMPTY -> ProviderResponse.success(List.of());
            case ERROR -> ProviderResponse.error(providerName + " simulated failure");
            case MALFORMED -> ProviderResponse.success(List.of(malformedRecord()));
            case TIMEOUT -> hang(providerName);
        };
    }

    private static ProviderResponse hang(String providerName) {
        try {
            Thread.sleep(SIMULATED_HANG);
            return ProviderResponse.error(providerName + " did not respond");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProviderResponse.error(providerName + " was cancelled");
        }
    }

    private static ProviderTransactionDTO malformedRecord() {
        return new ProviderTransactionDTO(
                "MALFORMED-RECORD",
                Instant.EPOCH,
                "INCOMPLETE RECORD",
                null,
                (BigDecimal) null,
                "ZAR",
                "DEBIT",
                "COMPLETED"
        );
    }
}
