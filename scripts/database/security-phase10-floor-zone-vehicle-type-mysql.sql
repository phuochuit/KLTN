-- ============================================================================
-- Phase 10: Floors, Zones, Dynamic Vehicle Types, UI Options, Capacity Conversion & Outbox
-- Safe, Idempotent, Additive Migration for MySQL 8.0 / MySQL 8.4
-- ============================================================================

USE parking_anpr;

-- 1. Create parking_floors table
CREATE TABLE IF NOT EXISTS parking_floors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    is_basement BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    max_capacity_units INT NOT NULL DEFAULT 500,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_parking_floor_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Create vehicle_types table
CREATE TABLE IF NOT EXISTS vehicle_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    capacity_units INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_vehicle_type_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Create parking_zones table
CREATE TABLE IF NOT EXISTS parking_zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    floor_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    code_prefix VARCHAR(16) NOT NULL,
    vehicle_type_id BIGINT NULL,
    capacity_units INT NOT NULL DEFAULT 50,
    side VARCHAR(32) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_parking_zone_floor_name UNIQUE (floor_id, name),
    CONSTRAINT fk_pz_floor FOREIGN KEY (floor_id) REFERENCES parking_floors(id),
    CONSTRAINT fk_pz_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Create ui_option table
CREATE TABLE IF NOT EXISTS ui_option (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(40) NOT NULL,
    option_value VARCHAR(160) NOT NULL,
    use_count INT NOT NULL DEFAULT 1,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ui_option_cat_val UNIQUE (category, option_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Create slot_conversion_logs table
CREATE TABLE IF NOT EXISTS slot_conversion_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id BIGINT NOT NULL,
    old_vehicle_type_id BIGINT NULL,
    new_vehicle_type_id BIGINT NOT NULL,
    old_slot_count INT NOT NULL,
    new_slot_count INT NOT NULL,
    capacity_units_before INT NOT NULL,
    capacity_units_after INT NOT NULL,
    deactivated_count INT NOT NULL,
    created_count INT NOT NULL,
    actor_username VARCHAR(80) NULL,
    converted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(400) NULL,
    CONSTRAINT fk_scl_zone FOREIGN KEY (zone_id) REFERENCES parking_zones(id),
    CONSTRAINT fk_scl_old_vt FOREIGN KEY (old_vehicle_type_id) REFERENCES vehicle_types(id),
    CONSTRAINT fk_scl_new_vt FOREIGN KEY (new_vehicle_type_id) REFERENCES vehicle_types(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Create outbox_event table
CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(64) NULL,
    event_type VARCHAR(64) NULL,
    payload_json TEXT NOT NULL,
    payload_sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP NULL,
    last_error VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------------------------------------------------------
-- 7. Modify pricing_rules: Drop unique on vehicle_type, add vehicle_type_id
-- ----------------------------------------------------------------------------

-- Modify vehicle_type to NULL
SET @pr_vt_null_sql = 'ALTER TABLE pricing_rules MODIFY COLUMN vehicle_type VARCHAR(50) NULL';
PREPARE pr_vt_null_stmt FROM @pr_vt_null_sql;
EXECUTE pr_vt_null_stmt;
DEALLOCATE PREPARE pr_vt_null_stmt;

-- Drop unique index on vehicle_type if exists
SET @pr_drop_idx_sql = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'pricing_rules'
       AND column_name = 'vehicle_type'
       AND non_unique = 0
       AND index_name != 'PRIMARY') > 0,
    (SELECT CONCAT('ALTER TABLE pricing_rules DROP INDEX `', index_name, '`')
     FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'pricing_rules'
       AND column_name = 'vehicle_type'
       AND non_unique = 0
       AND index_name != 'PRIMARY' LIMIT 1),
    'SELECT 1'
);
PREPARE pr_drop_idx_stmt FROM @pr_drop_idx_sql;
EXECUTE pr_drop_idx_stmt;
DEALLOCATE PREPARE pr_drop_idx_stmt;

-- Add vehicle_type_id to pricing_rules
SET @pr_add_col_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'pricing_rules'
       AND column_name = 'vehicle_type_id') = 0,
    'ALTER TABLE pricing_rules ADD COLUMN vehicle_type_id BIGINT NULL UNIQUE, ADD CONSTRAINT fk_pr_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id)',
    'SELECT 1'
);
PREPARE pr_add_col_stmt FROM @pr_add_col_sql;
EXECUTE pr_add_col_stmt;
DEALLOCATE PREPARE pr_add_col_stmt;

