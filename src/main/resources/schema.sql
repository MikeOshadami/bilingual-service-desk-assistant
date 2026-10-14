-- Audit trail for AI interactions and automated actions. Details are redacted before insert.
CREATE TABLE IF NOT EXISTS audit_event (
    id          BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    outcome     TEXT        NOT NULL,
    detail      TEXT
);
CREATE INDEX IF NOT EXISTS audit_event_occurred_at_idx ON audit_event (occurred_at DESC);
