-- Additive dynamic RBAC and employee profile schema for MySQL 8 / Aiven.
-- Back up parking_anpr before execution. Legacy system_accounts.role remains for rollback.
USE parking_anpr;

CREATE TABLE IF NOT EXISTS security_roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    employee_prefix VARCHAR(8) NOT NULL,
    description VARCHAR(500) NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    next_employee_number BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_security_roles PRIMARY KEY (id),
    CONSTRAINT uk_security_roles_code UNIQUE (code),
    CONSTRAINT uk_security_roles_employee_prefix UNIQUE (employee_prefix)
);

CREATE TABLE IF NOT EXISTS security_role_permissions (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    CONSTRAINT pk_security_role_permissions PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_security_role_permissions_role FOREIGN KEY (role_id)
        REFERENCES security_roles (id) ON DELETE CASCADE
);

INSERT INTO security_roles
    (code, display_name, employee_prefix, description, system_role, enabled, next_employee_number)
VALUES
    ('MANAGEMENT', 'Quản trị viên', 'QT', 'Vai trò hệ thống có toàn quyền quản trị.', TRUE, TRUE, 1),
    ('GATE_STAFF', 'Nhân viên gác cổng', 'GC', 'Vận hành trạm gác và xử lý kết quả nhận dạng trong ca trực.', TRUE, TRUE, 1)
ON DUPLICATE KEY UPDATE code = VALUES(code);

INSERT IGNORE INTO security_role_permissions (role_id, permission_code)
SELECT id, permission_code
FROM security_roles
JOIN (
    SELECT 'DASHBOARD_VIEW' permission_code UNION ALL SELECT 'ACCOUNT_MANAGE'
    UNION ALL SELECT 'ROLE_MANAGE' UNION ALL SELECT 'SHIFT_MANAGE'
    UNION ALL SELECT 'RESIDENT_MANAGE' UNION ALL SELECT 'VEHICLE_MANAGE'
    UNION ALL SELECT 'CARD_MANAGE' UNION ALL SELECT 'PRICING_MANAGE'
    UNION ALL SELECT 'SLOT_MANAGE' UNION ALL SELECT 'HISTORY_VIEW'
    UNION ALL SELECT 'SECURITY_AUDIT_VIEW' UNION ALL SELECT 'PRIVATE_EVIDENCE_VIEW'
    UNION ALL SELECT 'GATE_OPERATE' UNION ALL SELECT 'AI_RESULT_CORRECT'
) permissions
WHERE security_roles.code = 'MANAGEMENT';

INSERT IGNORE INTO security_role_permissions (role_id, permission_code)
SELECT id, permission_code
FROM security_roles
JOIN (
    SELECT 'DASHBOARD_VIEW' permission_code
    UNION ALL SELECT 'GATE_OPERATE'
    UNION ALL SELECT 'AI_RESULT_CORRECT'
) permissions
WHERE security_roles.code = 'GATE_STAFF';

SET @add_role_id = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'role_id') = 0,
    'ALTER TABLE system_accounts ADD COLUMN role_id BIGINT NULL', 'SELECT 1');
PREPARE statement FROM @add_role_id; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_employee_code = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'employee_code') = 0,
    'ALTER TABLE system_accounts ADD COLUMN employee_code VARCHAR(24) NULL', 'SELECT 1');
PREPARE statement FROM @add_employee_code; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_phone = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'phone') = 0,
    'ALTER TABLE system_accounts ADD COLUMN phone VARCHAR(20) NULL', 'SELECT 1');
PREPARE statement FROM @add_phone; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_email = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'email') = 0,
    'ALTER TABLE system_accounts ADD COLUMN email VARCHAR(160) NULL', 'SELECT 1');
PREPARE statement FROM @add_email; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_citizen_id = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'citizen_id') = 0,
    'ALTER TABLE system_accounts ADD COLUMN citizen_id VARCHAR(20) NULL', 'SELECT 1');
PREPARE statement FROM @add_citizen_id; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_date_of_birth = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'date_of_birth') = 0,
    'ALTER TABLE system_accounts ADD COLUMN date_of_birth DATE NULL', 'SELECT 1');
PREPARE statement FROM @add_date_of_birth; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_address = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND column_name = 'address') = 0,
    'ALTER TABLE system_accounts ADD COLUMN address VARCHAR(500) NULL', 'SELECT 1');
PREPARE statement FROM @add_address; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_role_fk = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
        AND table_name = 'system_accounts' AND constraint_name = 'fk_system_accounts_security_role') = 0,
    'ALTER TABLE system_accounts ADD CONSTRAINT fk_system_accounts_security_role FOREIGN KEY (role_id) REFERENCES security_roles(id) ON DELETE RESTRICT',
    'SELECT 1');
PREPARE statement FROM @add_role_fk; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_employee_code_uk = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND index_name = 'uk_system_accounts_employee_code') = 0,
    'CREATE UNIQUE INDEX uk_system_accounts_employee_code ON system_accounts(employee_code)', 'SELECT 1');
PREPARE statement FROM @add_employee_code_uk; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_email_uk = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND index_name = 'uk_system_accounts_email') = 0,
    'CREATE UNIQUE INDEX uk_system_accounts_email ON system_accounts(email)', 'SELECT 1');
PREPARE statement FROM @add_email_uk; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_citizen_id_uk = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'system_accounts' AND index_name = 'uk_system_accounts_citizen_id') = 0,
    'CREATE UNIQUE INDEX uk_system_accounts_citizen_id ON system_accounts(citizen_id)', 'SELECT 1');
PREPARE statement FROM @add_citizen_id_uk; EXECUTE statement; DEALLOCATE PREPARE statement;

-- role_id and employee_code are backfilled transactionally by SecurityRoleBootstrap.
-- They remain nullable during rollout so existing accounts are never lost or invalidated.
