ALTER TABLE gate_evidence
    ADD COLUMN recognition_status VARCHAR(16) NULL;

ALTER TABLE gate_evidence
    ADD COLUMN recognition_failure_at TIMESTAMP(6) NULL;

ALTER TABLE gate_evidence
    ADD COLUMN face_verification_status VARCHAR(16) NULL;

ALTER TABLE gate_evidence
    ADD COLUMN face_verification_failure_at TIMESTAMP(6) NULL;

ALTER TABLE gate_evidence
    ADD COLUMN face_verification_evidence_id VARCHAR(36) NULL;

UPDATE gate_evidence
SET recognition_status = CASE
    WHEN evidence_kind <> 'AI_RECOGNITION' THEN NULL
    WHEN recognized_plate IS NULL THEN 'PENDING'
    ELSE 'SUCCESS'
END;
