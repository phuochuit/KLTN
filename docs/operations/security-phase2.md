# Vận hành bảo mật Giai đoạn 2

Giai đoạn 2 thêm JWT RS256 cho Desktop, refresh-token rotation và thu hồi phiên khi thay đổi trạng thái tài khoản. RSA key, issuer, audience và secret không được lưu trong Git.

## 1. Điều kiện trước

- Giai đoạn 1 đã chạy thành công trên Aiven.
- Database hiện tại đúng là `parking_anpr`.
- Đã có backup Aiven và kiểm tra khôi phục.
- Web và Desktop đã đóng trước khi chạy migration.
- Maven test đã thành công với cấu hình H2 cô lập.
- WinForms đã build thành công.

## 2. Migration session

Script additive:

```text
scripts/database/security-phase2-mysql.sql
```

Trong HeidiSQL, chọn database `parking_anpr` rồi chạy script. Script chỉ tạo:

```text
desktop_sessions
desktop_refresh_tokens
```

Không sửa hay xóa bảng nghiệp vụ. Sau khi chạy, kiểm tra:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN (
    'system_accounts',
    'security_audit_events',
    'desktop_sessions',
    'desktop_refresh_tokens'
  )
ORDER BY table_name;
```

## 3. Tạo RSA key

Trên Windows, chạy từ thư mục project:

```cmd
scripts\security\generate-desktop-jwt-keys.cmd
```

Script tạo:

```text
scripts/security/keys/desktop-jwt-private.pem
scripts/security/keys/desktop-jwt-public.pem
```

Quyền private key chỉ dành cho người dùng hiện tại. Không commit, không gửi qua chat, không đưa vào `application.properties`.

Nếu không dùng được OpenSSL, tạo cặp RSA 2048-bit theo PKCS#8/X.509 và đặt đúng hai đường dẫn trên.

## 4. Cấu hình bật JWT

Biến môi trường Windows:

```cmd
set "PARKING_SECURITY_JWT_ENABLED=true"
set "PARKING_SECURITY_JWT_ISSUER=https://parking.local"
set "PARKING_SECURITY_JWT_AUDIENCE=parking-desktop"
set "PARKING_SECURITY_JWT_KID=key-2026-10"
set "PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION=D:\Code\KhoaLuan\scripts\security\keys\desktop-jwt-private.pem"
set "PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION=D:\Code\KhoaLuan\scripts\security\keys\desktop-jwt-public.pem"
```

Lưu ý:

- Private và public key phải là cặp RSA khớp nhau.
- RSA tối thiểu 2048 bit.
- `kid` phải duy nhất cho mỗi vòng đời key.
- HTTP Desktop chỉ an toàn trên localhost. Mạng thật nên dùng HTTPS.
- Không đặt private key vào Git hoặn log.

### 4.1 Cấu hình Aiven

Sao chép `.env.local.example` thành `.env.local` và điền `PARKING_DB_URL`, `PARKING_DB_USERNAME`, `PARKING_DB_PASSWORD`. File `.env.local` bị Git ignore; không đưa credential vào `application.properties`, log hoặc tài liệu.

`run-all.cmd`, `run-web.cmd` và launcher tương thích `run-all-laragon.cmd` đều dùng Aiven `parking_anpr`. Biến môi trường hệ thống được ưu tiên hơn `.env.local`.

## 5. Chạy lại

Trong cùng terminal đã đặt biến JWT:

```cmd
run-all.cmd
```

Launcher nạp credential Aiven từ biến môi trường hoặc `.env.local`. `application.properties` chỉ chứa placeholder, không chứa secret.

Khi Desktop mở, hệ thống hiện form đăng nhập. Chỉ nhập tài khoản đã có trong `system_accounts`.

## 6. Kiểm tra thủ công

### 6.1 API không token

```powershell
try {
  Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/slots' -TimeoutSec 5
  throw 'API should require a JWT'
} catch {
  if ($_.Exception.Response.StatusCode -ne 401) { throw }
}
```

### 6.2 Login và Bearer token

```powershell
$body = @{
  username = '<ten_tai_khoan_gate_staff>'
  password = '<mat_khau>'
  deviceLabel = 'smoke-test'
} | ConvertTo-Json

$tokens = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' `
  -Method Post -ContentType 'application/json' -Body $body

Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/slots' `
  -Headers @{ Authorization = "Bearer $($tokens.accessToken)" } | Out-Null
```

Không chạy lệnh nếu tài khoản có `must_change_password=true`. Hãy đổi mật khẩu trên Web trước.

### 6.3 Refresh

```powershell
$refresh = @{ refreshToken = $tokens.refreshToken } | ConvertTo-Json
$rotated = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/refresh' `
  -Method Post -ContentType 'application/json' -Body $refresh
```

Sau khi rotate, token cũ không còn dùng được. Nếu gửi lại token cũ, API trả 401 và session bị thu hồi.

### 6.4 Logout

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/logout' `
  -Method Post `
  -Headers @{ Authorization = "Bearer $($rotated.accessToken)" }
```

