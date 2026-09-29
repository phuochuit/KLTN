# Demo đối chiếu 3 ảnh khuôn mặt

Ứng dụng này nằm độc lập với `spring-web`, `desktop-winform` và `anpr-service`.
Ảnh chỉ được xử lý trong RAM trên máy đang chạy demo, không gọi Grok và không ghi
ảnh vào cơ sở dữ liệu.

## Chạy nhanh trên Windows

1. Nhấp đúp `run-demo.cmd`.
2. Lần chạy đầu sẽ tải hai mô hình chính thức từ OpenCV Zoo.
3. Mở `http://localhost:8002`.
4. Chọn ảnh CCCD, ảnh đăng ký và ảnh realtime rồi bấm **Bắt đầu đối chiếu**.

Nếu Windows mở `.ps1` bằng Notepad, không nhấp vào file `.ps1`; hãy chạy file
`run-demo.cmd`.

## Ý nghĩa kết quả

- `PASS`: cả ba phép so sánh vượt ngưỡng thử nghiệm và không có cảnh báo chất lượng.
- `REVIEW`: có điểm gần ngưỡng, ảnh chất lượng thấp, hoặc CCCD là nguồn đối chứng chưa nhất quán.
- `REJECT`: ảnh đăng ký không khớp CCCD hoặc realtime không khớp ảnh đăng ký.

Điểm hiển thị là cosine similarity, không phải phần trăm xác suất. Ngưỡng mặc định
`0.363` là giá trị tham khảo cho SFace; dự án thật phải thu thập tập kiểm thử phù hợp
để hiệu chỉnh ngưỡng và đo FMR/FNMR.

## Giới hạn bắt buộc phải biết

- Demo ảnh tĩnh chưa có liveness/anti-spoofing.
- Không dùng để tự động mở barrier.
- Không dùng ảnh CCCD hoặc dữ liệu cư dân thật khi chưa có đồng ý và quy trình bảo vệ dữ liệu.
- Ảnh nên chỉ có một khuôn mặt, nhìn tương đối thẳng, đủ sáng và không bị che.

