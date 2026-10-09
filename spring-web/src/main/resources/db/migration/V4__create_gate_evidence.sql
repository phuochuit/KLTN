CREATE TABLE gate_evidence (
    id VARCHAR(36) NOT NULL,
    owner_account_id BIGINT NOT NULL,
    desktop_session_id VARCHAR(36) NOT NULL,
    parking_session_id BIGINT NULL,
    operation_type VARCHAR(16) NOT NULL,
    evidence_kind VARCHAR(32) NOT NULL,
    file_name VARCHAR(64) NOT NULL,
    derived_file_name VARCHAR(64) NULL,
    media_type VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    derived_byte_size BIGINT NULL,
    derived_sha256 CHAR(64) NULL,
    recognized_plate VARCHAR(32) NULL,
    vehicle_type VARCHAR(16) NULL,
    detection_confidence DOUBLE NULL,
    ocr_confidence DOUBLE NULL,
    vehicle_confidence DOUBLE NULL,
    verification_decision VARCHAR(16) NULL,
    verification_similarity DOUBLE NULL,
    verification_threshold DOUBLE NULL,
    family_member_id BIGINT NULL,
    frame_index INT NULL,
    source_service VARCHAR(48) NULL,
    captured_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    recognition_valid_until TIMESTAMP(6) NULL,
    consumed_at TIMESTAMP(6) NULL,
    preserve_until TIMESTAMP(6) NULL,
    preserve_reason VARCHAR(500) NULL,
    preserve_approved_by BIGINT NULL,
    preserve_approved_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_gate_evidence PRIMARY KEY (id),
    CONSTRAINT uk_gate_evidence_file_name UNIQUE (file_name),
    CONSTRAINT uk_gate_evidence_derived_file_name UNIQUE (derived_file_name),
    CONSTRAINT fk_gate_evidence_owner FOREIGN KEY (owner_account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_desktop_session FOREIGN KEY (desktop_session_id)
        REFERENCES desktop_sessions (session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_parking_session FOREIGN KEY (parking_session_id)
        REFERENCES parking_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_family_member FOREIGN KEY (family_member_id)
        REFERENCES family_members (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_hold_approver FOREIGN KEY (preserve_approved_by)
        REFERENCES system_accounts (id) ON DELETE RESTRICT
);

CREATE INDEX ix_gate_evidence_expiry_hold
    ON gate_evidence (expires_at, preserve_until);
CREATE INDEX ix_gate_evidence_owner_session
    ON gate_evidence (owner_account_id, desktop_session_id);
CREATE INDEX ix_gate_evidence_parking_session
    ON gate_evidence (parking_session_id);
