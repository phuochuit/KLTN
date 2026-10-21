# Các phần còn thiếu trước khi triển khai thực tế

Cập nhật 21/10/2026, sau khi hoàn tất phần mềm Giai đoạn 5.

## Đã hoàn tất

- Barrier điều khiển qua `IBarrierController` + bộ mô phỏng mặc định, tự đóng theo cấu hình, báo trạng thái lỗi thiết bị.
- Cấu hình trạm gác lưu cục bộ (`station-settings.json`), tương thích biến môi trường cũ.
- Camera cấu hình được theo USB index hoặc RTSP/HTTP/HTTPS, truyền end-to-end Desktop → Spring → FastAPI, có kiểm tra URI và khóa đồng thời.
- Sổ giao dịch thống nhất cho phí lượt và phí gói, biên nhận, đối soát ca, quyền `PAYMENT_RECONCILE`, báo cáo đối soát.
- JWT cho Desktop, service token Spring → FastAPI, chống replay, lưu evidence/sinh trắc học trong vùng private, thời hạn lưu và audit trước/sau.
- Phân quyền động theo vai trò, mã nhân viên theo tiền tố vai trò, hồ sơ nhân viên đầy đủ, khóa/mở tài khoản, nhật ký bảo mật.
- Outbox Desktop ghi atomic, tự gửi lại sau phục hồi kết nối; Spring dùng UUID + SHA-256 payload để chống gửi trùng và trả response đã lưu.
- Quy tắc phân chỗ theo nhóm ô tô/xe hai bánh áp dụng cho tự động, ô cố định, đỗ nhờ và điều phối thủ công.
- Bộ script đóng gói release, backup MySQL/private data, health check và checklist nghiệm thu/bàn giao.
- API trạm gác fail-closed: JWT bật mặc định; request nghiệp vụ không token trả `401`.

## Còn thiếu

1. Driver thiết bị thật: relay/PLC barrier, cảm biến vòng từ, đầu đọc RFID, máy in vé, máy POS. Cần hãng/model/protocol cụ thể để viết adapter.
2. Chống giả mạo khuôn mặt (liveness): hiện dùng YuNet + SFace, chưa phát hiện ảnh/video phát lại; cần hiệu chỉnh ngưỡng bằng dữ liệu camera thực tế và quy trình xử lý kết quả REVIEW.
3. Cần nhập sơ đồ tầng/khu, mã ô và kích thước thực tế thay cho dữ liệu khởi tạo; ràng buộc nhóm xe đã được triển khai.
4. TLS/mTLS cho triển khai thật và chữ ký kết quả AI để tránh giả mạo `faceVerified`.
5. Mã hóa dữ liệu sinh trắc học khi lưu trữ bằng KMS/ổ đĩa mã hóa của môi trường triển khai; script backup, checksum và vùng private đã có.
6. Thực hiện checklist nghiệm thu tại hiện trường: camera ban đêm, ngược sáng, mưa, biển số bẩn/che, mất mạng, mất điện, xe bám đuôi và mở barrier thủ công.
7. Cấu hình lịch backup/monitoring thật trên Task Scheduler hoặc nền tảng vận hành của đơn vị và diễn tập phục hồi định kỳ.
8. Kiểm thử lại WinForms sau mỗi bản cập nhật .NET SDK Windows (hiện dùng .NET 8 LTS).
