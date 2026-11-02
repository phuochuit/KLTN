# ADR-0003: Lưu Gate Evidence làm nguồn tin cậy cho kết quả AI

## Status

Accepted

## Date

2026-10-10

## Context

Payload nghiệp vụ hiện có thể chứa biển số, `faceVerified` và `faceSimilarity` do Desktop gửi. Nếu backend tin trực tiếp các trường này, client bị sửa đổi có thể tự khai kết quả nhận dạng hoặc tái sử dụng một kết quả AI cho thao tác khác.

Hệ thống cần lưu ảnh vào/ra, giải thích quyết định AI, hỗ trợ kiểm tra ngoại lệ và truy vết người vận hành. Dữ liệu ảnh cũng cần thời hạn lưu và quyền truy cập rõ ràng.

## Decision

Spring tạo và quản lý `GateEvidence` cho mỗi lần xử lý AI. Bằng chứng tối thiểu phải gắn với:

- ID không đoán được và loại bằng chứng.
- Thao tác `ENTRY` hoặc `EXIT`.
- Tài khoản và phiên Desktop yêu cầu xử lý.
- Thời điểm tạo, hết hạn và sử dụng.
- Hash ảnh hoặc dữ liệu đầu vào cần đối chiếu.
- Biển số, loại xe, confidence và kết quả khuôn mặt liên quan.
- Ngưỡng quyết định do Spring áp dụng.
- Lượt gửi xe sau khi nghiệp vụ hoàn thành.

API nghiệp vụ tham chiếu `evidenceId`; Spring không tin cờ `faceVerified` hoặc điểm similarity do Desktop tự khai. Evidence chỉ được sử dụng đúng thao tác, đúng phiên, trong thời hạn và không được tái sử dụng ngoài chính sách.

Ảnh bằng chứng được lưu ngoài vùng public static, có kiểm tra đường dẫn, giới hạn kích thước, phân quyền truy cập, audit và retention cleanup.

Quyết định được chuyển sang `Accepted` ngày 2026-10-20 sau khi schema/storage, Spring–FastAPI proxy, Desktop, private-image RBAC/audit, correction và test chống replay được tích hợp; migration Aiven, runtime health và smoke test Web/Desktop đều đạt.

## Alternatives Considered

### Tin trực tiếp payload Desktop

- Ưu điểm: ít code và không cần lưu trạng thái trung gian.
- Nhược điểm: client có thể sửa kết quả, không chứng minh được nguồn AI và khó chống replay.
- Kết luận: không chấp nhận cho kiến trúc bảo mật mục tiêu.

### FastAPI ký HMAC hoặc JWT để Desktop chuyển tiếp

- Ưu điểm: giữ luồng Desktop gọi FastAPI trực tiếp và cho phép Spring kiểm tra chữ ký.
- Nhược điểm: vẫn phải thiết kế nonce, thời gian, hash ảnh, audience và chống replay; secret verification có thể bị phân tán.
- Kết luận: là phương án dự phòng hợp lệ nhưng không được ưu tiên so với Spring điều phối AI và lưu evidence.

### Chỉ lưu ảnh, không lưu metadata quyết định

- Ưu điểm: schema đơn giản.
- Nhược điểm: không tái hiện được ngưỡng, kết quả mô hình hoặc liên kết với người vận hành.
- Kết luận: không đủ cho audit.

## Consequences

- Tăng dung lượng lưu trữ và cần chính sách retention.
- Cần migration database và private file storage.
- Luồng Desktop và API vào/ra phải thay đổi đồng bộ.
- Có thể giải thích và kiểm tra quyết định AI sau sự kiện.
- Manual override phải tham chiếu evidence và ghi lý do, người thực hiện.
- Evidence không thay thế liveness; khả năng chống ảnh/video phát lại phải được đánh giá riêng.
