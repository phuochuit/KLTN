-- Giai đoạn 9: chống hai lượt OPEN đồng thời cho cùng một biển số.
-- Chạy một lần sau khi backup. Nếu dữ liệu đang có biển số OPEN trùng, UPDATE sẽ
-- thất bại khi tạo unique constraint; cần đối soát và đóng lượt sai trước khi chạy lại.

ALTER TABLE parking_sessions
    ADD COLUMN open_plate VARCHAR(20) NULL;

UPDATE parking_sessions
SET open_plate = entry_plate
WHERE status = 'OPEN';

ALTER TABLE parking_sessions
    ADD CONSTRAINT uk_parking_sessions_open_plate UNIQUE (open_plate);
