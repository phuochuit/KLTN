-- Security phase 4: Gate Evidence, private assets and controlled manual override.
-- Additive migration only. Back up/clone Aiven parking_anpr before execution.
-- manual_override_allowed is historical schema only. Current policy uses the
-- GATE_STAFF role, active shift, evidence and reason; application code does not read it.
USE parking_anpr;

SET @manual_override_column_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'system_accounts'
       AND column_name = 'manual_override_allowed') = 0,
    'ALTER TABLE system_accounts ADD COLUMN manual_override_allowed BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE manual_override_column_statement FROM @manual_override_column_sql;
EXECUTE manual_override_column_statement;
DEALLOCATE PREPARE manual_override_column_statement;

CREATE TABLE IF NOT EXISTS gate_evidences (
    id CHAR(36) NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    source_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    account_id BIGINT NOT NULL,
    desktop_session_id VARCHAR(36) NOT NULL,
    parking_session_id BIGINT NULL,
    family_member_id BIGINT NULL,
    gate_shift_assignment_id BIGINT NULL,
    consumed_by_account_id BIGINT NULL,
    request_id VARCHAR(128) NOT NULL,
    upstream_evidence_id VARCHAR(80) NULL,
    plate_number VARCHAR(30) NULL,
    vehicle_type VARCHAR(50) NULL,
    detection_confidence DECIMAL(8,6) NULL,
    ocr_confidence DECIMAL(8,6) NULL,
    vehicle_confidence DECIMAL(8,6) NULL,
    face_decision VARCHAR(24) NULL,
    face_similarity DECIMAL(8,6) NULL,
    face_match_threshold DECIMAL(8,6) NULL,
    face_review_threshold DECIMAL(8,6) NULL,
    liveness_status VARCHAR(24) NOT NULL DEFAULT 'NOT_EVALUATED',
    liveness_score DECIMAL(8,6) NULL,
    liveness_provider VARCHAR(80) NULL,
    liveness_model_version VARCHAR(120) NULL,
    model_version VARCHAR(160) NOT NULL,
    camera_id VARCHAR(120) NULL,
    frame_index INT NULL,
    request_sha256 CHAR(64) NULL,
    response_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    KEY ix_gate_evidence_session_status (desktop_session_id, status, expires_at),
    KEY ix_gate_evidence_parking_session (parking_session_id),
    KEY ix_gate_evidence_created (created_at),
    KEY ix_gate_evidence_expiry (status, expires_at),
    KEY ix_gate_evidence_request (request_id),
    CONSTRAINT fk_gate_evidence_account FOREIGN KEY (account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_desktop_session FOREIGN KEY (desktop_session_id)
        REFERENCES desktop_sessions (session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_parking_session FOREIGN KEY (parking_session_id)
        REFERENCES parking_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_member FOREIGN KEY (family_member_id)
        REFERENCES family_members (id) ON DELETE RESTRICT,
    CONSTRAINT fk_gate_evidence_shift FOREIGN KEY (gate_shift_assignment_id)
        REFERENCES gate_shift_assignments (id) ON DELETE SET NULL,
    CONSTRAINT fk_gate_evidence_consumer FOREIGN KEY (consumed_by_account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS gate_evidence_assets (
    id CHAR(36) NOT NULL,
    evidence_id CHAR(36) NOT NULL,
    asset_role VARCHAR(32) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    media_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    width_px INT NULL,
    height_px INT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    deleted_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gate_evidence_asset_storage (storage_key),
    KEY ix_gate_evidence_asset_evidence (evidence_id, asset_role),
    CONSTRAINT fk_gate_evidence_asset_evidence FOREIGN KEY (evidence_id)
        REFERENCES gate_evidences (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS manual_override_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation VARCHAR(16) NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    reason_text VARCHAR(500) NOT NULL,
    account_id BIGINT NOT NULL,
    desktop_session_id VARCHAR(36) NOT NULL,
    gate_shift_assignment_id BIGINT NULL,
    parking_session_id BIGINT NULL,
    vehicle_evidence_id CHAR(36) NULL,
    face_evidence_id CHAR(36) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_manual_override_account_created (account_id, created_at),
    KEY ix_manual_override_parking_session (parking_session_id),
    CONSTRAINT fk_manual_override_account FOREIGN KEY (account_id)
        REFERENCES system_accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_override_desktop_session FOREIGN KEY (desktop_session_id)
        REFERENCES desktop_sessions (session_id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_override_shift FOREIGN KEY (gate_shift_assignment_id)
        REFERENCES gate_shift_assignments (id) ON DELETE SET NULL,
    CONSTRAINT fk_manual_override_parking_session FOREIGN KEY (parking_session_id)
        REFERENCES parking_sessions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_override_vehicle_evidence FOREIGN KEY (vehicle_evidence_id)
        REFERENCES gate_evidences (id) ON DELETE RESTRICT,
    CONSTRAINT fk_manual_override_face_evidence FOREIGN KEY (face_evidence_id)
        REFERENCES gate_evidences (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Postcheck: all three rows must be returned.
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('gate_evidences', 'gate_evidence_assets', 'manual_override_events')
ORDER BY table_name;
