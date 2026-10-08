# Cài đặt, khởi động và xử lý sự cố

[Hướng dẫn sử dụng](user-guide.md) · [Kiến trúc](../architecture/system.md) · [Database](../architecture/database.md) · [Contracts](../api/contracts.md) · [Security](../security/overview.md)

## Phạm vi và trạng thái kiểm chứng

Hướng dẫn dựa trên đọc tĩnh launcher/config/source và [README](../../README.md), **không phải kết quả chạy thành công**. Không chạy lệnh khởi động/build, cài dependencies hoặc kiểm thử trong R08; chỉ đọc và kiểm tra tài liệu tĩnh. Các lệnh bên dưới dành cho lần vận hành được cho phép riêng. Không bảo đảm integrated deployment hoặc production readiness; không cung cấp mật khẩu/secret.

Desktop gọi Spring cho nghiệp vụ và ANPR cho AI; ANPR tải ảnh tham chiếu từ Spring và dùng camera trên máy chạy ANPR. Demo ba ảnh là app riêng, không phải dependency API đã được chứng minh của gate.

## Prerequisites và working directory

| Thành phần | Điều kiện theo source / cách chuẩn bị |
| --- | --- |
| Windows/PowerShell | CMD launchers gọi `powershell.exe`, script dùng `Get-NetTCPConnection`; Desktop target Windows. Mở terminal tại **root repository hiện tại**, không copy đường dẫn máy tác giả. |
| Spring | JDK **21** theo [pom](../../spring-web/pom.xml):18. Launcher ưu tiên JDK ở một đường dẫn cài đặt cụ thể nếu tồn tại; nếu không, cần Java/Maven environment đúng trên máy. Maven ưu tiên `tools/apache-maven-3.9.11/bin/mvn.cmd`, fallback `mvn` trong PATH. |
| Database | Cần datasource/driver/schema permissions được quản trị xác nhận trước startup; xem phần database bên dưới. Không mặc nhiên dùng dữ liệu thật cho demo. |
| Desktop | **Global .NET SDK hỗ trợ .NET 8**, Windows; [project](../../desktop-winform/ParkingGateDesktop.csproj) target `net8.0-windows`, WinForms. Launcher không chấp nhận local `tools/dotnet` thay global SDK. |
| ANPR | Python **3.10+**; network/package access cho lần cài dependencies/model đầu; quyền ghi venv/model cache. YOLO plate/vehicle weights, EasyOCR và OpenCV cần sẵn sàng. GPU không bắt buộc theo default source. |
| Face/camera | YuNet/SFace weights; camera có quyền truy cập và không bị app khác giữ. Gate camera dùng OpenCV DirectShow trên máy ANPR, không tự là camera máy Desktop nếu triển khai khác máy. |
| Media | Spring upload directory phải writable/persistent theo nhu cầu; default relative `uploads` dựa working directory. Database backup không tự bao gồm media; retention/restore chưa verified. |

CMD wrappers Web/ANPR/Desktop ở root đổi working directory về root và gọi PS1 với `-NoProfile -ExecutionPolicy Bypass`; demo wrapper gọi script theo đường dẫn riêng, không đổi directory ở wrapper. **Không bấm đúp `.ps1`** vì có thể mở bằng editor. Chỉ chạy scripts đã được tin cậy; launcher có thể download/install/build hoặc dừng process như mô tả bên dưới.

## Thứ tự khởi động

1. Xác nhận datasource, DB server/access và media/model directories. Nếu chọn local MySQL, khởi động database trước Spring.
2. Chạy Spring, đọc log startup/datasource và kiểm tra `http://localhost:8080/api/parking/health`; đối chiếu tên database thực tế. Health metadata không thay persistence/integration test.
3. Chạy ANPR, chờ initialization/model warm-up, kiểm `http://localhost:8001/health` và `/docs`. File/cache readiness không chứng minh accuracy/liveness/camera hoàn chỉnh.
4. Chạy Desktop, đặt `Spring API` và `ANPR API`, bấm **Kiểm tra**. Chỉ sau khi xác nhận services và datasource mới thao tác dữ liệu thử.
5. Demo ba ảnh khởi động riêng nếu cần; không đưa vào thứ tự bắt buộc cho gate.

### Launcher tại root (lệnh ghi nhận, chưa thực thi)

