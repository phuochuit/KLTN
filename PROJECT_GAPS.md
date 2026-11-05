# Các phần còn thiếu trước khi triển khai thực tế

Cập nhật 28/10/2026, gồm hardening trên `main` và nhánh `feat/pre-report-completion`.

## Đã hoàn tất

- Barrier điều khiển qua `IBarrierController` + bộ mô phỏng mặc định, tự đóng theo cấu hình, báo trạng thái lỗi thiết bị.
- Cấu hình trạm gác lưu cục bộ (`station-settings.json`), tương thích biến môi trường cũ.
- Camera cấu hình được theo USB index hoặc RTSP/HTTP/HTTPS, truyền end-to-end Desktop → Spring → FastAPI, có kiểm tra URI và khóa đồng thời.
- Sổ giao dịch thống nhất cho phí lượt và phí gói, biên nhận, đối soát ca, quyền `PAYMENT_RECONCILE`, báo cáo đối soát.
- JWT cho Desktop, service token Spring → FastAPI, chống replay, lưu evidence/sinh trắc học trong vùng private, thời hạn lưu và audit trước/sau.
- Phân quyền động theo vai trò, mã nhân viên theo tiền tố vai trò, hồ sơ nhân viên đầy đủ, khóa/mở tài khoản, nhật ký bảo mật.
- Outbox Desktop ghi atomic, tự gửi lại sau phục hồi kết nối **và khi Spring trả 5xx** (`TransientServerException`); Spring dùng UUID + SHA-256 payload để chống gửi trùng và trả response đã lưu.
- Liveness tách khỏi kết quả so khuôn mặt: `LivenessStatus` (`SPOOF`/`REVIEW`/`NOT_EVALUATED`), `SPOOF` không mở barrier, `REVIEW` phải xác nhận qua `ManualOverrideDialog`, ảnh tĩnh trả `NOT_EVALUATED`.
- Quy tắc phân chỗ theo nhóm ô tô/xe hai bánh áp dụng cho tự động, ô cố định, đỗ nhờ và điều phối thủ công.
- Bộ script đóng gói release, backup MySQL/private data, health check và checklist nghiệm thu/bàn giao.
- API trạm gác fail-closed: JWT bật mặc định; request nghiệp vụ không token trả `401`.
- Tài khoản quản trị mẫu được tạo khi bảng tài khoản còn trống (`ManagementBootstrap`), bắt buộc đổi mật khẩu ở lần đăng nhập đầu.
- Kiểm thử tự động hợp nhất bằng một runner: Spring, Desktop, ANPR và tooling, ghi báo cáo vào `.artifacts/test-runs/<run-id>/`.
- Giá và quyền gói được snapshot tại entry; thay bảng giá, khóa card hoặc gói hết hạn sau entry không làm đổi lượt đang mở.
- Cư dân ra bắt buộc chọn người lấy xe active, có quyền sử dụng xe và face match; confirm lưu `exitMember`. Chỉ manual override đủ quyền/lý do/evidence/ca trực mới được bỏ qua.
- Khách vãng lai bắt buộc có ảnh/face evidence lúc vào và đối chiếu lúc ra; evidence replay bị từ chối.
- Database bảo đảm mỗi biển số chỉ có một lượt OPEN bằng unique `open_plate`; confirm exit dùng pessimistic lock; idempotency reservation có kiểm thử hai thread.
- Known-total test xác minh tổng doanh thu và phân loại CASH/BANK_TRANSFER/POS cùng trạng thái chờ đối soát. Spring suite mới nhất đạt **163/163** trên H2.

## Còn thiếu

1. Driver thiết bị thật: relay/PLC barrier, cảm biến vòng từ, đầu đọc thẻ, máy in vé, máy POS. Cần hãng/model/protocol cụ thể để viết adapter.
2. Hiệu chỉnh ngưỡng bằng dữ liệu camera thật: `FACE_MATCH_THRESHOLD=0.363`, `FACE_REVIEW_THRESHOLD=0.300` và ngưỡng liveness motion-blink (`0.55`/`0.25`) mới là giá trị mặc định, chưa đo trên camera của bãi xe. Chống phát lại video/ảnh in/deepfake vẫn chưa có. Quy trình và công cụ đo: [face-threshold-tuning.md](docs/operations/face-threshold-tuning.md).
3. Cần nhập sơ đồ tầng/khu, mã ô và kích thước thực tế thay cho dữ liệu khởi tạo; ràng buộc nhóm xe đã được triển khai.
4. TLS/mTLS cho triển khai thật và chữ ký kết quả AI để tránh giả mạo `faceVerified`.
5. Mã hóa dữ liệu sinh trắc học khi lưu trữ bằng KMS/ổ đĩa mã hóa của môi trường triển khai; script backup, checksum và vùng private đã có.
6. Thực hiện checklist nghiệm thu tại hiện trường: camera ban đêm, ngược sáng, mưa, biển số bẩn/che, mất mạng, mất điện, xe bám đuôi và mở barrier thủ công.
7. Cấu hình lịch backup/monitoring thật trên Task Scheduler hoặc nền tảng vận hành của đơn vị và diễn tập phục hồi định kỳ.
8. Kiểm thử lại WinForms sau mỗi bản cập nhật .NET SDK Windows (hiện dùng .NET 8 LTS).
9. Chạy và xác nhận các migration SQL thủ công có version trên bản sao MySQL trước khi áp dụng dữ liệu thật; phạm vi đồ án không dùng Flyway theo [ADR-0007](docs/decisions/0007-schema-migration-khong-dung-flyway.md). Concurrency hiện đã pass trên H2 nhưng chưa chạy trên MySQL test riêng vì chưa có tài khoản/database test tách biệt; tuyệt đối không dùng Aiven hoặc `parking_anpr` chính cho destructive integration test.
