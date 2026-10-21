-- Additive payment ledger and shift reconciliation schema for MySQL 8 / Aiven.
-- Back up parking_anpr before execution.
USE parking_anpr;

SET @add_pending_payment = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'subscription_payments' AND column_name = 'pending_payment') = 0,
    'ALTER TABLE subscription_payments ADD COLUMN pending_payment BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1');
PREPARE statement FROM @add_pending_payment; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_payment_transaction_id = IF(
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
        AND table_name = 'subscription_payments' AND column_name = 'payment_transaction_id') = 0,
    'ALTER TABLE subscription_payments ADD COLUMN payment_transaction_id BIGINT NULL', 'SELECT 1');
PREPARE statement FROM @add_payment_transaction_id; EXECUTE statement; DEALLOCATE PREPARE statement;

INSERT IGNORE INTO security_role_permissions (role_id, permission_code)
SELECT id, 'PAYMENT_RECONCILE' FROM security_roles WHERE code = 'MANAGEMENT';

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    receipt_number VARCHAR(40) NOT NULL,
    payment_type VARCHAR(32) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount DECIMAL(12,0) NOT NULL,
    external_reference VARCHAR(120) NULL,
    parking_session_id BIGINT NULL,
    subscription_payment_id BIGINT NULL,
    collected_by_account_id BIGINT NOT NULL,
    gate_shift_assignment_id BIGINT NULL,
    paid_at TIMESTAMP(6) NOT NULL,
    reconciled_by_account_id BIGINT NULL,
    reconciled_at TIMESTAMP(6) NULL,
    reconciliation_note VARCHAR(500) NULL,
    CONSTRAINT pk_payment_transactions PRIMARY KEY (id),
    CONSTRAINT uk_payment_transactions_receipt UNIQUE (receipt_number),
    CONSTRAINT uk_payment_transactions_parking_session UNIQUE (parking_session_id),
    CONSTRAINT uk_payment_transactions_subscription UNIQUE (subscription_payment_id),
    CONSTRAINT fk_payment_transactions_session FOREIGN KEY (parking_session_id)
        REFERENCES parking_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_transactions_subscription FOREIGN KEY (subscription_payment_id)
        REFERENCES subscription_payments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_transactions_collector FOREIGN KEY (collected_by_account_id)
        REFERENCES system_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_transactions_shift FOREIGN KEY (gate_shift_assignment_id)
        REFERENCES gate_shift_assignments(id) ON DELETE SET NULL,
    CONSTRAINT fk_payment_transactions_reconciler FOREIGN KEY (reconciled_by_account_id)
        REFERENCES system_accounts(id) ON DELETE RESTRICT,
    CONSTRAINT chk_payment_transactions_source CHECK (
        (parking_session_id IS NOT NULL AND subscription_payment_id IS NULL)
        OR (parking_session_id IS NULL AND subscription_payment_id IS NOT NULL)
    ),
    CONSTRAINT chk_payment_transactions_amount CHECK (amount >= 0)
);

SET @add_subscription_payment_fk = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema = DATABASE()
        AND table_name = 'subscription_payments' AND constraint_name = 'fk_subscription_payments_transaction') = 0,
    'ALTER TABLE subscription_payments ADD CONSTRAINT fk_subscription_payments_transaction FOREIGN KEY (payment_transaction_id) REFERENCES payment_transactions(id) ON DELETE RESTRICT',
    'SELECT 1');
PREPARE statement FROM @add_subscription_payment_fk; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @add_subscription_payment_uk = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'subscription_payments' AND index_name = 'uk_subscription_payments_transaction') = 0,
    'CREATE UNIQUE INDEX uk_subscription_payments_transaction ON subscription_payments(payment_transaction_id)', 'SELECT 1');
PREPARE statement FROM @add_subscription_payment_uk; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @create_paid_at_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_paid_at') = 0,
    'CREATE INDEX idx_payment_transactions_paid_at ON payment_transactions(paid_at)', 'SELECT 1');
PREPARE statement FROM @create_paid_at_index; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @create_status_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_status') = 0,
    'CREATE INDEX idx_payment_transactions_status ON payment_transactions(status)', 'SELECT 1');
PREPARE statement FROM @create_status_index; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @create_collector_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_collector') = 0,
    'CREATE INDEX idx_payment_transactions_collector ON payment_transactions(collected_by_account_id, paid_at)', 'SELECT 1');
PREPARE statement FROM @create_collector_index; EXECUTE statement; DEALLOCATE PREPARE statement;

SET @create_shift_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
        AND table_name = 'payment_transactions' AND index_name = 'idx_payment_transactions_shift') = 0,
    'CREATE INDEX idx_payment_transactions_shift ON payment_transactions(gate_shift_assignment_id, paid_at)', 'SELECT 1');
PREPARE statement FROM @create_shift_index; EXECUTE statement; DEALLOCATE PREPARE statement;
