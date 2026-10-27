package com.capitec.base.constant;
/**
 * Deterministic failure simulation for mock providers, selected per provider with
 * app.mock-providers.&lt;id&gt;.mode. OK is the normal scripted data set.
 */
public enum MockMode {
    OK,
    EMPTY,
    ERROR,
    TIMEOUT,
    MALFORMED
}
