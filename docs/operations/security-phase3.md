# Vận hành bảo mật Giai đoạn 3

Giai đoạn 3 chuyển toàn bộ yêu cầu AI của Desktop qua Spring Web và bảo vệ FastAPI bằng service token nội bộ.

```text
Desktop --JWT--> Spring Web --service token--> FastAPI ANPR
```

Desktop không lưu service token và không gọi trực tiếp cổng `8001`.

## 1. Cấu hình

Các launcher local tự tạo token ngẫu nhiên 256-bit tại:

```text
scripts/security/keys/internal-service-token.txt
```

File này nằm trong thư mục bị Git ignore. Không sao chép token vào source, tài liệu hoặc log. Trong môi trường triển khai, cấu hình token bằng secret manager:

```text
PARKING_INTERNAL_SERVICE_TOKEN=<current-token>
PARKING_INTERNAL_PREVIOUS_TOKEN=<previous-token-during-rotation>
PARKING_AI_BASE_URL=http://127.0.0.1:8001
```

Spring chỉ gửi token hiện tại. FastAPI chấp nhận token hiện tại và token trước đó trong thời gian rotation. Xóa token trước đó sau khi mọi Spring instance đã chuyển sang token mới.

Timeout có thể điều chỉnh bằng `PARKING_AI_CONNECT_TIMEOUT` và `PARKING_AI_READ_TIMEOUT`. Mặc định là 5 giây để kết nối và 5 phút để xử lý.

Hai nguồn camera được cấu hình độc lập:

```text
PARKING_VEHICLE_CAMERA_INDEX=0
PARKING_FACE_CAMERA_INDEX=1
```

Khi hai index khác nhau, nhận dạng xe và khuôn mặt có thể chụp song song. Bản demo laptop đặt cả hai bằng `0`; coordinator sẽ xếp hàng hai tác vụ để không tranh chấp camera. Đây là chế độ demo bổ sung, không phải kiến trúc triển khai chính thức.

## 2. Endpoint

Desktop gọi Spring bằng JWT:

- `GET /api/ai/health`
- `POST /api/ai/recognize/image`
- `POST /api/ai/recognize/video`
- `POST /api/ai/recognize/camera`
- `POST /api/ai/face/verify-camera`
- `POST /api/ai/face/capture-camera`
- `POST /api/ai/face/verify-image`
- `POST /api/ai/face/verify-video`

Spring gọi endpoint tương ứng của FastAPI và tự gắn `X-Parking-Service-Token`. FastAPI chỉ để public `/health` nhằm phục vụ launcher; tài liệu và endpoint AI yêu cầu service token.

## 3. Giới hạn và lỗi

- Ảnh tối đa 10 MB; hỗ trợ JPEG, PNG, BMP và WebP.
- Video tối đa 25 MB; hỗ trợ MP4, AVI, MOV, MKV, WMV và WebM.
- Spring không chuyển tiếp cookie, Authorization hoặc header tùy ý từ Desktop sang FastAPI.
- Lỗi kết nối và lỗi AI được đổi thành thông báo hướng người dùng; response không lộ token hoặc exception nội bộ.
- Mỗi request có `X-Request-ID`. Kết quả AI có `contractVersion`, `requestId`, `evidenceId`, `processedAt` và `modelVersion` để làm nền tảng cho Gate Evidence.

## 4. Audit

Các thao tác thành công được ghi trong `security_audit_events`:

- `AI_RECOGNIZE_IMAGE`
- `AI_RECOGNIZE_VIDEO`
- `AI_RECOGNIZE_CAMERA`
- `AI_FACE_VERIFY`
- `AI_FACE_CAPTURE`

Audit gắn với tài khoản Desktop đang xác thực và request ID. Không ghi ảnh base64, JWT hoặc service token vào audit.

## 5. Luồng vận hành đã nghiệm thu

- Desktop chia hai nguồn `XE / BIỂN SỐ` và `NGƯỜI / KHUÔN MẶT`; mỗi nguồn hỗ trợ ảnh, video và camera.
- Hai camera khác index chạy song song. Laptop dùng chung một camera được coordinator xếp hàng an toàn.
- Video xe quét tối đa 12 frame, OCR/phân loại 3 frame tốt nhất và trả frame tốt nhất làm bằng chứng chính.
- Khi xử lý media xe, Desktop đồng thời chụp khuôn mặt, tra cứu cư dân rồi đối chiếu chính ảnh vừa chụp; khách được lưu ảnh vào theo lượt.
- Biển số chuẩn hóa là mã nhận diện cho xe đăng ký và khách vãng lai. Desktop không yêu cầu nhập mã thẻ.
- Migration `scripts/database/plate-as-card-identifier-mysql.sql` sao lưu mã cũ tại `parking_card_code_migration_backup` trước khi cập nhật Aiven.
- Ô đỗ cũ chưa có metadata được xem là ô dùng chung linh hoạt; nhân viên trạm gác chỉ được điều phối lượt xe, còn cấu hình/gán cố định vẫn thuộc Web quản trị.

## 6. Nghiệm thu

1. Chạy test Python bằng `python -m pytest tests` và test Spring bằng `mvn.cmd clean test`.
2. Build WinForms Release.
3. Tắt các launcher cũ rồi chạy `run-all.cmd`.
4. `GET http://localhost:8001/health` trả `200`.
5. Gọi endpoint AI FastAPI không có service token trả `401`.
6. Gọi `/api/ai/health` trên Spring không có JWT trả `401`.
7. Đăng nhập Desktop bằng `GATE_STAFF`, bấm **Kiểm tra** và nhận trạng thái **Hệ thống đang hoạt động**.
8. Nhận dạng một ảnh; audit phải có request ID và response phải có evidence ID.
9. Thu hồi phiên Desktop; mọi request nghiệp vụ và AI tiếp theo đều bị từ chối.

Kết quả chốt Giai đoạn 3 ngày 17/10/2026:

- Spring: `70` test đạt, `0` failure, `0` error.
- FastAPI/ANPR: `6` test đạt.
- WinForms Release: `0` warning, `0` error.
- Ảnh biển số kiểm thử `59F241578`: khoảng `2.10-2.35s`, OCR `0.8675`.
- Video kiểm thử: nhận đúng `59F241578`, frame tốt nhất `14`, khoảng `3.625s`.
- Nghiệm thu thực tế đạt cho hai ảnh xe/khuôn mặt, gói theo biển số, khách vãng lai và tự động cấp ô.

Nếu từng chạy nhiều `run-anpr.ps1`, đóng các cửa sổ ANPR cũ trước khi nghiệm thu để tránh nhiều vòng lặp Uvicorn cạnh tranh cổng 8001.

## 7. Rotation và rollback

Rotation:

1. Đặt token cũ vào `PARKING_INTERNAL_PREVIOUS_TOKEN` trên FastAPI.
2. Đặt token mới vào `PARKING_INTERNAL_SERVICE_TOKEN` trên FastAPI và Spring.
3. Restart FastAPI rồi Spring.
4. Xác minh proxy hoạt động.
5. Xóa previous token và restart FastAPI.

Không rollback bằng cách mở public endpoint AI. Khi FastAPI gặp lỗi cấu hình token, hệ thống cố ý trả `503` (fail closed).
