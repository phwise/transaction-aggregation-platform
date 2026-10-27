package com.capitec.base.constant;


/**
 * An HTTP-level response captured for idempotent replay. The body is the JSON that was returned to the client.
 */
public record StoredResponse(int status, String body) {
}