## 7. Khóa và reset tài khoản

Sau khi khóa, reset mật khẩu hoặc đổi vai trò trên `/account-management`:

1. `security_version` của account tăng.
2. Các `desktop_sessions` đang mở được đánh dấu `revoked_at`.
3. Refresh token cũ bị từ chối.
4. Access token cũ bị từ chối ở lần gọi tiếp theo.
5. Desktop hiển thị thông báo đăng nhập lại.

### 7.1 Quản trị từng phiên

Tại `/account-management`, tài khoản `MANAGEMENT` có thể:

- Xem phiên Desktop còn hiệu lực, thời điểm đăng nhập, hoạt động gần nhất và thiết bị.
- Thu hồi riêng một phiên Desktop; refresh/access token của phiên đó bị từ chối ở lần dùng tiếp theo.
- Xem các phiên Web trên instance hiện tại và kết thúc từng phiên khác.
- Không thể tự kết thúc phiên Web đang dùng để tránh thao tác nhầm.

Mọi thao tác thu hồi được ghi vào `security_audit_events` với action `SESSION_REVOKE`. Giao diện không hiển thị JWT, refresh token hoặc session cookie.

### 7.2 Phân công ca làm

Migration additive:

```text
scripts/database/gate-shifts-mysql.sql
```

Ca làm chỉ được giao cho tài khoản `GATE_STAFF` đang hoạt động. Thời gian nhập trên Web được hiểu theo `Asia/Ho_Chi_Minh`, lưu dưới dạng `Instant`; ca qua đêm được hỗ trợ. Hệ thống từ chối ca kết thúc trước lúc bắt đầu, dài quá 24 giờ hoặc trùng ca của cùng nhân viên. Ca hiện tại và nhiệm vụ được ghép vào phiên Desktop đang hoạt động.

## 8. Key rotation

Khi cần đổi key:

1. Sinh cặp RSA mới.
2. Giữ public key cũ trong `verification-keys-json` trong khoảng overlap.
3. Cập nhật `kid` và public/private key đang ký.
4. Xác minh token mới và token cũ đều được chấp nhận trong khoảng chồng lấn.
5. Sau khi hết overlap, loại key cũ khỏi keyring.

## 9. Rollback tức thời

Nếu cần quay lại chế độ tương thích:

```cmd
set "PARKING_SECURITY_JWT_ENABLED=false"
```

Và restart Web/Desktop.

Không xóa hai bảng JWT. Chúng có thể trống và không ảnh hưởng bảng nghiệp vụ. Khôi phục dữ liệu từ backup nếu migration hoặc smoke test gặp sự cố.

## 10. Kiến trúc Desktop

- Access token lưu trong bộ nhớ.
- Refresh token lưu trong bộ nhớ, chỉ gửi tới origin đã đăng nhập.
- Desktop tự refresh trước khi access token hết hạn.
- Nếu refresh token đã được dùng, toàn bộ session bị thu hồi.
- Đóng ứng dụng gọi `/api/auth/logout`.
- Desktop kiểm tra hiệu lực phiên qua `GET /api/auth/session` theo chu kỳ ngắn. Khi quản trị viên thu hồi phiên, ứng dụng hiển thị thông báo và tự quay về màn hình đăng nhập.
- Mất kết nối tạm thời không tự đăng xuất người dùng; Desktop tiếp tục kiểm tra khi kết nối phục hồi.

## 11. Cập nhật giao diện quản trị

Trang `/account-management` tự cập nhật riêng danh sách phiên Desktop, phiên Web và lịch phân công mà không tải lại toàn trang. Nội dung đang nhập trong biểu mẫu không bị mất. Endpoint fragment `/account-management/live` chỉ dành cho vai trò `MANAGEMENT`.

Các thao tác có ảnh hưởng như thu hồi phiên, đặt lại mật khẩu, xóa ca, giải phóng ô đỗ và đăng xuất sử dụng hộp thoại Parking ANPR thay cho `alert()`/`confirm()` mặc định của trình duyệt. Nội dung hiển thị hướng tới người dùng và không trình bày timeout, chu kỳ polling hoặc chi tiết token.

## 12. Nghiệm thu trước khi phát hành

1. Chạy `mvn.cmd clean test` và xác nhận toàn bộ test dùng H2 memory.
2. Build WinForms Release, không có lỗi biên dịch.
3. Tắt toàn bộ hệ thống rồi chạy `run-all.cmd`; xác nhận Web, ANPR và Desktop cùng khởi động.
4. Đăng nhập Desktop, quan sát phiên xuất hiện trên Web mà không F5.
5. Thu hồi phiên từ Web; Desktop phải tự quay về đăng nhập trong vài giây.
6. Thử login, refresh, logout và reuse detection theo mục 6.
7. Xác nhận `.env.local`, RSA private key và credential không nằm trong Git.
8. Rotate credential từng bị lộ trước khi gắn nhãn phát hành.

Sau Giai đoạn 2, xem [Vận hành bảo mật Giai đoạn 3](security-phase3.md) cho Spring–FastAPI proxy và internal service authentication.
