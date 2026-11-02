# Parking ANPR Demo

## Chức năng đã ghép

- Web Spring Boot lưu hộ gia đình, thành viên, ảnh khuôn mặt đăng ký, phương tiện, gói gửi xe, bảng giá, lượt xe và sơ đồ vị trí đỗ trong database.
- Form đăng ký cư dân cho phép tải ảnh hoặc chụp webcam; QR CCCD dùng để tự điền thông tin, không dùng ảnh CCCD để so khớp khuôn mặt.
- WinForms có hai nguồn `XE / BIỂN SỐ` và `NGƯỜI / KHUÔN MẶT`, hỗ trợ ảnh, video, camera, xử lý song song và hiển thị sơ đồ vị trí đỗ.
- Cư dân: chọn người được phép lái xe và so ảnh đăng ký với ảnh realtime. Người cùng hộ có thể lấy xe nếu đã được cấp quyền cho xe đó.
- Khách vãng lai: chụp một khuôn mặt lúc vào, lưu tạm theo lượt; lúc ra chụp lại và so với ảnh vào.
- Barrier có nút mở/đóng, trạng thái trực quan và tự mở 5 giây sau khi lượt vào/ra hợp lệ. Đây là mô phỏng phần mềm, chưa điều khiển barrier vật lý.

Các hạng mục cần làm tiếp được ghi tại [PROJECT_GAPS.md](PROJECT_GAPS.md).

## Python chạy được khi chuyển máy hoặc đổi thư mục

`scripts\run-anpr.cmd` không dùng đường dẫn Python tuyệt đối. Script tự tìm Python theo thứ tự: biến `PARKING_PYTHON`, Python Launcher `py`, rồi `python/python3` trong PATH. Môi trường `.venv` luôn được tạo trong `anpr-service` theo đường dẫn tương đối của project. Nếu `.venv` được chép từ máy khác và không còn hợp lệ, script giữ nó dưới tên `.venv.incompatible.<thời gian>` rồi tạo môi trường mới và cài `requirements.txt`.

Máy mới chỉ cần cài Python 3.10 trở lên và chọn **Add python.exe to PATH**. Nếu không muốn thêm PATH, có thể cấu hình trước khi chạy:

```powershell
$env:PARKING_PYTHON = 'E:\Python312\python.exe'
.\scripts\run-anpr.cmd
```

Hệ thống gồm ba ứng dụng:

- `spring-web`: Java Spring Boot + Thymeleaf + MySQL Aiven cho quản trị và REST API nghiệp vụ.
- `anpr-service`: Python FastAPI, YOLO chuyên phát hiện biển số, YOLO COCO phân loại xe và EasyOCR đọc ký tự.
- `desktop-winform`: C# WinForms cho trạm gác, tự điền biển số và loại xe từ ANPR.

## Kiến trúc và quyết định kỹ thuật

Spring Boot là backend nghiệp vụ trung tâm, FastAPI cung cấp năng lực AI độc lập và WinForms là client vận hành tại cổng. Bối cảnh, phương án thay thế và hệ quả của các quyết định quan trọng được lưu trong [Architecture Decision Records](docs/decisions/README.md).

Các ADR có trạng thái `Proposed` mô tả hướng thiết kế chưa được tích hợp hoàn chỉnh; không được xem là bằng chứng tính năng hiện đang hoạt động.

Hướng dẫn triển khai tài khoản database, bootstrap quản trị viên, audit retention và migration MySQL được ghi tại [Vận hành bảo mật Giai đoạn 1](docs/operations/security-phase1.md). Hướng dẫn JWT Desktop, RSA key, migration session và smoke test được ghi tại [Vận hành bảo mật Giai đoạn 2](docs/operations/security-phase2.md).

## Chạy hệ thống

Cách dễ nhất trên Windows là bấm đúp `scripts\run-all.cmd`. File này mở Web, ANPR và Desktop, sử dụng database chính Aiven `parking_anpr`.

Muốn chạy riêng từng phần, bấm đúp lần lượt:

1. `scripts\run-web.cmd`
2. `scripts\run-anpr.cmd`
3. `scripts\run-desktop.cmd`

Không bấm đúp file `.ps1`, vì Windows có thể đang liên kết loại file này với Notepad. Các launcher tự kiểm tra cổng để tránh mở trùng dịch vụ.

Hoặc chạy trong ba terminal PowerShell tại `D:\Code\KhoaLuan`:

```powershell
.\scripts\run-web.ps1
.\scripts\run-anpr.ps1
.\scripts\run-desktop.ps1
```

