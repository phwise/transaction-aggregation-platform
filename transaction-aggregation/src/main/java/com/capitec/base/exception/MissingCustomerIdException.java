package com.capitec.base.exception;

public class MissingCustomerIdException extends RuntimeException {

    public MissingCustomerIdException() {
        super("Request does not identify a valid customer");
    }
}
