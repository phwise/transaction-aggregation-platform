package com.capitec.base.constant;


import org.springframework.http.HttpStatus;

/**
 * Partial success is 200 because the operation completed synchronously; the body's status field
 * is what tells the client the result is incomplete. 503 is used only when no provider succeeded.
 */
public final class AggregationHttpStatus {

    private AggregationHttpStatus() {
    }

    public static HttpStatus of(AggregationStatus status) {
        return switch (status) {
            case COMPLETE_SUCCESS, PARTIAL_SUCCESS, EMPTY_SUCCESS -> HttpStatus.OK;
            case COMPLETE_FAILURE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
