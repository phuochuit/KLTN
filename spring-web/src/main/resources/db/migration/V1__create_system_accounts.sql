CREATE TABLE system_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(24) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    security_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_system_accounts PRIMARY KEY (id),
    CONSTRAINT uk_system_accounts_username UNIQUE (username),
    CONSTRAINT ck_system_accounts_role CHECK (role IN ('MANAGEMENT', 'GATE_STAFF'))
);
