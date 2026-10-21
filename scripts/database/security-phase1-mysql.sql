-- Security phase 1 schema for MySQL 8 / Aiven.
-- Take and verify a backup before running this script on an existing database.
-- This script is additive and intentionally does not drop or alter business tables.

CREATE TABLE IF NOT EXISTS system_accounts (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS security_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_account_id BIGINT NULL,
    actor_username VARCHAR(80) NULL,
    action VARCHAR(48) NOT NULL,
    target_type VARCHAR(48) NULL,
    target_reference VARCHAR(120) NULL,
    outcome VARCHAR(16) NOT NULL,
    reason VARCHAR(500) NULL,
    evidence_reference VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_security_audit_events PRIMARY KEY (id),
    CONSTRAINT ck_security_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    INDEX ix_security_audit_occurred_at (occurred_at),
    INDEX ix_security_audit_action_occurred (action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Verification queries. Both tables must be returned before starting the application.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('system_accounts', 'security_audit_events')
ORDER BY table_name;
