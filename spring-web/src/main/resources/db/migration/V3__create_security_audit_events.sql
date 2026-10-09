CREATE TABLE security_audit_events (
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
    CONSTRAINT pk_security_audit_events PRIMARY KEY (id)
);

CREATE INDEX ix_security_audit_events_action_time
    ON security_audit_events (action, occurred_at);
CREATE INDEX ix_security_audit_events_target_time
    ON security_audit_events (target_type, target_reference, occurred_at);
