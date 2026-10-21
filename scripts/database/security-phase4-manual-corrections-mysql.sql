-- Additive Phase 4 migration: preserve AI result and operator correction in audit events.
SET @table_name := 'manual_override_events';

SET @column_name := 'original_plate_number';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @table_name AND column_name = @column_name) = 0,
    'ALTER TABLE manual_override_events ADD COLUMN original_plate_number VARCHAR(30) NULL', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @column_name := 'corrected_plate_number';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @table_name AND column_name = @column_name) = 0,
    'ALTER TABLE manual_override_events ADD COLUMN corrected_plate_number VARCHAR(30) NULL', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @column_name := 'original_vehicle_type';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @table_name AND column_name = @column_name) = 0,
    'ALTER TABLE manual_override_events ADD COLUMN original_vehicle_type VARCHAR(50) NULL', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @column_name := 'corrected_vehicle_type';
SET @sql := IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = @table_name AND column_name = @column_name) = 0,
    'ALTER TABLE manual_override_events ADD COLUMN corrected_vehicle_type VARCHAR(50) NULL', 'SELECT 1');
PREPARE statement FROM @sql; EXECUTE statement; DEALLOCATE PREPARE statement;

SELECT column_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'manual_override_events'
  AND column_name IN ('original_plate_number', 'corrected_plate_number',
      'original_vehicle_type', 'corrected_vehicle_type')
ORDER BY column_name;