-- ----------------------------------------------------------------------------
-- 8. Add columns to parking_slots: zone_id, vehicle_type_id, deactivated_reason
-- ----------------------------------------------------------------------------

SET @ps_zone_id_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'parking_slots'
       AND column_name = 'zone_id') = 0,
    'ALTER TABLE parking_slots ADD COLUMN zone_id BIGINT NULL, ADD CONSTRAINT fk_ps_zone FOREIGN KEY (zone_id) REFERENCES parking_zones(id)',
    'SELECT 1'
);
PREPARE ps_zone_id_stmt FROM @ps_zone_id_sql;
EXECUTE ps_zone_id_stmt;
DEALLOCATE PREPARE ps_zone_id_stmt;

SET @ps_vt_id_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'parking_slots'
       AND column_name = 'vehicle_type_id') = 0,
    'ALTER TABLE parking_slots ADD COLUMN vehicle_type_id BIGINT NULL, ADD CONSTRAINT fk_ps_vt FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id)',
    'SELECT 1'
);
PREPARE ps_vt_id_stmt FROM @ps_vt_id_sql;
EXECUTE ps_vt_id_stmt;
DEALLOCATE PREPARE ps_vt_id_stmt;

SET @ps_deact_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'parking_slots'
       AND column_name = 'deactivated_reason') = 0,
    'ALTER TABLE parking_slots ADD COLUMN deactivated_reason VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE ps_deact_stmt FROM @ps_deact_sql;
EXECUTE ps_deact_stmt;
DEALLOCATE PREPARE ps_deact_stmt;

-- ----------------------------------------------------------------------------
-- 9. Add columns to vehicles & parking_sessions: vehicle_type_id
-- ----------------------------------------------------------------------------

SET @v_vt_id_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'vehicles'
       AND column_name = 'vehicle_type_id') = 0,
    'ALTER TABLE vehicles ADD COLUMN vehicle_type_id BIGINT NULL, ADD CONSTRAINT fk_v_vt FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id)',
    'SELECT 1'
);
PREPARE v_vt_id_stmt FROM @v_vt_id_sql;
EXECUTE v_vt_id_stmt;
DEALLOCATE PREPARE v_vt_id_stmt;

SET @sess_vt_id_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'parking_sessions'
       AND column_name = 'detected_vehicle_type_id') = 0,
    'ALTER TABLE parking_sessions ADD COLUMN detected_vehicle_type_id BIGINT NULL, ADD CONSTRAINT fk_sess_vt FOREIGN KEY (detected_vehicle_type_id) REFERENCES vehicle_types(id)',
    'SELECT 1'
);
PREPARE sess_vt_id_stmt FROM @sess_vt_id_sql;
EXECUTE sess_vt_id_stmt;
DEALLOCATE PREPARE sess_vt_id_stmt;

-- ----------------------------------------------------------------------------
-- 10. Seed standard vehicle types (INSERT IGNORE)
-- ----------------------------------------------------------------------------

INSERT IGNORE INTO vehicle_types (code, display_name, category, capacity_units, active) VALUES
('BICYCLE_ELECTRIC_BICYCLE', 'Xe đạp / xe đạp điện', 'BICYCLE_ELECTRIC_BICYCLE', 1, TRUE),
('MOTORBIKE', 'Xe máy', 'MOTORBIKE', 1, TRUE),
('LARGE_MOTORBIKE', 'Xe máy phân khối lớn', 'LARGE_MOTORBIKE', 1, TRUE),
('CAR', 'Ô tô', 'CAR', 5, TRUE),
('CAR_6_7', 'Ô tô 6-7 chỗ', 'CAR_6_7', 5, TRUE),
('CAR_8_9', 'Ô tô 8-9 chỗ', 'CAR_8_9', 5, TRUE);

