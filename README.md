# Parking ANPR Demo

Tài liệu canonical: [docs/README.md](docs/README.md). Mô tả implementation dưới đây đối chiếu tĩnh source `main abd6183` ngày 2026-10-24, không dùng backups làm source. Không chạy startup/build/app suites/DB/camera trong đợt docs; kết quả Security + WIP là lịch sử, không chứng nhận main hoặc production readiness.

## Chức năng đã ghép

- Web Spring Boot quản lý hộ gia đình, thành viên, ảnh khuôn mặt đăng ký, phương tiện, thẻ/gói tháng, bảng giá, lượt xe và vị trí đỗ. Source khởi tạo 60 ô khi bảng ô trống (khác mô tả cũ 50 ô); ảnh ở private filesystem, metadata ở database, không chứng minh dữ liệu runtime đã được tạo.
- Form đăng ký cư dân cho phép tải ảnh hoặc chụp webcam; QR CCCD dùng để tự điền thông tin, không dùng ảnh CCCD để so khớp khuôn mặt.
- WinForms nhận diện biển số/loại xe, hiển thị sơ đồ vị trí đỗ ngay trên form và có cửa sổ sơ đồ lớn.
- Cư dân: UI hỗ trợ chọn người và so ảnh đăng ký với realtime. Người lấy xe phải có quyền theo từng xe; chưa chứng minh backend bắt buộc kiểm face/quyền ở mọi luồng xe ra.
- Khách vãng lai: hỗ trợ chụp ảnh vào và đối chiếu lúc ra; backend kiểm face có điều kiện khi có entry-face path, trừ override. Evidence mode lấy kết quả do Spring lưu; nhánh legacy khi `PARKING_EVIDENCE_REQUIRED=false` và không có IDs vẫn tin client face flags, không chứng minh xác thực thực sự.
- Barrier có nút mở/đóng, trạng thái trực quan, mở sau kết quả nghiệp vụ và tự đóng mặc định sau 5 giây (cấu hình 1–120 giây). `IBarrierController` hiện chỉ có simulator, chưa điều khiển barrier vật lý.
- Thu phí lượt/gói có phương thức, biên nhận và đối soát; entry/exit-confirm có `operationId` và Desktop outbox khi lỗi kết nối. Source này không chứng minh tiền thực nhận, exactly-once hoặc offline admission/phục hồi live an toàn.

Các hạng mục cần làm tiếp được ghi tại [trạng thái implementation](docs/implementation-status.md).

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

Desktop gửi nghiệp vụ và yêu cầu xử lý ảnh/video/khuôn mặt qua Spring; Spring gọi FastAPI bằng service token nội bộ. Camera xử lý khuôn mặt nằm trên máy ANPR. Xem [kiến trúc](docs/architecture/system.md).

## Chạy hệ thống

Trước khi chạy cần Windows, JDK 21, Maven, global .NET SDK hỗ trợ .NET 8, Python 3.10+ và datasource được xác nhận. Source dùng `spring-web/src/main/resources/application.properties` (không `application.yml`), MySQL/Hibernate `ddl-auto=update`, không Flyway. Web launcher lấy `PARKING_DB_URL`/`PARKING_DB_USERNAME`/`PARKING_DB_PASSWORD` từ process hoặc fallback `.env.local`; yêu cầu database `parking_anpr`, target Aiven theo launcher nhưng runtime chưa xác minh. Startup có thể ghi schema/dữ liệu, cần xác nhận DB và backup trước.

Web dùng tài khoản database; Desktop JWT mặc định bật qua `PARKING_SECURITY_JWT_ENABLED:true`, cần khóa RS256 bên ngoài. Không tắt JWT để chữa login: compatibility mode cho phép parking/AI ở filter. Spring/FastAPI dùng cùng `PARKING_INTERNAL_SERVICE_TOKEN` trong header `X-Parking-Service-Token`, không internal Bearer; không lưu token vào source hoặc Desktop. Làm theo [setup/troubleshooting](docs/operations/setup-and-troubleshooting.md) và [security](docs/security/overview.md).

`scripts/run-all.cmd` mở Web, ANPR và Desktop bằng các khoảng đợi cố định, không bảo đảm dịch vụ đã sẵn sàng.

Muốn chạy riêng từng phần, bấm đúp lần lượt:

1. `scripts/run-web.cmd`
2. `scripts/run-anpr.cmd`
3. `scripts/run-desktop.cmd`

