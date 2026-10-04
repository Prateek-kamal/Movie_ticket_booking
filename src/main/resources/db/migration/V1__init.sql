CREATE TABLE shows (
    id             UUID PRIMARY KEY,
    name           TEXT   NOT NULL,
    price_paise    BIGINT NOT NULL,
    per_user_limit INT    NOT NULL DEFAULT 4,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seats (
    show_id        UUID NOT NULL REFERENCES shows(id),
    label          TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'AVAILABLE'
                   CHECK (status IN ('AVAILABLE', 'HELD', 'CONFIRMED')),
    user_id        TEXT,
    reservation_id UUID,
    PRIMARY KEY (show_id, label)
);
CREATE INDEX seats_user_idx ON seats (show_id, user_id);
CREATE INDEX seats_reservation_idx ON seats (reservation_id);

CREATE TABLE reservations (
    id           UUID PRIMARY KEY,
    show_id      UUID   NOT NULL REFERENCES shows(id),
    user_id      TEXT   NOT NULL,
    amount_paise BIGINT NOT NULL,
    status       TEXT   NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    user_id        TEXT NOT NULL,
    idem_key       TEXT NOT NULL,
    request_hash   TEXT NOT NULL,
    reservation_id UUID NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
