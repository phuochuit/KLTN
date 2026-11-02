# Các phần còn thiếu trước khi triển khai thực tế

Cập nhật 28/10/2026, sau khi hoàn tất hardening trên nhánh `feat/final-hardening`.

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

## Còn thiếu

1. Driver thiết bị thật: relay/PLC barrier, cảm biến vòng từ, đầu đọc thẻ, máy in vé, máy POS. Cần hãng/model/protocol cụ thể để viết adapter.
2. Hiệu chỉnh ngưỡng bằng dữ liệu camera thật: `FACE_MATCH_THRESHOLD=0.363`, `FACE_REVIEW_THRESHOLD=0.300` và ngưỡng liveness motion-blink (`0.55`/`0.25`) mới là giá trị mặc định, chưa đo trên camera của bãi xe. Chống phát lại video/ảnh in/deepfake vẫn chưa có. Quy trình và công cụ đo: [face-threshold-tuning.md](docs/operations/face-threshold-tuning.md).
3. Cần nhập sơ đồ tầng/khu, mã ô và kích thước thực tế thay cho dữ liệu khởi tạo; ràng buộc nhóm xe đã được triển khai.
4. TLS/mTLS cho triển khai thật và chữ ký kết quả AI để tránh giả mạo `faceVerified`.
5. Mã hóa dữ liệu sinh trắc học khi lưu trữ bằng KMS/ổ đĩa mã hóa của môi trường triển khai; script backup, checksum và vùng private đã có.
6. Thực hiện checklist nghiệm thu tại hiện trường: camera ban đêm, ngược sáng, mưa, biển số bẩn/che, mất mạng, mất điện, xe bám đuôi và mở barrier thủ công.
7. Cấu hình lịch backup/monitoring thật trên Task Scheduler hoặc nền tảng vận hành của đơn vị và diễn tập phục hồi định kỳ.
8. Kiểm thử lại WinForms sau mỗi bản cập nhật .NET SDK Windows (hiện dùng .NET 8 LTS).
9. Chuyển schema sang migration có version (Flyway) trước khi dùng dữ liệu thật; hiện schema do `ddl-auto=update` và script tay trong `scripts/database/` quản lý, xem [ADR-0007](docs/decisions/0007-schema-migration-khong-dung-flyway.md).