### Cấu hình database

Aiven `parking_anpr` là database chính cho `scripts\run-all.cmd` và `scripts\run-web.cmd`.

Sao chép `.env.local.example` thành `.env.local`, sau đó điền ba giá trị:

```text
PARKING_DB_URL=jdbc:mysql://<host>:<port>/parking_anpr?sslMode=REQUIRED&allowPublicKeyRetrieval=TRUE
PARKING_DB_USERNAME=<username>
PARKING_DB_PASSWORD=<password>
```

`.env.local` bị Git ignore. Biến môi trường hệ thống cùng tên được ưu tiên hơn file local. Không lưu credential trong `application.properties`.

Đường dự phòng `scripts\run-all-laragon.cmd` / `scripts\run-web-laragon.cmd` dùng MySQL local tại
`127.0.0.1:3306/parking_anpr`, mặc định user `root`. Launcher tự tạo database nếu tài
khoản có quyền và không đọc `.env.local` của Aiven. Nếu chưa đặt
`PARKING_LARAGON_PASSWORD`, launcher yêu cầu nhập mật khẩu ẩn; máy thật sự dùng mật
khẩu rỗng phải đặt `PARKING_LARAGON_ALLOW_EMPTY_PASSWORD=true`. Có thể đổi URL/user
bằng `PARKING_LARAGON_URL`, `PARKING_LARAGON_USERNAME`; không ghi mật khẩu vào repo.

Launcher tự tạo service token nội bộ 256-bit bị Git ignore để Spring gọi FastAPI. Có thể ghi đè bằng `PARKING_INTERNAL_SERVICE_TOKEN` trong môi trường triển khai. Chi tiết tại [Vận hành bảo mật Giai đoạn 3](docs/operations/security-phase3.md).

Thiết kế Gate Evidence, private storage, chống replay và xử lý ngoại lệ có kiểm soát được ghi tại [Vận hành bảo mật Giai đoạn 4](docs/operations/security-phase4.md). Nhân viên cổng đang trong ca có thể chỉnh kết quả AI sau khi có evidence; hệ thống lưu dữ liệu trước/sau và audit đầy đủ. RBAC động cho phép quản trị tạo vai trò, chọn quyền và tiền tố mã nhân viên; tài khoản mới bắt buộc có hồ sơ đầy đủ. Toàn bộ schema và hai ảnh resident PNG đã được migrate sang Aiven với backup trước thay đổi.

Hướng dẫn đóng gói, backup, monitoring và checklist nghiệm thu Giai đoạn 5 nằm tại [Đóng gói và nghiệm thu Giai đoạn 5](docs/operations/phase5-deployment-acceptance.md). Các lệnh dành cho người vận hành đều có file `.cmd` trong `scripts\operations`.

## Cách dùng Desktop

1. Chờ Web chạy ở cổng `8080`; launcher tự kết nối dịch vụ ANPR nội bộ ở cổng `8001`.
2. Mở Desktop và đăng nhập bằng tài khoản có quyền **Vận hành trạm gác** đã tạo trên Web.
3. Bấm **Kiểm tra**. Kết quả đúng là `Hệ thống đang hoạt động`.
4. Chọn tab **XE VÀO** hoặc **XE RA**.
5. Trong mỗi cột, chọn ảnh, video hoặc camera; nút **XỬ LÝ SONG SONG** điều phối đồng thời nguồn xe và khuôn mặt.
6. Hệ thống nhận dạng biển số, tự tra cứu gói, chụp/xác thực khuôn mặt và cấp ô đỗ phù hợp.
7. Kiểm tra dữ liệu được tự điền rồi xác nhận nghiệp vụ; không cần nhập mã thẻ.

Với ảnh quá cận chỉ thấy biển số, hệ thống dự phòng phân loại theo hình dạng biển. Với video, pipeline quét tối đa 12 frame, OCR/phân loại 3 frame tốt nhất và dùng frame tốt nhất làm ảnh chính.

## Web quản trị

Mở `http://localhost:8080`:

- Tài khoản: tài khoản quản trị đã tạo trong `system_accounts`
- Mật khẩu: mật khẩu sau khi đăng nhập lần đầu và đổi mật khẩu bắt buộc

Quy trình đăng ký hộ gia đình/gói tháng:

