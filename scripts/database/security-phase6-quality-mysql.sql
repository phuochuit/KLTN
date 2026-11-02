-- Security phase 6: AI quality gate metadata on Gate Evidence.
-- Additive migration only. Back up/clone Aiven parking_anpr before execution.
USE parking_anpr;

SET @quality_status_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'gate_evidences'
       AND column_name = 'quality_status') = 0,
    'ALTER TABLE gate_evidences ADD COLUMN quality_status VARCHAR(16) NULL',
    'SELECT 1'
);
PREPARE quality_status_statement FROM @quality_status_sql;
EXECUTE quality_status_statement;
DEALLOCATE PREPARE quality_status_statement;

SET @quality_warnings_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'gate_evidences'
       AND column_name = 'quality_warnings') = 0,
    'ALTER TABLE gate_evidences ADD COLUMN quality_warnings VARCHAR(400) NULL',
    'SELECT 1'
);
PREPARE quality_warnings_statement FROM @quality_warnings_sql;
EXECUTE quality_warnings_statement;
DEALLOCATE PREPARE quality_warnings_statement;

SET @quality_version_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'gate_evidences'
       AND column_name = 'quality_version') = 0,
    'ALTER TABLE gate_evidences ADD COLUMN quality_version VARCHAR(40) NULL',
    'SELECT 1'
);
PREPARE quality_version_statement FROM @quality_version_sql;
EXECUTE quality_version_statement;
DEALLOCATE PREPARE quality_version_statement;
