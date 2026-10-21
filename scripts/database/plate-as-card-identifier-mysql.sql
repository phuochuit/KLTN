-- Phase 3: use normalized vehicle plate as the parking-card identifier.
-- Run only after the preflight queries return zero conflicts.
-- The backup table is intentionally retained for audit and rollback mapping.
USE parking_anpr;

-- PREFLIGHT 1: every card must reference a vehicle.
SELECT pc.id, pc.card_code
FROM parking_cards pc
LEFT JOIN vehicles v ON v.id = pc.vehicle_id
WHERE v.id IS NULL;

-- PREFLIGHT 2: one vehicle must not have multiple cards.
SELECT pc.vehicle_id, COUNT(*) AS card_count
FROM parking_cards pc
GROUP BY pc.vehicle_id
HAVING COUNT(*) > 1;

-- PREFLIGHT 3: normalized plates must be unique among cards.
SELECT UPPER(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(v.plate_number), '-', ''), '.', ''), ' ', ''), '_', '')) AS normalized_plate,
       COUNT(*) AS card_count
FROM parking_cards pc
JOIN vehicles v ON v.id = pc.vehicle_id
GROUP BY normalized_plate
HAVING COUNT(*) > 1 OR normalized_plate = '';

CREATE TABLE IF NOT EXISTS parking_card_code_migration_backup (
    parking_card_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    old_card_code VARCHAR(30) NOT NULL,
    normalized_plate VARCHAR(30) NOT NULL,
    backed_up_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (parking_card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

START TRANSACTION;

INSERT INTO parking_card_code_migration_backup
    (parking_card_id, vehicle_id, old_card_code, normalized_plate)
SELECT pc.id,
       pc.vehicle_id,
       pc.card_code,
       UPPER(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(v.plate_number), '-', ''), '.', ''), ' ', ''), '_', ''))
FROM parking_cards pc
JOIN vehicles v ON v.id = pc.vehicle_id
ON DUPLICATE KEY UPDATE
    vehicle_id = VALUES(vehicle_id),
    old_card_code = parking_card_code_migration_backup.old_card_code,
    normalized_plate = VALUES(normalized_plate);

UPDATE parking_cards pc
JOIN vehicles v ON v.id = pc.vehicle_id
SET pc.card_code = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(v.plate_number), '-', ''), '.', ''), ' ', ''), '_', ''));

COMMIT;

-- POSTCHECK: these queries must return zero rows.
SELECT pc.id, pc.card_code, v.plate_number
FROM parking_cards pc
JOIN vehicles v ON v.id = pc.vehicle_id
WHERE pc.card_code <> UPPER(REPLACE(REPLACE(REPLACE(REPLACE(TRIM(v.plate_number), '-', ''), '.', ''), ' ', ''), '_', ''));

SELECT card_code, COUNT(*) AS duplicate_count
FROM parking_cards
GROUP BY card_code
HAVING COUNT(*) > 1;

-- Summary: card/pass attributes are unchanged; only card_code is migrated.
SELECT COUNT(*) AS migrated_cards FROM parking_cards;
SELECT COUNT(*) AS backup_rows FROM parking_card_code_migration_backup;
