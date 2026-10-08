# Parking ANPR Demo

Tài liệu canonical: [docs/README.md](docs/README.md). Hướng dẫn dưới đây không chứng nhận startup hoặc production readiness.

## Chức năng đã ghép

- Web Spring Boot lưu hộ gia đình, thành viên, ảnh khuôn mặt đăng ký, phương tiện, thẻ/gói tháng, bảng giá, lượt xe và 50 vị trí đỗ trong database.
- Form đăng ký cư dân cho phép tải ảnh hoặc chụp webcam; QR CCCD dùng để tự điền thông tin, không dùng ảnh CCCD để so khớp khuôn mặt.
- WinForms nhận diện biển số/loại xe, hiển thị sơ đồ vị trí đỗ ngay trên form và có cửa sổ sơ đồ lớn.
- Cư dân: UI hỗ trợ chọn người và so ảnh đăng ký với realtime. Người lấy xe phải có quyền theo từng xe; chưa chứng minh backend bắt buộc kiểm face/quyền ở mọi luồng xe ra.
- Khách vãng lai: hỗ trợ chụp ảnh vào và đối chiếu lúc ra; backend kiểm face có điều kiện khi có entry-face path, trừ override. Client flag không chứng minh xác thực thực sự.
- Barrier có nút mở/đóng, trạng thái trực quan và tự mở 5 giây sau khi lượt vào/ra hợp lệ. Đây là mô phỏng phần mềm, chưa điều khiển barrier vật lý.

Các hạng mục cần làm tiếp được ghi tại [PROJECT_GAPS.md](evidence/archive/PROJECT_GAPS.md).

## Python chạy được khi chuyển máy hoặc đổi thư mục

`scripts/run-anpr.cmd` không dùng đường dẫn Python tuyệt đối. Script tự tìm Python theo thứ tự: biến `PARKING_PYTHON`, Python Launcher `py`, rồi `python/python3` trong PATH. Môi trường `.venv` luôn được tạo trong `anpr-service` theo đường dẫn tương đối của project. Nếu `.venv` được chép từ máy khác và không còn hợp lệ, script giữ nó dưới tên `.venv.incompatible.<thời gian>` rồi tạo môi trường mới và cài `requirements.txt`.

Máy mới chỉ cần cài Python 3.10 trở lên và chọn **Add python.exe to PATH**. Nếu không muốn thêm PATH, có thể cấu hình trước khi chạy:

```powershell
$env:PARKING_PYTHON = 'E:\Python312\python.exe'
.\scripts\run-anpr.cmd
```

Hệ thống gồm ba ứng dụng:

- `spring-web`: Java Spring Boot + Thymeleaf cho quản trị và REST API nghiệp vụ. H2 dependency ở test scope; datasource runtime cần xác nhận theo cấu hình thực tế.
- `anpr-service`: Python FastAPI, YOLO chuyên phát hiện biển số, YOLO COCO phân loại xe và EasyOCR đọc ký tự.
- `desktop-winform`: C# WinForms cho trạm gác, tự điền biển số và loại xe từ ANPR.

## Chạy hệ thống

Cách dễ nhất trên Windows là bấm đúp `scripts/run-all.cmd`. File này mở Web, ANPR và Desktop.

Muốn chạy riêng từng phần, bấm đúp lần lượt:

1. `scripts/run-web.cmd`
2. `scripts/run-anpr.cmd`
3. `scripts/run-desktop.cmd`

Không bấm đúp file `.ps1`, vì Windows có thể đang liên kết loại file này với Notepad. Các launcher tự kiểm tra cổng để tránh mở trùng dịch vụ.

Hoặc chạy trong ba terminal PowerShell tại thư mục gốc project:

```powershell
.\scripts\run-web.ps1
.\scripts\run-anpr.ps1
.\scripts\run-desktop.ps1
```

### Chạy với MySQL của Laragon

1. Mở Laragon tại thư mục cài đặt trên máy và bấm **Start All**.
2. Xác nhận datasource đang dùng và đóng bản Parking Web cũ có kiểm soát nếu có.
3. Bấm đúp `scripts/run-all-laragon.cmd`.
4. Nhập mật khẩu MySQL `root` khi được hỏi.

Launcher kiểm cổng MySQL local 3306 nhưng không tự thiết lập datasource Spring; probe đăng nhập có thể bị bỏ qua khi không có MySQL client hợp lệ. Chưa xác minh runtime dùng Laragon, tự tạo database/schema hoặc chuyển dữ liệu từ H2. Xem [setup và troubleshooting](docs/operations/setup-and-troubleshooting.md) trước khi chạy.

## Cách dùng Desktop

