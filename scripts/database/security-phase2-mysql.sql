-- Security phase 2: Desktop JWT sessions and refresh-token rotation.
-- Additive migration only. Back up Aiven and select parking_anpr before running.
USE parking_anpr;

CREATE TABLE IF NOT EXISTS desktop_sessions (
    session_id VARCHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    absolute_expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    security_version BIGINT NOT NULL,
    device_label VARCHAR(120) NULL,
    PRIMARY KEY (session_id),
    KEY ix_desktop_sessions_account_active (account_id, revoked_at, absolute_expires_at),
    CONSTRAINT fk_desktop_sessions_account FOREIGN KEY (account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS desktop_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    replaced_by_hash CHAR(64) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_desktop_refresh_tokens_hash (token_hash),
    KEY ix_desktop_refresh_tokens_session (session_id, expires_at),
    CONSTRAINT fk_desktop_refresh_tokens_session FOREIGN KEY (session_id)
        REFERENCES desktop_sessions (session_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
