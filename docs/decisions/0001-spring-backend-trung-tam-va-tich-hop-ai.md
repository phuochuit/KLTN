# ADR-0001: Spring Boot là backend nghiệp vụ trung tâm và FastAPI là dịch vụ AI độc lập

## Status

Accepted

## Date

2026-10-10

## Context

Hệ thống gồm ba ứng dụng:

- Spring Boot cung cấp Web quản trị, REST API nghiệp vụ và truy cập database.
- FastAPI thực hiện nhận dạng biển số, phân loại phương tiện và đối chiếu khuôn mặt.
- WinForms phục vụ nhân viên tại trạm gác.

Nghiệp vụ vào/ra, tính phí, gói tháng, phân ô đỗ và lưu lượt xe cần một nguồn quyết định nhất quán. Mô hình AI có thể thay đổi độc lập và không được tự ghi trực tiếp trạng thái nghiệp vụ vào database.

Luồng hiện tại cho phép Desktop gọi FastAPI để lấy kết quả AI rồi gửi dữ liệu nghiệp vụ sang Spring. Cách này phù hợp cho bản demo nhưng Spring hiện chưa thể chứng minh kết quả AI do dịch vụ tin cậy tạo ra.

## Decision

Spring Boot là backend nghiệp vụ trung tâm và là thành phần duy nhất được phép quyết định hoặc lưu thay đổi nghiệp vụ bãi xe.

FastAPI tiếp tục là dịch vụ AI độc lập, không truy cập trực tiếp database nghiệp vụ. Desktop là client vận hành tại cổng, không phải nguồn có thẩm quyền cho quyết định nhận dạng hoặc xác thực.

Khi lớp bảo mật AI được triển khai, ưu tiên luồng:

```text
Desktop → Spring Boot → FastAPI
                    ↓
                 Database
```

Spring gọi FastAPI bằng thông tin xác thực nội bộ, kiểm tra kết quả, áp chính sách ngưỡng và gắn kết quả với thao tác vào/ra. Cho đến khi luồng này được triển khai và kiểm thử đầy đủ, luồng Desktop gọi FastAPI trực tiếp vẫn được ghi nhận là giới hạn của bản hiện tại.

## Alternatives Considered

### Desktop gọi trực tiếp cả FastAPI và Spring

- Ưu điểm: đơn giản, ít thay đổi code, phù hợp demo trên một máy.
- Nhược điểm: Spring phải tin dữ liệu AI do Desktop khai báo; khó chống sửa payload và tái sử dụng kết quả.
- Kết luận: được duy trì tạm thời nhưng không phải kiến trúc bảo mật mục tiêu.

### Đưa toàn bộ AI vào Spring Boot

- Ưu điểm: một tiến trình và một API.
- Nhược điểm: hệ sinh thái Python phù hợp hơn cho YOLO, EasyOCR và OpenCV; làm tăng độ phức tạp triển khai mô hình trong Java.
- Kết luận: không chọn.

### Cho FastAPI ghi trực tiếp database

- Ưu điểm: giảm một lượt gọi API.
- Nhược điểm: phân tán luật nghiệp vụ và quyền ghi dữ liệu; khó duy trì transaction nhất quán.
- Kết luận: không chọn.

## Consequences

- Luật vào/ra, tính phí và phân ô đỗ tập trung tại Spring.
- FastAPI có thể được phát triển và triển khai độc lập với backend nghiệp vụ.
- Cần API nội bộ và secret hoặc cơ chế xác thực tương đương giữa Spring và FastAPI.
- Spring phải giới hạn kích thước, thời gian chờ và định dạng response từ AI.
- Desktop phải chuyển dần các thao tác AI qua Spring khi Gate Evidence được triển khai.
- Không được tuyên bố kết quả AI đã được chứng thực khi project vẫn dùng luồng trực tiếp hiện tại.
