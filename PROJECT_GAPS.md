# Các phần còn thiếu trước khi triển khai thực tế

1. Barrier hiện là mô phỏng trên WinForms. Cần driver/SDK relay hoặc PLC của thiết bị thật, cảm biến vòng từ và tín hiệu barrier đã đóng/mở.
2. Xác thực khuôn mặt dùng YuNet + SFace và chưa có liveness. Cần chống dùng ảnh/video giả, hiệu chỉnh ngưỡng bằng dữ liệu camera thực tế và quy trình xử lý kết quả REVIEW.
3. Camera đang dùng camera mặc định số 0. Cần màn hình cấu hình camera vào, camera ra, RTSP, độ phân giải và vùng quan tâm.
4. Ảnh được lưu trên ổ đĩa máy chủ. Cần chính sách mã hóa, phân quyền, thời hạn lưu/xóa, sao lưu và nhật ký người truy cập dữ liệu sinh trắc học.
5. API trạm gác hiện được mở để demo. Cần JWT/API key, chữ ký kết quả AI và TLS để tránh giả mạo `faceVerified`.
6. Sơ đồ A1-A50 là cấu hình khởi tạo. Cần nhập sơ đồ tầng/khu thật, loại vị trí, kích thước và quy tắc phân chỗ theo loại xe.
7. Chưa tích hợp RFID vật lý, máy in vé, máy POS/chuyển khoản, hóa đơn và đối soát ca trực.
8. Cần thêm phân quyền nhân viên/quản trị viên đầy đủ, quản lý tài khoản trong database, khóa tài khoản và audit log.
9. Cần kiểm thử camera ban đêm, ngược sáng, mưa, biển số bẩn/che, mất mạng, mất điện, xe bám đuôi và mở barrier thủ công.
10. WinForms đã được nâng lên .NET 8 LTS; cần kiểm thử lại sau mỗi bản cập nhật SDK Windows.
