CREATE TABLE providers (
    code varchar(40) PRIMARY KEY,
    display_name varchar(100) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    supported_methods varchar(120) NOT NULL,
    provider_fee_bps integer NOT NULL CHECK (provider_fee_bps >= 0),
    fixed_fee_minor bigint NOT NULL DEFAULT 0 CHECK (fixed_fee_minor >= 0),
    success_rate double precision NOT NULL CHECK (success_rate >= 0 AND success_rate <= 1),
    p95_latency_ms bigint NOT NULL CHECK (p95_latency_ms >= 0)
);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    merchant_id varchar(80) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_fingerprint varchar(64) NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'INR'),
    payment_method varchar(24) NOT NULL,
    status varchar(32) NOT NULL,
    selected_provider_code varchar(40) REFERENCES providers(code),
    provider_reference varchar(120) UNIQUE,
    merchant_fee_minor bigint NOT NULL DEFAULT 0 CHECK (merchant_fee_minor >= 0),
    provider_fee_minor bigint NOT NULL DEFAULT 0 CHECK (provider_fee_minor >= 0),
    avoided_provider_fee_minor bigint NOT NULL DEFAULT 0 CHECK (avoided_provider_fee_minor >= 0),
    route_score double precision,
    route_reason varchar(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_idempotency UNIQUE (merchant_id, idempotency_key)
);
CREATE INDEX idx_payment_merchant_created ON payments(merchant_id, created_at DESC);
CREATE INDEX idx_payment_status_created ON payments(status, created_at DESC);

CREATE TABLE payment_attempts (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id),
    provider_code varchar(40) NOT NULL REFERENCES providers(code),
    outcome varchar(24) NOT NULL,
    resolved_outcome varchar(24),
    latency_ms bigint NOT NULL,
    provider_fee_minor bigint NOT NULL,
    detail varchar(300),
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_attempt_provider_created ON payment_attempts(provider_code, created_at DESC);

CREATE TABLE fee_rules (
    id uuid PRIMARY KEY,
    rule_name varchar(120) NOT NULL,
    merchant_id varchar(80),
    payment_method varchar(24) NOT NULL,
    rate_bps integer NOT NULL CHECK (rate_bps >= 0),
    fixed_fee_minor bigint NOT NULL DEFAULT 0 CHECK (fixed_fee_minor >= 0),
    active_from timestamp with time zone NOT NULL,
    active_until timestamp with time zone,
    enabled boolean NOT NULL DEFAULT true,
    CHECK (active_until IS NULL OR active_until > active_from)
);
CREATE INDEX idx_fee_rule_lookup ON fee_rules(payment_method, merchant_id, enabled, active_from);

CREATE TABLE refunds (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id),
    idempotency_key varchar(128) NOT NULL,
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    status varchar(24) NOT NULL,
    provider_reference varchar(120),
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_refund_idempotency UNIQUE (payment_id, idempotency_key)
);

CREATE TABLE ledger_entries (
    id uuid PRIMARY KEY,
    transfer_id uuid NOT NULL,
    payment_id uuid NOT NULL REFERENCES payments(id),
    account_code varchar(80) NOT NULL,
    direction varchar(8) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor bigint NOT NULL CHECK (amount_minor > 0),
    currency varchar(3) NOT NULL CHECK (currency = 'INR'),
    entry_type varchar(32) NOT NULL,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_ledger_payment ON ledger_entries(payment_id);
CREATE INDEX idx_ledger_transfer ON ledger_entries(transfer_id);

CREATE TABLE webhook_events (
    id uuid PRIMARY KEY,
    provider_code varchar(40) NOT NULL REFERENCES providers(code),
    provider_event_id varchar(120) NOT NULL,
    provider_reference varchar(120) NOT NULL,
    event_type varchar(60) NOT NULL,
    signature_valid boolean NOT NULL,
    processing_status varchar(24) NOT NULL,
    raw_payload text NOT NULL,
    received_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_webhook_provider_event UNIQUE (provider_code, provider_event_id)
);

CREATE TABLE webhook_deliveries (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id),
    event_type varchar(60) NOT NULL,
    payload text NOT NULL,
    target_mode varchar(40) NOT NULL,
    status varchar(24) NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    last_error varchar(300),
    created_at timestamp with time zone NOT NULL,
    delivered_at timestamp with time zone
);
CREATE INDEX idx_delivery_due ON webhook_deliveries(status, next_attempt_at);

CREATE TABLE reconciliation_runs (
    id uuid PRIMARY KEY,
    provider_code varchar(40) NOT NULL REFERENCES providers(code),
    status varchar(24) NOT NULL,
    row_count integer NOT NULL DEFAULT 0,
    matched_count integer NOT NULL DEFAULT 0,
    discrepancy_count integer NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE settlement_records (
    id uuid PRIMARY KEY,
    provider_code varchar(40) NOT NULL REFERENCES providers(code),
    provider_reference varchar(120) NOT NULL,
    payment_id uuid REFERENCES payments(id),
    reported_amount_minor bigint NOT NULL CHECK (reported_amount_minor >= 0),
    reported_fee_minor bigint NOT NULL CHECK (reported_fee_minor >= 0),
    status varchar(40) NOT NULL,
    ledger_posted boolean NOT NULL DEFAULT false,
    imported_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_settlement_provider_reference UNIQUE (provider_code, provider_reference)
);
CREATE INDEX idx_settlement_payment ON settlement_records(payment_id);

CREATE TABLE reconciliation_items (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES reconciliation_runs(id),
    provider_reference varchar(120) NOT NULL,
    payment_id uuid REFERENCES payments(id),
    expected_amount_minor bigint,
    reported_amount_minor bigint,
    expected_fee_minor bigint,
    reported_fee_minor bigint,
    discrepancy_type varchar(40) NOT NULL,
    resolved boolean NOT NULL DEFAULT false,
    created_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_recon_run ON reconciliation_items(run_id);
CREATE INDEX idx_recon_open ON reconciliation_items(resolved, discrepancy_type);

INSERT INTO providers(code, display_name, enabled, supported_methods, provider_fee_bps, fixed_fee_minor, success_rate, p95_latency_ms) VALUES
('razorpay_sandbox', 'Razorpay sandbox simulator', true, 'UPI,CARD,NETBANKING', 24, 0, 0.992, 245),
('cashfree_sandbox', 'Cashfree sandbox simulator', true, 'UPI,CARD,NETBANKING', 20, 0, 0.985, 280),
('payu_sandbox', 'PayU sandbox simulator', true, 'UPI,CARD,NETBANKING', 22, 0, 0.976, 190);

-- These are sample commercial rules for the demo, not a statement of current regulation.
INSERT INTO fee_rules(id, rule_name, merchant_id, payment_method, rate_bps, fixed_fee_minor, active_from, enabled) VALUES
('11111111-1111-1111-1111-111111111111', 'Illustrative UPI zero-MDR scenario', NULL, 'UPI', 0, 0, '2020-01-01T00:00:00Z', true),
('22222222-2222-2222-2222-222222222222', 'Illustrative card merchant fee', NULL, 'CARD', 250, 0, '2020-01-01T00:00:00Z', true),
('33333333-3333-3333-3333-333333333333', 'Illustrative netbanking merchant fee', NULL, 'NETBANKING', 180, 0, '2020-01-01T00:00:00Z', true);