-- Backfill pricing_rules.vehicle_type_id
UPDATE pricing_rules pr
JOIN vehicle_types vt ON (
    vt.code = pr.vehicle_type
    OR (pr.vehicle_type = 'CAR' AND vt.code = 'CAR')
    OR (pr.vehicle_type = 'MOTORBIKE' AND vt.code = 'MOTORBIKE')
)
SET pr.vehicle_type_id = vt.id
WHERE pr.vehicle_type_id IS NULL;

-- Backfill vehicles.vehicle_type_id
UPDATE vehicles v
JOIN vehicle_types vt ON (
    vt.code = v.vehicle_type
    OR (v.vehicle_type = 'CAR' AND vt.code = 'CAR')
    OR (v.vehicle_type = 'MOTORBIKE' AND vt.code = 'MOTORBIKE')
)
SET v.vehicle_type_id = vt.id
WHERE v.vehicle_type_id IS NULL;

-- ----------------------------------------------------------------------------
-- 11. Backfill parking_floors & parking_zones from parking_slots
-- ----------------------------------------------------------------------------

-- Backfill floors
INSERT IGNORE INTO parking_floors (name, is_basement, display_order, max_capacity_units, active)
SELECT DISTINCT
    COALESCE(NULLIF(TRIM(floor), ''), 'Tầng hầm B1') AS name,
    CASE WHEN LOWER(COALESCE(floor, '')) LIKE '%hầm%' OR UPPER(COALESCE(floor, '')) LIKE 'B%' THEN TRUE ELSE FALSE END AS is_basement,
    CASE WHEN UPPER(COALESCE(floor, '')) LIKE '%B1%' THEN 1 WHEN UPPER(COALESCE(floor, '')) LIKE '%B2%' THEN 2 ELSE 3 END AS display_order,
    500 AS max_capacity_units,
    TRUE AS active
FROM parking_slots;

-- Backfill default zones
INSERT IGNORE INTO parking_zones (floor_id, name, code_prefix, vehicle_type_id, capacity_units, active)
SELECT DISTINCT
    pf.id AS floor_id,
    COALESCE(NULLIF(TRIM(ps.zone_name), ''), 'Khu A') AS name,
    COALESCE(SUBSTRING(NULLIF(TRIM(ps.slot_code), ''), 1, 1), 'A') AS code_prefix,
    vt.id AS vehicle_type_id,
    50 AS capacity_units,
    TRUE AS active
FROM parking_slots ps
JOIN parking_floors pf ON pf.name = COALESCE(NULLIF(TRIM(ps.floor), ''), 'Tầng hầm B1')
LEFT JOIN vehicle_types vt ON (
    (ps.allowed_vehicle_type IS NOT NULL AND vt.code = ps.allowed_vehicle_type)
    OR (ps.allowed_vehicle_type IS NULL AND vt.code = 'CAR')
);

-- Backfill parking_slots zone_id & vehicle_type_id
UPDATE parking_slots ps
JOIN parking_floors pf ON pf.name = COALESCE(NULLIF(TRIM(ps.floor), ''), 'Tầng hầm B1')
JOIN parking_zones pz ON pz.floor_id = pf.id AND pz.name = COALESCE(NULLIF(TRIM(ps.zone_name), ''), 'Khu A')
SET ps.zone_id = pz.id
WHERE ps.zone_id IS NULL;

UPDATE parking_slots ps
JOIN vehicle_types vt ON (
    (ps.allowed_vehicle_type IS NOT NULL AND vt.code = ps.allowed_vehicle_type)
    OR (ps.allowed_vehicle_type IS NULL AND vt.code = 'CAR')
)
SET ps.vehicle_type_id = vt.id
WHERE ps.vehicle_type_id IS NULL;

-- In case any slots still have null zone_id, fallback to first zone
UPDATE parking_slots ps
CROSS JOIN (SELECT id FROM parking_zones ORDER BY id ASC LIMIT 1) def_pz
SET ps.zone_id = def_pz.id
WHERE ps.zone_id IS NULL;

-- In case any slots still have null vehicle_type_id, fallback to CAR
UPDATE parking_slots ps
CROSS JOIN (SELECT id FROM vehicle_types WHERE code = 'CAR' LIMIT 1) def_vt
SET ps.vehicle_type_id = def_vt.id
WHERE ps.vehicle_type_id IS NULL;
