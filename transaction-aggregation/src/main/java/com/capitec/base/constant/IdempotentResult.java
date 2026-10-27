package com.capitec.base.constant;

public record IdempotentResult(StoredResponse response, boolean replayed) {
}

