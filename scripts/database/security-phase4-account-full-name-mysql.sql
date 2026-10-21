-- Additive Phase 4 migration: operator display name.
-- Safe for existing rows; applications fall back to username when NULL.
SET @column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'system_accounts'
      AND column_name = 'full_name'
);

SET @sql := IF(
    @column_exists = 0,
    'ALTER TABLE system_accounts ADD COLUMN full_name VARCHAR(160) NULL AFTER username',
    'SELECT ''system_accounts.full_name already exists'''
);
PREPARE statement FROM @sql;
EXECUTE statement;
DEALLOCATE PREPARE statement;

SELECT COUNT(*) AS full_name_column_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'system_accounts'
  AND column_name = 'full_name';
