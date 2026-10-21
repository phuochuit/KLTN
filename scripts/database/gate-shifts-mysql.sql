-- Gate staff shift assignments. Additive migration only.
-- Back up Aiven and select parking_anpr before running.
USE parking_anpr;

CREATE TABLE IF NOT EXISTS gate_shift_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    shift_name VARCHAR(80) NOT NULL,
    starts_at TIMESTAMP(6) NOT NULL,
    ends_at TIMESTAMP(6) NOT NULL,
    duty VARCHAR(240) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_gate_shift_account_time (account_id, starts_at, ends_at),
    KEY ix_gate_shift_time (starts_at, ends_at),
    CONSTRAINT fk_gate_shift_account FOREIGN KEY (account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_gate_shift_time CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
