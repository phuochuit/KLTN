# Đồng bộ Aiven ↔ MySQL local

Mục tiêu: Aiven là DB chính; MySQL Laragon là phương án dự phòng. Khi Aiven
không dùng được, web ghi vào Laragon. Khi Aiven hoạt động lại, dữ liệu phát sinh
ở Laragon được đẩy lên Aiven.

## Chạy

```cmd
REM Chỉ đọc, không ghi DB
scripts\sync\sync-two-way.cmd --dry-run

REM Chạy một lần
scripts\sync\sync-two-way.cmd --once

REM Chạy nền, lặp mỗi 5 phút
scripts\start-sync.cmd
```

`scripts\start-auto-parking.cmd` tự bật vòng lặp đồng bộ. Dùng `--no-sync` nếu
muốn khởi động web/ANPR mà không bật đồng bộ.

Log:

- `scripts/sync/logs/sync.log`
- `scripts/sync/logs/conflicts.log`

## Quy tắc đồng bộ

- Bảng cấu hình/danh mục: Aiven thắng khi hai bên khác nhau.
- Bảng chỉ ghi thêm (audit, thanh toán, bằng chứng, sự kiện): hợp nhất theo khóa
  chính; nếu cùng khóa mà nội dung khác thì ghi conflict và **không ghi đè**.
- `parking_sessions`: bên có `exit_time` mới hơn thắng; nếu cả hai đang mở thì
  Aiven thắng.
- `parking_slots`: ưu tiên bên có `current_session_id`; nếu cả hai có thì so
  `entry_time` của phiên tương ứng.
- `system_accounts`: so `security_version`, bên cao hơn thắng.
- `parking_operations`: so `updated_at`.
- Không đồng bộ xóa cứng (schema không có tombstone). Bản ghi bị xóa ở một bên
  có thể xuất hiện lại từ bên kia.
- Không đồng bộ `desktop_sessions` và `desktop_refresh_tokens` để tránh mang
  token đăng nhập giữa hai môi trường.
- Bỏ qua hai bảng backup migration.

## ID và xung đột ID

- Khi chạy lần đầu, script đặt `AUTO_INCREMENT` của các bảng có khóa `id` đơn ở
  local lên `1.000.000.000.000`. Nhờ vậy dữ liệu phát sinh khi fallback không
  trùng ID với Aiven.
- Sau khi đẩy ID cao lên Aiven, script đưa `AUTO_INCREMENT` của Aiven về
  `max(ID thấp) + 1` để Aiven tiếp tục dùng dải ID thấp.
- Nếu phát hiện cùng khóa chính nhưng nội dung khác ở bảng chỉ ghi thêm, script
  ghi vào `conflicts.log` để xử lý tay.

## Giới hạn cần biết

- Đây là đồng bộ theo bảng, không phải replication nhị phân. Khi Aiven đang chạy,
  mọi thay đổi cấu hình/danh mục ở local có thể bị Aiven ghi đè.
- Khi Aiven mất kết nối, script ghi log và bỏ qua vòng đó; dữ liệu local vẫn
  được giữ và sẽ được đẩy lên khi Aiven hoạt động lại.
- Nên backup trước lần chạy thật đầu tiên. Backup hiện có ở `backups/sync-before-*`.
- Không chạy hai tiến trình đồng bộ cùng lúc; script dùng `.sync.lock`.
