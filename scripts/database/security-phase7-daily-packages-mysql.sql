-- Giai đoạn 7: giá gói theo ngày. Chạy sau khi backup MySQL.
-- Script idempotent cho MySQL 8 / Laragon 8.0.30 và Aiven MySQL 8.

ALTER TABLE pricing_rules
    ADD COLUMN IF NOT EXISTS daily_pass_price DECIMAL(12, 0) NULL;

-- MySQL tạo enum từ phiên bản cũ chỉ có MONTHLY/PER_VISIT. Mở rộng enum để
-- gói theo ngày lưu được trên cả cơ sở dữ liệu đã tồn tại; chạy lại không làm
-- thay đổi các bản ghi hiện có.
ALTER TABLE parking_cards
    MODIFY COLUMN pass_type ENUM('DAILY', 'MONTHLY', 'PER_VISIT') NULL;

-- Giá khởi tạo đã chốt bằng phí qua đêm hiện hành; Admin có thể sửa trên trang Bảng giá.
UPDATE pricing_rules
SET daily_pass_price = overnight_fee
WHERE daily_pass_price IS NULL OR daily_pass_price = 0;
