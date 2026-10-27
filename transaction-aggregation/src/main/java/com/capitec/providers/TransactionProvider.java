package com.capitec.providers;


/**
 * Interface for transaction providers.
 * Implementations provide transaction data from various sources.
 */
public interface TransactionProvider {

    /**
     * Gets the unique identifier for this provider.
     */
    String getProviderId();

    /**
     * Fetches transactions for a customer.
     * Must handle timeouts and errors gracefully.
     *
     * @param customerId The customer ID
     * @return Provider response with transactions or error details
     */
    ProviderResponse fetchTransactions(String customerId);
}