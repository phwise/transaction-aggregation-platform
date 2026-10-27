package com.capitec.providers;

import com.capitec.base.dto.ProviderTransactionDTO;

import com.capitec.base.constant.ProviderStatus;

import java.util.List;

public record ProviderResponse(
        List<ProviderTransactionDTO> transactions,
        ProviderStatus status,
        String errorMessage
) {
    public static ProviderResponse success(List<ProviderTransactionDTO> transactions) {
        return new ProviderResponse(transactions, ProviderStatus.SUCCESS, null);
    }

    public static ProviderResponse timeout() {
        return new ProviderResponse(List.of(), ProviderStatus.TIMEOUT, "Provider request timed out");
    }

    public static ProviderResponse error(String errorMessage) {
        return new ProviderResponse(List.of(), ProviderStatus.ERROR, errorMessage);
    }
}