CREATE TABLE desktop_sessions (
    session_id VARCHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    absolute_expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    security_version BIGINT NOT NULL,
    device_label VARCHAR(120) NULL,
    CONSTRAINT pk_desktop_sessions PRIMARY KEY (session_id),
    CONSTRAINT fk_desktop_sessions_account FOREIGN KEY (account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT
);

CREATE INDEX ix_desktop_sessions_account_active
    ON desktop_sessions (account_id, revoked_at, absolute_expires_at);

CREATE TABLE desktop_refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    replaced_by_hash CHAR(64) NULL,
    CONSTRAINT pk_desktop_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_desktop_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_desktop_refresh_tokens_session FOREIGN KEY (session_id)
        REFERENCES desktop_sessions (session_id) ON DELETE RESTRICT
);

CREATE INDEX ix_desktop_refresh_tokens_session
    ON desktop_refresh_tokens (session_id, expires_at);
