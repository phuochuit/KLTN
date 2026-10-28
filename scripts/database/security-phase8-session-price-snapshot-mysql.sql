-- Giai đoạn 8: chốt bảng giá và quyền gói tại thời điểm xe vào.
-- Chạy sau khi backup MySQL. Các cột nullable để lượt OPEN cũ dùng fallback tương thích.

ALTER TABLE parking_sessions
    ADD COLUMN IF NOT EXISTS prepaid_package_applied BOOLEAN NULL,
    ADD COLUMN IF NOT EXISTS price_base_snapshot DECIMAL(12, 0) NULL,
    ADD COLUMN IF NOT EXISTS price_night_snapshot DECIMAL(12, 0) NULL,
    ADD COLUMN IF NOT EXISTS price_overnight_snapshot DECIMAL(12, 0) NULL,
    ADD COLUMN IF NOT EXISTS price_extra_block_snapshot DECIMAL(12, 0) NULL,
    ADD COLUMN IF NOT EXISTS base_hours_snapshot INT NULL,
    ADD COLUMN IF NOT EXISTS extra_block_hours_snapshot INT NULL;
