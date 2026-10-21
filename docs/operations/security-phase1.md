# Vận hành bảo mật Giai đoạn 1

Tài liệu này mô tả cách chuẩn bị tài khoản database, RBAC Web và security audit. Không lưu URL, username, password Aiven hoặc mật khẩu bootstrap trong Git.

## 1. Điều kiện trước khi triển khai

- Có backup Aiven đã kiểm tra khả năng phục hồi.
- Biết chính xác database/schema đang được Spring sử dụng.
- Dừng Web trong thời gian thay đổi schema.
- Chạy thử trên database clone hoặc staging trước production.
- 40 Maven test phải tiếp tục thành công.

## 2. Schema MySQL

Script additive nằm tại:

```text
scripts/database/security-phase1-mysql.sql
```

Script chỉ tạo `system_accounts` và `security_audit_events`; không xóa hoặc thay đổi bảng nghiệp vụ. `CREATE TABLE IF NOT EXISTS` không xác nhận cấu trúc của bảng đã tồn tại, vì vậy phải so sánh cột/index nếu database từng chạy một phiên bản bảo mật khác.

Không bật Flyway trực tiếp trên schema Aiven hiện hữu trước khi có baseline và thử nghiệm trên bản clone.

## 3. Bootstrap quản trị viên đầu tiên

Đặt biến môi trường chỉ cho lần khởi động tạo tài khoản:

```powershell
$env:PARKING_SECURITY_BOOTSTRAP_USERNAME = 'manager'
$env:PARKING_SECURITY_BOOTSTRAP_PASSWORD = '<mat-khau-tam-tu-12-den-72-byte>'
```

Sau khi Web khởi động:

1. Xác nhận tài khoản xuất hiện trong `system_accounts`.
2. Đăng nhập và đổi mật khẩu tại `/account/password`.
3. Xóa biến `PARKING_SECURITY_BOOTSTRAP_PASSWORD` khỏi môi trường/launcher.
4. Không ghi mật khẩu tạm vào log, ảnh chụp màn hình hoặc source code.

Bootstrap không ghi đè tài khoản trùng username. Nếu chỉ cấu hình một trong hai biến hoặc mật khẩu không đạt yêu cầu, ứng dụng từ chối khởi động.

## 4. Quản lý tài khoản và audit

- Trang tài khoản: `/account-management`.
- Nhật ký bảo mật: `/security-audit`.
- Chỉ `MANAGEMENT` được truy cập.
- Không thể khóa hoặc hạ quyền quản trị viên đang hoạt động cuối cùng.
- Tài khoản mới/reset phải đổi mật khẩu ở lần đăng nhập tiếp theo.
- Mật khẩu tạm chỉ hiển thị trong một redirect sau thao tác tạo/reset.

## 5. Audit retention

Mặc định giữ 365 ngày. Scheduler mặc định tắt.

```text
PARKING_SECURITY_AUDIT_CLEANUP_ENABLED=true
PARKING_SECURITY_AUDIT_RETENTION_DAYS=365
PARKING_SECURITY_AUDIT_CLEANUP_CRON=0 30 2 * * *
```

- Không được đặt retention dưới 30 ngày.
- Cleanup chạy theo UTC và xóa theo batch 100 dòng.
- Mỗi lần cleanup tạo sự kiện `AUDIT_RETENTION_CLEANUP`.
- Chỉ bật sau khi đã chốt yêu cầu lưu trữ và backup.

## 6. Smoke test Aiven

Sau khi migration và bootstrap trên clone/staging:

1. Đăng nhập đúng và kiểm tra `LOGIN_SUCCESS`.
2. Đăng nhập sai và kiểm tra `LOGIN_FAILURE`.
3. Tạo một tài khoản `GATE_STAFF`.
4. Xác nhận tài khoản này không mở được trang quản trị.
5. Đổi vai trò/khóa tài khoản và xác nhận Web session cũ mất hiệu lực.
6. Kiểm tra dashboard, đăng ký cư dân, phương tiện và dữ liệu tháng 9/10 vẫn nguyên vẹn.

## 7. Rollback

Nếu migration hoặc smoke test thất bại:

1. Dừng Web.
2. Không tiếp tục chạy launcher nhiều lần.
3. Lưu log lỗi và trạng thái schema để phân tích.
4. Phục hồi database từ backup đã kiểm tra.
5. Khởi động lại phiên bản ứng dụng trước tích hợp.

Không cung cấp script `DROP TABLE` tự động vì có thể xóa audit/tài khoản đã phát sinh sau triển khai.

## 8. Giới hạn đang chuyển sang Giai đoạn 2

`/uploads/**` còn public để FastAPI hiện tại tải ảnh qua URL do Desktop cung cấp. URL này chỉ được khóa sau khi Desktop có JWT và Spring trực tiếp điều phối dữ liệu ảnh sang FastAPI.
