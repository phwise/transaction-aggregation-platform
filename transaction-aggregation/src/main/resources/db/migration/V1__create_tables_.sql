
-- =========================================================
-- V1__create_transaction_tables.sql
-- Transaction Aggregation API
-- PostgreSQL
-- =========================================================

-- =========================================================
-- 1. Transactions
-- =========================================================

CREATE TABLE transactions
(
    id                       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    customer_id              VARCHAR(50)     NOT NULL,
    provider_id              VARCHAR(50)     NOT NULL,
    provider_transaction_id   VARCHAR(100)   NOT NULL,

    transaction_date         TIMESTAMPTZ     NOT NULL,

    description              VARCHAR(500),
    merchant_name            VARCHAR(500),

    amount                  NUMERIC(19, 2)  NOT NULL,
    currency                VARCHAR(3)      NOT NULL,

    direction                VARCHAR(10)     NOT NULL,
    status                   VARCHAR(20)     NOT NULL,
    category                 VARCHAR(50),

    created_at               TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_provider_customer_provider_tx_id
        UNIQUE (
            customer_id,
            provider_id,
            provider_transaction_id
        ),

    CONSTRAINT chk_transaction_amount
        CHECK (amount >= 0),

    CONSTRAINT chk_transaction_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT chk_transaction_direction
        CHECK (direction IN ('DEBIT', 'CREDIT'))
);


-- =========================================================
-- Transaction indexes
-- =========================================================

CREATE INDEX idx_customer_created
    ON transactions (customer_id, created_at);

CREATE INDEX idx_provider_provider_id
    ON transactions (provider_id, provider_transaction_id);

CREATE INDEX idx_customer_date
    ON transactions (customer_id, transaction_date);

CREATE INDEX idx_customer_category
    ON transactions (customer_id, category);


-- =========================================================
-- 2. Idempotency Keys
-- =========================================================

CREATE TABLE idempotency_keys
(
    id                      UUID            PRIMARY KEY,

    customer_id              VARCHAR(50)     NOT NULL,
    operation                VARCHAR(255)    NOT NULL,
    idempotency_key          VARCHAR(255)    NOT NULL,

    request_fingerprint      VARCHAR(128)    NOT NULL,

    status                   VARCHAR(50)      NOT NULL,

    lease_expires_at         TIMESTAMPTZ     NOT NULL,

    response_status          INTEGER,
    response_body            TEXT,

    created_at               TIMESTAMPTZ     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at             TIMESTAMPTZ,

    CONSTRAINT uk_idempotency_customer_operation_key
        UNIQUE (
            customer_id,
            operation,
            idempotency_key
        ),

    CONSTRAINT chk_idempotency_response_status
        CHECK (
            response_status IS NULL
            OR response_status BETWEEN 100 AND 599
        )
);


-- =========================================================
-- Idempotency indexes
-- =========================================================

CREATE INDEX idx_idempotency_customer
    ON idempotency_keys (customer_id);

CREATE INDEX idx_idempotency_lease_expiry
    ON idempotency_keys (lease_expires_at);

CREATE INDEX idx_idempotency_status
    ON idempotency_keys (status);


-- =========================================================
-- Migration complete
-- =========================================================
