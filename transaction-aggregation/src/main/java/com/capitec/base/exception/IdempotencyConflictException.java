package com.capitec.base.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("A request with this Idempotency-Key is still in progress");
    }
}