```cmd
scripts\run-web.cmd
scripts\run-anpr.cmd
scripts\run-desktop.cmd
```

Chạy từng lệnh trong terminal riêng để giữ log. [run-all.cmd](../../scripts/run-all.cmd) mở Web, đợi **5 giây**, mở ANPR, đợi **8 giây**, rồi Desktop. Đây là **fixed delays**, không readiness guarantee; model/download hoặc Maven startup có thể lâu hơn. Chưa ready thì chờ log và bấm Kiểm tra lại, không mở thêm process trùng.

## Spring backend và database

[run-web.ps1](../../scripts/run-web.ps1):3–32 chọn Maven/JDK, kiểm health version `4.0-face-slots`, tái dùng Web cùng version, báo lỗi bản Web cũ hoặc port8080 đang bị chiếm, rồi gọi `clean spring-boot:run` từ `spring-web`.

Manual tương đương khi bundled Maven có mặt (chưa chạy):

```cmd
cd spring-web
..\tools\apache-maven-3.9.11\bin\mvn.cmd clean spring-boot:run
```

Nếu dùng Maven PATH, thay executable bằng `mvn`. Giữ working directory `spring-web` vì relative media path phụ thuộc nơi chạy. Không khẳng định auto-reload: inspected pom không cung cấp bằng chứng devtools/reload và thay Java code có thể cần restart.

### H2/MySQL: khác biệt không được che giấu

- README mô tả H2 mặc định, MySQL Laragon tự tạo `parking_anpr`; đây là **documented claims**, không startup verified.
- Focused listing không tìm thấy `application*` dưới `spring-web/src/main/resources`. Production datasource/profile/DDL initialization vẫn chưa xác nhận trong evidence; không suy ra không có config ngoài thư mục này.
- `pom.xml` có MySQL connector **runtime**, H2 **test scope**. Vì vậy không được hứa `scripts/run-web.cmd` sẽ boot bằng H2 ở runtime. H2 memory/create-drop của integration test không là cấu hình development/production.
- Trước chạy, quản trị phải xác nhận nguồn cấu hình Spring được cung cấp, JDBC URL/driver, tài khoản qua kênh an toàn, schema và DDL policy. Không copy test `create-drop` tới DB thật; không tự xóa/reset database để sửa lỗi.
- Nếu datasource chưa rõ hoặc Web báo driver/URL/schema lỗi, dừng ở bước Spring và lấy quyết định cấu hình từ quản trị; không có command trong tài liệu này được xác nhận tự thiết lập DB.

### Laragon launchers và hạn chế thực tế

[run-all-laragon.cmd](../../scripts/run-all-laragon.cmd) gọi [run-all-laragon.ps1](../../scripts/run-all-laragon.ps1). Script đòi listener local **3306**, yêu cầu mở Laragon/Start All nếu chưa có; nhận `PARKING_DB_PASSWORD` hoặc prompt kín; có thể dừng Web đã nhận diện ở8080; mở `scripts/run-web-laragon.ps1`, đợi tối đa60 vòng health/database chứa MySQL, rồi ANPR và Desktop.

**Không coi tên launcher là cấu hình DB:** [run-web-laragon.ps1](../../scripts/run-web-laragon.ps1) in nhãn cloud nhưng chỉ gọi Maven, không set datasource. Trong `scripts/run-all-laragon.ps1`, biến được dùng như đường dẫn MySQL client lại chứa mô tả connection, và `Test-MySqlLogin` trả true khi không có file client. Do đó password probe có thể bị bỏ qua; port3306/label/password prompt không chứng minh Spring dùng Laragon hoặc login thành công. Nhãn cloud không được dùng làm connection instruction; không chép host/user/secret vào guide.

Không chạy launcher Laragon nếu chưa rõ datasource đang dùng. Trước switch môi trường, giữ backup theo quy trình quản trị và đóng Web cũ có kiểm soát; script có nhánh `Stop-Process -Force`, không phải safe migration. README database auto-creation và MySQL persistence cần runtime evidence riêng.

## ANPR startup và configuration

