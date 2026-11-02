# ADR-0002: Sử dụng tài khoản database, RBAC và JWT cho Desktop

## Status

Accepted

## Date

2026-10-10

## Context

Project hiện dùng tài khoản Web demo và các API Desktop chưa có danh tính người vận hành đáng tin cậy. Điều này không đủ để phân biệt quản trị viên với nhân viên cổng, thu hồi phiên hoặc truy vết người thực hiện ngoại lệ.

Web quản trị và Desktop có đặc điểm khác nhau: trình duyệt phù hợp với session cookie và CSRF, trong khi Desktop cần bearer token, thời hạn ngắn và khả năng refresh có kiểm soát.

## Decision

Đề xuất lưu tài khoản trong database với mật khẩu BCrypt và hai vai trò tối thiểu:

- `MANAGEMENT`: quản trị hệ thống, tài khoản, giá, dữ liệu cư dân, doanh thu và cấu hình ô đỗ.
- `GATE_STAFF`: thực hiện nghiệp vụ vận hành tại cổng trong phạm vi được cấp.

Web tiếp tục dùng session cookie và CSRF. Desktop dùng JWT ký bằng RS256:

- Access token có thời hạn ngắn, tối đa 15 phút.
- Refresh token được rotation sau mỗi lần dùng và chỉ lưu dạng hash.
- Token gắn với tài khoản, phiên Desktop và phiên bản bảo mật của tài khoản.
- Đổi mật khẩu, đổi quyền hoặc khóa tài khoản phải làm mất hiệu lực các phiên liên quan.
- Các endpoint không được khai báo quyền phải bị từ chối mặc định.

Quyết định được chuyển sang `Accepted` sau khi schema, cấu hình khóa, Desktop client và test phân quyền được tích hợp thành công trên branch tích hợp. Runtime Aiven vẫn cần migration, RSA key và bật biến môi trường JWT trước khi coi như đã triển khai thật.

## Alternatives Considered

### Giữ tài khoản cấu hình cứng và API không xác thực

- Ưu điểm: dễ chạy demo.
- Nhược điểm: không có danh tính nhân viên, thu hồi phiên hoặc RBAC thực tế.
- Kết luận: không đáp ứng mục tiêu bảo mật.

### Dùng API key chung cho mọi Desktop

- Ưu điểm: triển khai đơn giản.
- Nhược điểm: không phân biệt người dùng; lộ một key ảnh hưởng mọi trạm; audit không đáng tin cậy.
- Kết luận: chỉ phù hợp cho kết nối service-to-service, không phù hợp đăng nhập nhân viên.

### Dùng session cookie cho cả Desktop

- Ưu điểm: tái sử dụng cơ chế Web.
- Nhược điểm: quản lý cookie và CSRF trên native client phức tạp; khó biểu diễn phiên thiết bị và refresh rotation.
- Kết luận: không chọn.

## Consequences

- Cần migration cho tài khoản, Desktop session, refresh token và audit event.
- Cần quản lý RSA private/public key ngoài source code.
- Desktop phải có màn hình đăng nhập, refresh và logout.
- Mọi quyết định thủ công có thể gắn với danh tính người vận hành.
- Phải kiểm thử quyền `MANAGEMENT`, `GATE_STAFF` và anonymous cho từng nhóm API.
- Login throttling trong RAM chỉ là biện pháp demo, không được mô tả là bảo vệ phân tán hoàn chỉnh.
