package com.capitec.base.constant;
public enum AggregationStatus {
    COMPLETE_SUCCESS,    // All providers succeeded
    PARTIAL_SUCCESS,     // Some providers succeeded, others failed
    COMPLETE_FAILURE,    // All providers failed
    EMPTY_SUCCESS        // Providers succeeded but returned no transactions
}