[run-anpr.ps1](../../scripts/run-anpr.ps1):12–111 ưu tiên `PARKING_PYTHON`, rồi `py` (3.12/3.11/3.10/3), rồi Python PATH; probe version>=3.10. Nó kiểm venv, **đổi tên** venv incompatible để giữ lại, tạo venv mới, upgrade pip và install requirements nếu hash thay đổi. Đây là side effects của lần chạy sau, không được thực hiện trong R08.

| Biến / nguồn | Ý nghĩa, default source (không secret) |
| --- | --- |
| `PARKING_PYTHON` | Interpreter base do người vận hành chọn; không hardcode đường dẫn máy khác. |
| `EASYOCR_MODULE_PATH`, `YOLO_CONFIG_DIR`, `PYTHONUTF8` | Launcher set local `.EasyOCR`, `.ultralytics` dưới service và UTF8; cần quyền ghi/cache/network phù hợp. |
| `ANPR_PLATE_MODEL`, `ANPR_VEHICLE_MODEL` | [recognizer](../../anpr-service/app/recognizer.py):58–72 mặc định service `models/license_plate_detector.pt` / `models/vehicle_detector.pt`. File phải đúng model và readable. |
| `ANPR_GPU` | EasyOCR GPU bật khi giá trị là `true`; default `false`. Không tự bảo đảm CUDA/GPU support. |
| `ANPR_PLATE_CONFIDENCE`, `ANPR_VEHICLE_CONFIDENCE` | Default0.20 /0.25; không phải quality acceptance hoặc OCR UI threshold. |
| `FACE_MATCH_THRESHOLD`, `FACE_REVIEW_THRESHOLD`, `FACE_DETECTION_THRESHOLD` | [face engine](../../anpr-service/app/face_engine.py):14–16 default0.363 /0.300 /0.60. Cần site calibration; hạ threshold không phải cách xử lý authorization hay liveness. |
| `PARKING_DB_PASSWORD` | Laragon coordinator đọc/prompt và truyền process environment; chưa verified Spring mapping. Không ghi giá trị vào log/docs/shell history. |
| `parking.upload-dir` | Spring media property, default `uploads`; cần xác nhận config/working directory. Xem [database/media](../architecture/database.md). |

Sau khi venv/dependencies đã được chuẩn bị, manual ANPR tương đương (chưa chạy):

