-- Durable idempotency reservations for gate entry/exit retries (MySQL 8 / Aiven).
-- Additive and safe to run repeatedly after taking a database backup.
USE parking_anpr;

CREATE TABLE IF NOT EXISTS parking_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_username VARCHAR(100) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operation_key VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_parking_operations PRIMARY KEY (id),
    CONSTRAINT uk_parking_operations_actor_type_key UNIQUE (
        actor_username, operation_type, operation_key),
    CONSTRAINT chk_parking_operations_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

SET @create_operation_updated_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'parking_operations' AND index_name = 'idx_parking_operations_updated_at') = 0,
    'CREATE INDEX idx_parking_operations_updated_at ON parking_operations(updated_at)', 'SELECT 1');
PREPARE statement FROM @create_operation_updated_index; EXECUTE statement; DEALLOCATE PREPARE statement;