1. Vào **Hộ gia đình**, tạo căn hộ rồi khai báo đầy đủ từng thành viên: CCCD, ngày sinh, quan hệ, liên hệ, địa chỉ và đường dẫn ảnh đối chiếu.
2. Vào **Phương tiện**, chọn hộ gia đình, chủ xe đứng tên và tất cả thành viên được phép dùng chung xe; nhập biển số, đăng ký xe, hãng, mẫu, màu, số khung, số máy, số chỗ, phân khối và loại nhiên liệu.
3. Vào **Hạn mức**, đặt số xe hai bánh và ô tô mặc định cho mỗi hộ. Có thể ghi đè hạn mức riêng tại màn hình **Hộ gia đình**.
4. Vào **Bảng giá**, điều chỉnh giá ban ngày, ban đêm, qua đêm, gói 30 ngày và cách tính theo khung giờ.
5. Vào **Thẻ & gói**, chọn xe, chọn **Gói 30 ngày** và ngày bắt đầu. Biển số chuẩn hóa tự động là mã nhận diện, không nhập mã thẻ riêng.
6. Hệ thống ghi nhận tiền gói khi kích hoạt/gia hạn. Trong thời hạn, phí mỗi lượt ra là `0 đ`.
7. Gói hết hạn thì hệ thống cảnh báo và áp dụng giá khách vãng lai. Khách cũng dùng biển số làm mã nhận diện lượt xe.

Giá mặc định lấy từ tài liệu khảo sát và quản trị viên có thể thay đổi:

- Gói 30 ngày: xe đạp/xe đạp điện `100.000 đ`; xe máy/xe máy điện `150.000 đ`; mô tô phân khối lớn `300.000 đ`; ô tô 4-5 chỗ `1.500.000 đ`; ô tô 6-7 chỗ `1.700.000 đ`; ô tô 8-9 chỗ `1.800.000 đ`.
- Khách vãng lai: xe máy `5.000 đ` ban ngày, `8.000 đ` ban đêm, `10.000 đ` mỗi chu kỳ 24 giờ có qua đêm; xe đạp `2.000/3.000/5.000 đ`; ô tô `40.000 đ` cho 4 giờ đầu, thêm `20.000 đ` cho mỗi 2 giờ hoặc phần lẻ, tối thiểu `100.000 đ` cho mỗi đêm đi qua 00:00.

Dữ liệu mẫu:

- Xe máy: `59A112345`, mã nhận diện `59A112345`
- Ô tô: `51H88888`, mã nhận diện `51H88888`

## API

Spring Boot:

- `GET /api/parking/health`
- `POST /api/parking/entry`
- `POST /api/parking/exit-preview`
- `POST /api/parking/exit-confirm`
- `GET /api/ai/health`
- `POST /api/ai/recognize/image`
- `POST /api/ai/recognize/video`
- `POST /api/ai/recognize/camera`
- `POST /api/ai/face/verify-image`
- `POST /api/ai/face/verify-video`
- `POST /api/ai/face/verify-camera`
- `POST /api/ai/face/capture-camera`

ANPR nội bộ (Desktop không gọi trực tiếp):

- `GET http://localhost:8001/health`
- `POST http://localhost:8001/recognize/image`
- `POST http://localhost:8001/recognize/video`
- `POST http://localhost:8001/recognize/camera`
- `POST http://localhost:8001/face/verify-image`
- `POST http://localhost:8001/face/verify-video`
- `POST http://localhost:8001/face/verify-camera`
- `POST http://localhost:8001/face/capture-camera`
- Swagger: `http://localhost:8001/docs`

Ngoại trừ `/health`, endpoint FastAPI yêu cầu `X-Parking-Service-Token`. Endpoint `/api/ai/**` của Spring yêu cầu Desktop JWT.

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
- Desktop đã có đăng nhập JWT RS256, refresh rotation và thu hồi phiên; bật bằng `PARKING_SECURITY_JWT_ENABLED=true`.
- Desktop chỉ kết nối Spring; Spring proxy yêu cầu AI sang FastAPI bằng service token nội bộ.
- Trang quản lý tài khoản hiển thị phiên Web/Desktop đang hoạt động, cho phép thu hồi từng phiên và phân ca cho `GATE_STAFF`.
- Danh sách phiên và ca trên Web tự cập nhật; Desktop tự quay về đăng nhập khi phiên bị quản trị viên kết thúc.
- Các xác nhận quan trọng dùng hộp thoại Parking ANPR với nội dung hướng người dùng thay cho hộp thoại mặc định của trình duyệt.
- Bản triển khai thật cần HTTPS, bảo vệ private key, audit log và chính sách lưu ảnh.