```cmd
cd anpr-service
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Launcher bind `0.0.0.0`, không chỉ loopback; mạng có thể truy cập tùy firewall. Không expose biometric/API service ra mạng không tin cậy. Port8001 đã có process: script chờ health, tái dùng ANPR đúng service hoặc báo process/PID conflict. Unexpected native exit sẽ retry sau2 giây; restart loop không chứng minh lỗi được chữa. Không có `--reload`; thay source cần restart có kiểm soát.

## Desktop

[run-desktop.ps1](../../scripts/run-desktop.ps1):3–22 tìm global `dotnet.exe`, set CLI home dưới repo, build Release và mở EXE chờ kết thúc. Lệnh build thủ công tương đương, **chưa chạy trong R08**:

```cmd
dotnet build desktop-winform\ParkingGateDesktop.csproj --configuration Release
```

Muốn vừa build vừa mở, dùng `scripts/run-desktop.cmd`. Thay Desktop source phải rebuild; không dùng EXE cũ để kết luận API incompatibility đã sửa. Defaults UI là localhost8080/8001; thay host cho cả hai dịch vụ nếu cần, nhưng remote deployment chưa verified. Face verify-camera chỉ chấp nhận prefix URL local theo handler hiện tại; thay Web host có thể khiến HTTP400. Camera capture vẫn diễn ra trên máy ANPR.

## Demo face-verification độc lập

[run-demo.cmd](../../face-verification-demo/run-demo.cmd) gọi [run-demo.ps1](../../face-verification-demo/run-demo.ps1), tải YuNet/SFace qua `download-models.ps1` nếu thiếu, bind loopback port8002 và tìm free port tới8012; đọc URL được in thay vì luôn assume8002. `FACE_DEMO_NO_BROWSER=1` ngăn auto browser, `FACE_DEMO_NONINTERACTIVE=1` ngăn prompt duplicate-launcher.

**Path limitation:** script resolve Python từ `parent-of-repository/anpr-service/.venv`, không từ ANPR directory bên trong repo. Nó có thể báo thiếu môi trường dù `scripts/run-anpr.cmd` đã tạo venv đúng trong repo. Không sửa script hoặc tạo venv giả để vượt lỗi. Manual alternative từ root, chỉ khi repo ANPR venv/deps và demo models đã chuẩn bị (chưa thực thi):

```cmd
cd face-verification-demo
..\anpr-service\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8002
```

Nếu8002 đã dùng, operator chọn port trống và dùng URL tương ứng. Demo API `/api/compare` ba ảnh không được chứng minh gate caller; shared model fallback không là integrated enrollment hoặc liveness guarantee.

## Xử lý sự cố an toàn

| Triệu chứng | Kiểm tra / hành động supported hoặc suy luận vận hành, không claim đã sửa |
| --- | --- |
| Java/Maven không tìm thấy / build Web lỗi | Kiểm JDK21, Maven PATH/bundled path và console build log; launcher chỉ override Java ở path cụ thể. Không suy ra DB lỗi từ compilation failure. |
| Port8080/8001 bị chiếm | Đối chiếu health/service/version và process đang giữ port. Đóng đúng cửa sổ dịch vụ nếu được phép; không kill process không rõ. Web chuẩn có thể tái dùng version đúng, Laragon có nhánh force-stop. |
| Datasource/driver/access/schema lỗi | Xác nhận active config, runtime driver, endpoint và account qua quản trị; phân biệt local3306 với datasource khác. Không tin label hoặc login probe đã skip; không đổi DDL/delete DB tự động. |
| Python/venv/pip lỗi | Đặt `PARKING_PYTHON` đúng interpreter>=3.10 hoặc PATH rồi mở terminal mới; đọc pip/network/package log. Launcher giữ incompatible venv và tạo mới; không cần hướng dẫn xóa venv rộng. |
| Model missing/AI503/native restart loop | Xác nhận model paths, deps, file permissions và runtime compatibility; xem ANPR terminal. Model/cache files chưa proof accuracy; retry loop liên tục phải báo quản trị, không nâng timeout/hạ thresholds tùy tiện. |
| Camera không mở/ảnh không đạt422 | Kiểm camera trên máy ANPR, quyền và app giữ camera; đủ sáng, đúng một mặt, tránh che/mờ. Browser webcam dùng browser permission riêng. Camera index/DirectShow và remote camera chưa verified. |
| URL reference rejected400 hoặc fetch502 | Kiểm `Spring API`, ảnh registration/guest path và endpoint reachable từ ANPR; prefix localhost/127.0.0.1 hiện giới hạn remote URLs. Không mở rộng URL allowlist hoặc public media permissions như một “quick fix”. |
| Desktop HTML/empty/invalid JSON/404 | [ApiResponseReader](../../desktop-winform/ApiResponseReader.cs):8–74 nêu sai URL/version/redirect login. Kiểm base URLs/routes/server log; restart đúng stack đã xác nhận DB, không mặc nhiên dùng Laragon chỉ vì thông báo gợi ý. |
| HTTP400 nghiệp vụ | Đọc message: plate/card/session/rights; chỉnh dữ liệu hoặc chuyển quản trị. Không dùng manual checkbox để che card/authorization lỗi. |
| HTTP401/403/500/503 | Kiểm session/access hoặc server/model readiness và log; thông báo chung không chứng minh actual role enforcement. Không chép payload biometric/secret vào báo lỗi. |
| Timeout sau entry/exit/payment | Tra lookup/history/session trước gửi lại: server có thể đã commit. Idempotency/recovery chưa verified; không tự retry nhiều lần hoặc mở barrier để “đồng bộ”. |
| Map trống/stale | Kiểm service, filters, bấm Làm mới ở sơ đồ lớn; MainForm refresh có catch bỏ qua lỗi nên empty map không proof DB trống. |
| Nhận dạng sai/chậm | Ảnh rõ/đủ sáng, kiểm plate manual theo evidence; CPU/video có thể chậm. UI warning thresholds khác model thresholds; chưa có SLA/accuracy result. Không suy từ AI type ra quyền/charging. |

Không có verified backup/restore, offline queue/resync, production datasource/TLS/retention/security deployment trong slice này. Xem [security](../security/overview.md), [NV07](../requirements/nv07-exceptions-recovery.md) và [testing strategy](../testing/strategy.md) trước đưa hệ thống vào vận hành thật.