Không bấm đúp file `.ps1`, vì Windows có thể đang liên kết loại file này với Notepad. Trên main, Web health có `version` và ANPR health có `service` khớp kiểm tra launcher; thiếu identity là khác biệt lịch sử nhánh Security. Health không chứng minh đúng datasource/JWT/model/camera; xem [hạn chế launcher](docs/operations/setup-and-troubleshooting.md#spring-backend-và-database), không tự dừng process chỉ vì launcher báo port conflict.

Hoặc chạy trong ba terminal PowerShell tại thư mục gốc project:

```powershell
.\scripts\run-web.ps1
.\scripts\run-anpr.ps1
.\scripts\run-desktop.ps1
```

### Launcher mang tên Laragon

1. Xác nhận datasource Aiven/database `parking_anpr`, biến process hoặc `.env.local`, DB access/TLS và backup qua quản trị.
2. Xác nhận có được phép đóng Web cũ trước khi cân nhắc `scripts/run-all-laragon.cmd`: script có nhánh force-stop Web.
3. Chỉ chạy launcher trong nhiệm vụ vận hành được phép; không dùng tên Laragon làm lý do chuyển sang MySQL local.

Source main giữ tên để tương thích nhưng không còn probe local 3306 hoặc prompt mật khẩu `root`; `run-all-laragon` dùng launcher Web chính. `run-web-laragon.ps1` riêng không nạp `.env.local`/chuẩn bị token-JWT như launcher chính. Nhãn Aiven hoặc health `database:MySQL` không chứng minh host/schema runtime đúng, tự tạo database hoặc chuyển dữ liệu từ H2. Xem [setup và troubleshooting](docs/operations/setup-and-troubleshooting.md) trước khi chạy.

## Cách dùng Desktop

1. Chờ Web chạy ở cổng `8080` và ANPR chạy ở cổng `8001`.
2. Mở Desktop, đăng nhập bằng tài khoản được cấp rồi bấm **Kiểm tra**. Nút này có thể gửi lại outbox, không chỉ kiểm health; xác nhận hàng đợi/môi trường trước. Health UP không chứng minh đăng nhập hoặc API nghiệp vụ đã sẵn sàng.
3. Chọn tab **XE VÀO** hoặc **XE RA**.
4. Bấm **Nhận dạng ảnh** hoặc **Nhận dạng video**.
5. YOLO khoanh biển số, EasyOCR đọc ký tự và YOLO COCO xác định ô tô/xe máy.
6. Kiểm tra dữ liệu được tự điền rồi xác nhận nghiệp vụ. Sửa AI cần quyền/ca/lý do và evidence; confirm xe ra phí dương cần phương thức thu, chuyển khoản/POS cần mã tham chiếu. Xem biên nhận và tra lịch sử/outbox trước retry sau timeout.

Với ảnh quá cận chỉ thấy biển số, hệ thống dự phòng phân loại theo hình dạng biển. Với video, dịch vụ mặc định lấy mẫu tối đa 12 frame và chọn kết quả tốt nhất.

## Web quản trị

Mở `http://localhost:8080` và dùng tài khoản DB do quản trị cấp. Tài khoản có yêu cầu đổi mật khẩu phải hoàn thành bước này trước khi dùng nghiệp vụ. Không có tài khoản demo mặc định; xem [authentication](docs/api/authentication.md).

Quy trình đăng ký hộ gia đình/gói tháng:

1. Vào **Hộ gia đình**, tạo căn hộ rồi khai báo đầy đủ từng thành viên: CCCD, ngày sinh, quan hệ, liên hệ, địa chỉ và đường dẫn ảnh đối chiếu.
2. Vào **Phương tiện**, chọn hộ gia đình, chủ xe đứng tên và tất cả thành viên được phép dùng chung xe; nhập biển số, đăng ký xe, hãng, mẫu, màu, số khung, số máy, số chỗ, phân khối và loại nhiên liệu.
3. Vào **Hạn mức**, đặt số xe hai bánh và ô tô mặc định cho mỗi hộ. Có thể ghi đè hạn mức riêng tại màn hình **Hộ gia đình**.
4. Vào **Bảng giá**, điều chỉnh giá ban ngày, ban đêm, qua đêm, gói 30 ngày và cách tính theo khung giờ.
5. Vào **Thẻ & gói**, chọn xe; mã thẻ được sinh từ biển số chuẩn hóa, chọn **Gói 30 ngày** và ngày bắt đầu.
6. Kích hoạt/gia hạn gói tháng tạo SubscriptionPayment và PaymentTransaction/biên nhận, cần phương thức thu (chuyển khoản/POS cần mã tham chiếu). `/payments` có đối soát; records không chứng minh tiền thực nhận hoặc provider settlement. Gói tháng hợp lệ được code tính phí ra `0 đ`; cần kiểm tra preview thực tế.
7. Gói hết hạn hoặc thẻ không khớp/còn thiếu có thể cảnh báo và áp dụng giá lượt; main tra card theo plate, không theo mã thẻ nhập ở entry. Warning vẫn có thể tạo OPEN, không chứng minh policy từ chối đã được thực thi.

Các mức giá được ghi nhận dưới đây có thể được quản trị viên thay đổi; provenance và tính đại diện của khảo sát chưa xác minh. Kiểm tra bảng giá hiện tại, không coi đây là kết quả khảo sát đã được công nhận:

- Gói 30 ngày: xe đạp/xe đạp điện `100.000 đ`; xe máy/xe máy điện `150.000 đ`; mô tô phân khối lớn `300.000 đ`; ô tô 4-5 chỗ `1.500.000 đ`; ô tô 6-7 chỗ `1.700.000 đ`; ô tô 8-9 chỗ `1.800.000 đ`.
- Khách vãng lai: xe máy `5.000 đ` ban ngày, `8.000 đ` ban đêm, `10.000 đ` mỗi chu kỳ 24 giờ có qua đêm; xe đạp `2.000/3.000/5.000 đ`; ô tô `40.000 đ` cho 4 giờ đầu, thêm `20.000 đ` cho mỗi 2 giờ hoặc phần lẻ, tối thiểu `100.000 đ` cho mỗi đêm đi qua 00:00.

Dữ liệu mẫu trong source khởi tạo có điều kiện, chưa xác nhận DB runtime (không tự seed/reset để thử):

- Xe máy: `59A112345`, mã thẻ theo plate `59A112345`
- Ô tô: `51H88888`, mã thẻ theo plate `51H88888`

## API

Spring Boot:

- `GET /api/parking/health`
- `POST /api/parking/entry`
- `POST /api/parking/exit-preview`
- `POST /api/parking/exit-confirm`
- AI health/processing qua Spring: `/api/ai/**` (Desktop JWT khi bật); không `/api/parking/anpr/**`. Payload, payment và `operationId` tại [API contracts](docs/api/contracts.md).

ANPR nội bộ (Desktop không gọi trực tiếp các processing routes):

- `GET http://localhost:8001/health`
- `POST http://localhost:8001/recognize/image`
- `POST http://localhost:8001/recognize/video`
- Swagger: `http://localhost:8001/docs` (cũng cần service-token header, không public browser page)

Chỉ health/favicon public; processing và Swagger cần `X-Parking-Service-Token` hợp lệ. Địa chỉ bên trên không phải API công khai cho Desktop client.

## Kiểm thử và CI

```powershell
.\scripts\test\run-tests.ps1 -Suite all
dotnet build desktop-winform\ParkingGateDesktop.csproj --configuration Release
```

Đây là lệnh tham khảo, **chưa chạy application suites trên main trong đợt này**. Chuẩn bị JDK21, global .NET SDK và ANPR venv trước; runner không tự cài dependencies. `anpr-service/requirements-test.txt` bổ sung runtime requirements cùng pytest/httpx được pin; chưa cài hoặc chạy ANPR suite để chứng nhận môi trường đó.

[Workspace CI](.github/workflows/workspace.yml) tự kiểm docs/paths/whitespace/syntax/tooling trên PR và push main, không build/test ứng dụng. [Verify](.github/workflows/verify.yml) giữ application matrix nhưng chỉ kích hoạt thủ công theo quyết định được duyệt; đợt này không dispatch. Tests H2/fakes không chứng minh MySQL/camera/live E2E; Desktop main là xUnit outbox tests, không dùng số12 HTTP doubles của nhánh cũ làm main PASS. Xem [tests/README](tests/README.md) và [testing](docs/testing/strategy.md).

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
- Source đã có Web session, Desktop JWT, audit và kiểm soát evidence; cấu hình triển khai, HTTPS, MySQL, camera/model và chính sách vận hành thực tế vẫn cần kiểm chứng riêng.