1. Chờ Web chạy ở cổng `8080` và ANPR chạy ở cổng `8001`.
2. Mở Desktop và bấm **Kiểm tra**. Kết quả đúng là `Spring + ANPR đang hoạt động`.
3. Chọn tab **XE VÀO** hoặc **XE RA**.
4. Bấm **Nhận dạng ảnh** hoặc **Nhận dạng video**.
5. YOLO khoanh biển số, EasyOCR đọc ký tự và YOLO COCO xác định ô tô/xe máy.
6. Kiểm tra dữ liệu được tự điền rồi xác nhận nghiệp vụ.

Với ảnh quá cận chỉ thấy biển số, hệ thống dự phòng phân loại theo hình dạng biển. Với video, dịch vụ lấy mẫu tối đa 24 frame và chọn kết quả tốt nhất.

## Web quản trị

Mở `http://localhost:8080`:

- Tài khoản: `admin`
- Mật khẩu: `admin123`

Quy trình đăng ký hộ gia đình/gói tháng:

1. Vào **Hộ gia đình**, tạo căn hộ rồi khai báo đầy đủ từng thành viên: CCCD, ngày sinh, quan hệ, liên hệ, địa chỉ và đường dẫn ảnh đối chiếu.
2. Vào **Phương tiện**, chọn hộ gia đình, chủ xe đứng tên và tất cả thành viên được phép dùng chung xe; nhập biển số, đăng ký xe, hãng, mẫu, màu, số khung, số máy, số chỗ, phân khối và loại nhiên liệu.
3. Vào **Hạn mức**, đặt số xe hai bánh và ô tô mặc định cho mỗi hộ. Có thể ghi đè hạn mức riêng tại màn hình **Hộ gia đình**.
4. Vào **Bảng giá**, điều chỉnh giá ban ngày, ban đêm, qua đêm, gói 30 ngày và cách tính theo khung giờ.
5. Vào **Thẻ & gói**, gán mã thẻ cho xe, chọn **Gói 30 ngày** và ngày bắt đầu.
6. Hệ thống tạo payment record khi kích hoạt/gia hạn; record không chứng minh đã thu tiền hoặc đối soát thanh toán. Gói tháng hợp lệ được code tính phí ra `0 đ`; cần kiểm tra preview thực tế.
7. Gói hết hạn, thẻ sai xe hoặc không quét thẻ thì hệ thống cảnh báo và áp dụng giá khách vãng lai.

Các mức giá được ghi nhận dưới đây có thể được quản trị viên thay đổi; provenance và tính đại diện của khảo sát chưa xác minh. Kiểm tra bảng giá hiện tại, không coi đây là kết quả khảo sát đã được công nhận:

- Gói 30 ngày: xe đạp/xe đạp điện `100.000 đ`; xe máy/xe máy điện `150.000 đ`; mô tô phân khối lớn `300.000 đ`; ô tô 4-5 chỗ `1.500.000 đ`; ô tô 6-7 chỗ `1.700.000 đ`; ô tô 8-9 chỗ `1.800.000 đ`.
- Khách vãng lai: xe máy `5.000 đ` ban ngày, `8.000 đ` ban đêm, `10.000 đ` mỗi chu kỳ 24 giờ có qua đêm; xe đạp `2.000/3.000/5.000 đ`; ô tô `40.000 đ` cho 4 giờ đầu, thêm `20.000 đ` cho mỗi 2 giờ hoặc phần lẻ, tối thiểu `100.000 đ` cho mỗi đêm đi qua 00:00.

Dữ liệu mẫu:

- Xe máy: `59A112345`, thẻ `CARD001`
- Ô tô: `51H88888`, thẻ `CARD002`

## API

Spring Boot:

- `GET /api/parking/health`
- `POST /api/parking/entry`
- `POST /api/parking/exit-preview`
- `POST /api/parking/exit-confirm`

ANPR:

- `GET http://localhost:8001/health`
- `POST http://localhost:8001/recognize/image`
- `POST http://localhost:8001/recognize/video`
- Swagger: `http://localhost:8001/docs`

## Cấu hình mô hình

Mặc định:

- Phát hiện biển số: `anpr-service/models/license_plate_detector.pt`
- Phân loại xe: `anpr-service/models/vehicle_detector.pt`

Có thể thay trọng số bằng biến môi trường:

```powershell
$env:ANPR_PLATE_MODEL = 'D:\models\plate-best.pt'
$env:ANPR_VEHICLE_MODEL = 'D:\models\vehicle-best.pt'
.\scripts\run-anpr.ps1
```

Ngưỡng mặc định có thể đổi qua `ANPR_PLATE_CONFIDENCE` và `ANPR_VEHICLE_CONFIDENCE`.

## Giới hạn bản demo

- Chạy CPU nên nhận dạng video có thể chậm.
- Ảnh cần đủ sáng, biển số không bị che và không quá mờ/nghiêng.
- Phân loại dự phòng theo hình dạng biển chỉ dùng khi ảnh quá cận, vì vậy nhân viên vẫn cần kiểm tra trước khi xác nhận.
- Bản triển khai thật cần JWT/API key, HTTPS, audit log và chính sách lưu ảnh.
