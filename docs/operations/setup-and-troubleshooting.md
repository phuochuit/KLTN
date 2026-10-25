# Cài đặt, khởi động và xử lý sự cố

[Hướng dẫn sử dụng](user-guide.md) · [Kiến trúc](../architecture/system.md) · [Database](../architecture/database.md) · [Contracts](../api/contracts.md) · [Security](../security/overview.md)

## Phạm vi và trạng thái kiểm chứng

Hướng dẫn đối chiếu tĩnh launcher/config/source của `main` tại `abd6183` ngày **2026-10-24**, **không phải kết quả chạy thành công**. Đợt đồng bộ tài liệu không chạy khởi động/build, cài dependencies, app suites, DB hoặc camera. Các lệnh bên dưới dành cho lần vận hành được cho phép riêng. Không bảo đảm integrated deployment hoặc production readiness; không cung cấp mật khẩu/secret. Source backup dưới `.artifacts/` không được dùng làm implementation hiện hành.

Desktop gọi Spring cho nghiệp vụ và các yêu cầu image/video/face/camera; Spring đọc ảnh tham chiếu từ private storage và gọi FastAPI qua header `X-Parking-Service-Token`. Camera vẫn mở trên máy ANPR (USB hoặc nguồn mạng được cấu hình). Desktop hiện kiểm AI health qua Spring `/api/ai/health`, không có ô ANPR URL riêng. Demo ba ảnh là app riêng, không phải dependency API của gate.

## Prerequisites và working directory

| Thành phần | Điều kiện theo source / cách chuẩn bị |
| --- | --- |
| Windows/PowerShell | CMD launchers gọi `powershell.exe`, script dùng `Get-NetTCPConnection`; Desktop target Windows. Mở terminal tại **root repository hiện tại**, không copy đường dẫn máy tác giả. |
| Spring | JDK **21** theo [pom](../../spring-web/pom.xml). Launcher ưu tiên JDK ở một đường dẫn cài đặt cụ thể nếu tồn tại; nếu không, cần Java/Maven environment đúng trên máy. Maven ưu tiên `.cache/toolchains/apache-maven-3.9.11/bin/mvn.cmd`, fallback `mvn.cmd` trong PATH; thiếu cả hai thì dừng với hướng dẫn, không tự tải. |
| Database | Cần datasource/driver/schema permissions được quản trị xác nhận trước startup; xem phần database bên dưới. Không mặc nhiên dùng dữ liệu thật cho demo. |
| Desktop | **Global .NET SDK hỗ trợ .NET 8**, Windows; [project](../../desktop-winform/ParkingGateDesktop.csproj) target `net8.0-windows`, WinForms. Launcher không chấp nhận local `tools/dotnet` thay global SDK. |
| ANPR | Python **3.10+**; network/package access cho lần cài dependencies/model đầu; quyền ghi venv/model cache. YOLO plate/vehicle weights, EasyOCR và OpenCV cần sẵn sàng. GPU không bắt buộc theo default source. |
| Face/camera | YuNet/SFace weights; camera có quyền truy cập và không bị app khác giữ. OpenCV dùng DirectShow cho chỉ số USB và VideoCapture nguồn RTSP/HTTP(S) cho camera mạng trên máy ANPR; không tự là camera máy Desktop nếu triển khai khác máy. |
| Media | `PARKING_PRIVATE_STORAGE_PATH` mặc định relative `private-data`: ảnh cư dân dưới `residents/`, Gate Evidence dưới `evidence/`. `PARKING_UPLOAD_DIR` mặc định `uploads` chỉ phục vụ đọc ảnh legacy. Paths dựa working directory và phải writable/persistent; database backup không tự bao gồm media, deployed retention/restore chưa verified. |

CMD wrappers Web/ANPR/Desktop ở root đổi working directory về root và gọi PS1 với `-NoProfile -ExecutionPolicy Bypass`; demo wrapper gọi script theo đường dẫn riêng, không đổi directory ở wrapper. **Không bấm đúp `.ps1`** vì có thể mở bằng editor. Chỉ chạy scripts đã được tin cậy; launcher có thể download/install/build hoặc dừng process như mô tả bên dưới.

## Thứ tự khởi động

1. Xác nhận datasource Aiven được cấp, DB access/TLS và media/model directories; launcher Web chính yêu cầu URL nhắm database `parking_anpr`. Không dùng DB đang vận hành làm môi trường thử.
2. Chuẩn bị tài khoản và external JWT keys/config theo phần bên dưới và [security](../security/overview.md), rồi chạy Spring, đọc log startup/datasource và kiểm `http://localhost:8080/api/parking/health`. Response có `status`, `service`, `version`, `database`; `database` là tên sản phẩm JDBC hoặc `Unknown`, không có host/schema/credentials. Status vẫn `UP` nếu probe DB lỗi nên không thay DB/JWT readiness.
3. Chạy ANPR, chờ initialization/model warm-up, kiểm `http://localhost:8001/health`. Response có `status:UP`, `service:parking-anpr`, `version:4.0-robust-liveness`, `modelLoaded`, `faceModelsReady`. Hai cờ là cache recognizer và sự tồn tại face model files, không camera/accuracy/liveness proof. `/docs` không public trong middleware hiện tại; mở browser không kèm service header có thể bị từ chối.
4. Chạy Desktop, đăng nhập bằng tài khoản DB đã đổi mật khẩu; `PARKING_SECURITY_JWT_ENABLED` mặc định true và external RS256 config phải sẵn sàng trên Spring. Kiểm `Spring API`, bấm **Kiểm tra**. Nút này có thể gửi lại outbox đang chờ, không chỉ health. Health UP không thay login/API readiness. Chỉ sau khi xác nhận services, datasource và hàng đợi mới thao tác dữ liệu thử.
5. Demo ba ảnh khởi động riêng nếu cần; không đưa vào thứ tự bắt buộc cho gate.

### Launcher tại root (lệnh ghi nhận, chưa thực thi)

```cmd
scripts\run-web.cmd
scripts\run-anpr.cmd
scripts\run-desktop.cmd
```

Chạy từng lệnh trong terminal riêng để giữ log. [run-all.cmd](../../scripts/run-all.cmd) mở Web, đợi **5 giây**, mở ANPR, đợi **8 giây**, rồi Desktop. Đây là **fixed delays**, không readiness guarantee; model/download hoặc Maven startup có thể lâu hơn. Chưa ready thì chờ log và bấm Kiểm tra lại, không mở thêm process trùng.

## Spring backend và database

[run-web.ps1](../../scripts/run-web.ps1) chọn Maven, đọc `.env.local` tại root làm fallback cho biến process chưa có/đang trống; đòi `PARKING_DB_URL`, `PARKING_DB_USERNAME`, `PARKING_DB_PASSWORD`, kiểm URL có `/parking_anpr`, chuẩn bị internal token và JWT config/key paths, rồi chọn JDK. Sau đó kiểm health version `4.0-face-slots`, tái dùng Web cùng version, báo lỗi bản Web cũ hoặc port8080 đang bị chiếm, rồi gọi `clean spring-boot:run` từ `spring-web`. Đây là startup có thể cập nhật schema theo Hibernate, không chỉ read-only health probe.

**Health/launcher trên main:** [ParkingApiController.health](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) trả đúng `version` mà Web launcher kiểm; [FastAPI health](../../anpr-service/app/main.py) trả đúng `service` mà ANPR launcher kiểm. Discrepancy được ghi ở nhánh Security trước đây không áp dụng ở đây. Tuy nhiên nhận diện chỉ dựa response fields không chứng nhận đúng môi trường/datasource; không tự kill process khi port conflict. Web launcher kiểm config/keys trước bước tái dùng service.

Manual tương đương khi bundled Maven có mặt (chưa chạy):

```cmd
cd spring-web
..\.cache\toolchains\apache-maven-3.9.11\bin\mvn.cmd clean spring-boot:run
```

Nếu dùng Maven PATH, thay executable bằng `mvn`. Giữ working directory `spring-web` vì relative media path phụ thuộc nơi chạy. Không khẳng định auto-reload: inspected pom không cung cấp bằng chứng devtools/reload và thay Java code có thể cần restart.

### H2/MySQL: khác biệt không được che giấu

- Hướng dẫn cũ từng mô tả H2 mặc định/MySQL Laragon tự tạo schema; đây không phải runtime evidence. README hiện dẫn về trang này để xác nhận datasource trước chạy.
- [application.properties](../../spring-web/src/main/resources/application.properties) là cấu hình source main; không có `application.yml`. Nó map datasource tới `PARKING_DB_URL`/`PARKING_DB_USERNAME`/`PARKING_DB_PASSWORD`, driver MySQL, dialect MySQL và **`spring.jpa.hibernate.ddl-auto=update`**. Xác nhận backup/schema permissions trước mọi startup; không suy `update` là migration an toàn. `application-laragon.properties` không được coi là authority chỉ từ tên; active profile/external override cần kiểm riêng, không chép credentials vào docs.
- [pom.xml](../../spring-web/pom.xml) không khai báo Flyway; main không có switch `PARKING_FLYWAY_ENABLED` của feature branch. Không hướng dẫn bật Flyway hoặc hứa rehearsal migration từ source main này. Desktop JWT mặc định **true**, dùng `PARKING_SECURITY_JWT_ENABLED`; issuer/audience/kid theo bảng dưới và external RS256 public/private keys bắt buộc khi bật. Có config không đồng nghĩa keys đã provisioned hay account đã tạo. Không tắt JWT để chữa login. Web dùng tài khoản DB, không có demo login mặc định.
- Web launcher đọc `.env.local` (không tự đọc `.env`); ANPR launcher chỉ đọc token qua helper, không nạp mọi biến trong `.env.local`. Biến process có trước được ưu tiên. Spring chạy Maven thủ công không tự có logic loader PowerShell này; chuẩn bị environment qua kênh an toàn cho đúng process. Không giả định `.vscode/launch.json` và CMD dùng cùng environment.
- `pom.xml` có MySQL connector **runtime**, H2 **test scope**. Vì vậy không được hứa `scripts/run-web.cmd` sẽ boot bằng H2 ở runtime. H2 memory/create-drop của integration test không là cấu hình development/production.
- Trước chạy, quản trị phải xác nhận nguồn cấu hình Spring được cung cấp, JDBC URL/driver, tài khoản qua kênh an toàn, schema và DDL policy. Không copy test `create-drop` tới DB thật; không tự xóa/reset database để sửa lỗi.
- Nếu datasource chưa rõ hoặc Web báo driver/URL/schema lỗi, dừng ở bước Spring và lấy quyết định cấu hình từ quản trị; không có command trong tài liệu này được xác nhận tự thiết lập DB.

### Laragon launchers và hạn chế thực tế

[run-all-laragon.cmd](../../scripts/run-all-laragon.cmd) gọi [run-all-laragon.ps1](../../scripts/run-all-laragon.ps1). Tên Laragon được giữ để tương thích, nhưng script hiện ghi database chính vẫn Aiven `parking_anpr`; không còn listener3306/password prompt/login probe. Nó có thể force-stop Web đã nhận diện ở8080, mở `run-web.ps1`, đợi tối đa60 vòng (mỗi vòng chờ1 giây và HTTP timeout2 giây) health/database chứa MySQL, rồi ANPR, chờ5 giây và Desktop.

**Không coi tên launcher là cấu hình DB:** [run-web-laragon.ps1](../../scripts/run-web-laragon.ps1) riêng vẫn in nhãn cloud và gọi Maven, không set datasource/profile, không load `.env.local` hoặc chuẩn bị token/JWT như `run-web.ps1`; nó cũng có nhánh force-stop. `database:MySQL` chỉ cho biết sản phẩm JDBC, không chứng minh host/schema Aiven đúng. Nhãn cloud không được dùng làm connection instruction; không chép host/user/secret vào guide.

Không chạy launcher Laragon nếu chưa rõ datasource đang dùng. Trước switch môi trường, giữ backup theo quy trình quản trị và đóng Web cũ có kiểm soát; script có nhánh `Stop-Process -Force`, không phải safe migration. Database auto-creation/persistence cần runtime evidence riêng.

### Tài khoản ban đầu, mật khẩu và JWT

1. Quản trị cấp riêng `parking.security.bootstrap.username` và `parking.security.bootstrap.password` qua external Spring configuration (relaxed-binding env tương ứng `PARKING_SECURITY_BOOTSTRAP_USERNAME`/`PARKING_SECURITY_BOOTSTRAP_PASSWORD`). [ManagementBootstrap](../../spring-web/src/main/java/vn/edu/parking/config/ManagementBootstrap.java) mặc định cả hai trống: không tạo tài khoản. Cấu hình thiếu một trong hai làm lỗi; password cần ít nhất12 ký tự và tối đa72 byte UTF-8, username trim/lowercase tối đa80 ký tự. Không điền secret thật trong command/doc.
2. Bootstrap cần role `MANAGEMENT` đã có; account đã tồn tại được giữ nguyên, **không reset password**. Startup cũng có data-initialization code, không được dùng như seed/reset DB được cho phép sẵn. Xác nhận DB/backup và quyền khởi động trước thực hiện.
3. Đăng nhập Web `/login`, vào `/account/password` nếu buộc đổi mật khẩu. Nhập mật khẩu hiện tại, mật khẩu mới và xác nhận; minimum12 ký tự/maximum72 byte UTF-8. Đổi thành công thu hồi phiên, quay `/login?passwordChanged`; đăng nhập lại rồi mới dùng Desktop. Gỡ cấu hình bootstrap khỏi môi trường sau provisioning theo quy trình quản trị, không xóa account/DB.
4. Web **Quản trị → Tài khoản** (`/account-management`) tạo/reset mật khẩu tạm, khóa/mở tài khoản và quản lý ca/phiên theo quyền. Cấp mật khẩu tạm qua kênh kín; không gửi ảnh chụp màn hình có secret. Tài khoản mới/reset phải đổi mật khẩu trên Web trước Desktop; không dùng mật khẩu demo từ tư liệu cũ.

| Spring env | Default main / launcher |
| --- | --- |
| `PARKING_SECURITY_JWT_ENABLED` | `true` trong properties và fallback launcher. |
| `PARKING_SECURITY_JWT_ISSUER`, `PARKING_SECURITY_JWT_AUDIENCE`, `PARKING_SECURITY_JWT_KID` | `https://parking.local`, `parking-desktop`, `key-2026-10`; đây là identifiers, không xác nhận TLS/deployment. |
| `PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION`, `PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION` | Properties mặc định trống; launcher fallback vào hai file `scripts/security/keys/desktop-jwt-{private,public}.pem`, kiểm tồn tại và dừng nếu thiếu; không sinh JWT keys. Provision bên ngoài/quy trình được duyệt, không commit key. |

Không khẳng định Secure session cookie default của nhánh Security trên main: properties hiện không đặt `server.servlet.session.cookie.secure`; phải kiểm effective servlet/reverse-proxy/TLS settings, không tự tắt bảo vệ production.

## ANPR startup và configuration

[run-anpr.ps1](../../scripts/run-anpr.ps1):12–111 ưu tiên `PARKING_PYTHON`, rồi `py` (3.12/3.11/3.10/3), rồi Python PATH; probe version>=3.10. Nó kiểm venv, **đổi tên** venv incompatible để giữ lại, tạo venv mới, upgrade pip và install requirements nếu hash thay đổi. Đây là side effects của lần chạy sau, không được thực hiện trong đợt tài liệu.

| Biến / nguồn | Ý nghĩa, default source (không secret) |
| --- | --- |
| `PARKING_PYTHON` | Interpreter base do người vận hành chọn; không hardcode đường dẫn máy khác. |
| `EASYOCR_MODULE_PATH`, `YOLO_CONFIG_DIR`, `PYTHONUTF8` | Launcher set local `.EasyOCR`, `.ultralytics` dưới service và UTF8; cần quyền ghi/cache/network phù hợp. |
| `ANPR_PLATE_MODEL`, `ANPR_VEHICLE_MODEL` | [recognizer](../../anpr-service/app/recognizer.py):58–72 mặc định service `models/license_plate_detector.pt` / `models/vehicle_detector.pt`. File phải đúng model và readable. |
| `PARKING_INTERNAL_SERVICE_TOKEN` | Cùng secret cho Spring/FastAPI, header `X-Parking-Service-Token`, không internal Bearer. Helper của hai launcher ưu tiên process env → token trong `.env.local` → đọc/tạo file ignored `scripts/security/keys/internal-service-token.txt` bằng32 random bytes/Base64. Đây là write side effect của launcher, không đã thực hiện trong đợt docs. Middleware không có minimum-length/ASCII policy tương đương feature branch; không suy helper validation áp dụng cho mọi env value. Không lưu token vào Desktop/source/log/docs. |
| `PARKING_INTERNAL_PREVIOUS_TOKEN` | FastAPI chấp nhận thêm token trước để rotation; không tự cấu hình Spring bằng biến này. Rotation/provisioning cần kế hoạch riêng, không đọc/xuất giá trị trong đợt docs. |
| `PARKING_AI_BASE_URL` | Spring upstream default `http://127.0.0.1:8001` (`parking.ai.base-url`); không phải `ANPR_BASE_URL`. URL và network trust phải được quản trị kiểm; không kế thừa khẳng định HTTPS/loopback-only validation của feature client. Desktop chỉ có Spring URL. |
| `PARKING_AI_CONNECT_TIMEOUT`, `PARKING_AI_READ_TIMEOUT` | Properties default `5s` / `5m`; không là SLA/latency measurement. |
| `ANPR_GPU` | EasyOCR GPU bật khi giá trị là `true`; default `false`. Không tự bảo đảm CUDA/GPU support. |
| `ANPR_PLATE_CONFIDENCE`, `ANPR_VEHICLE_CONFIDENCE` | Default0.20 /0.25; không phải quality acceptance hoặc OCR UI threshold. |
| `FACE_MATCH_THRESHOLD`, `FACE_REVIEW_THRESHOLD`, `FACE_DETECTION_THRESHOLD` | [face engine](../../anpr-service/app/face_engine.py) default0.363 /0.300 /0.60. Spring GateEvidenceService lưu upstream decision/threshold và consumption coi `PASS` là face-passed; main không có FaceVerificationPolicy tính lại độc lập của feature branch. Cần site calibration; không tự hạ threshold để chữa lỗi quyền/liveness. |
| `PARKING_DB_PASSWORD` | Spring map trực tiếp datasource password; Web launcher đòi biến process hoặc fallback `.env.local`. Không ghi giá trị vào log/docs/shell history. |
| `PARKING_PRIVATE_STORAGE_PATH`, `PARKING_UPLOAD_DIR` | Default `private-data` / `uploads` như phần Media; không dùng `parking.gate-evidence.directory` của feature branch. Effective runtime paths chưa kiểm; xem [database/media](../architecture/database.md). |
| `PARKING_EVIDENCE_REQUIRED`, `PARKING_EVIDENCE_RETENTION_DAYS`, `PARKING_EVIDENCE_CLEANUP_ENABLED`, `PARKING_EVIDENCE_CLEANUP_CRON` | Default `true`, `30`, `false`, `0 0 3 * * *`. TTL còn dùng `parking.evidence.ttl-minutes` default10 (hợp lệ1–60). Không tắt evidence/enable cleanup trên dữ liệu thật khi chưa duyệt; retention source không là deployed deletion/restore proof. |

Launcher chỉ cài `requirements.txt`, không test tooling. `requirements-test.txt` được bổ sung cho môi trường test riêng, include runtime requirements cùng pytest8.4.2/httpx0.28.1; không sửa runtime manifest hoặc tự cài global. Chỉ chuẩn bị/chạy suite khi được phép; đợt này chưa cài/chạy ANPR. [CI](../../tests/README.md#github-actions) tách workspace auto checks khỏi application workflow thủ công, không tự dùng operational DB/camera.

Sau khi venv/dependencies đã được chuẩn bị, manual ANPR tương đương (chưa chạy):

```cmd
cd anpr-service
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001
```

Launcher bind `0.0.0.0`, không chỉ loopback; mạng có thể truy cập tùy firewall. Không expose biometric/API service ra mạng không tin cậy. Port8001 đã có process: script chờ health, tái dùng ANPR đúng service hoặc báo process/PID conflict. Unexpected native exit sẽ retry sau2 giây; restart loop không chứng minh lỗi được chữa. Không có `--reload`; thay source cần restart có kiểm soát.

## Desktop

[run-desktop.ps1](../../scripts/run-desktop.ps1) tìm global `dotnet.exe`, set `DOTNET_CLI_HOME=.cache/dotnet-cli`, `DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1`, tắt CLI telemetry, build Release và mở EXE chờ kết thúc. Launcher này không set `DOTNET_GENERATE_ASPNET_CERTIFICATE` như test runner/CI. Lệnh build thủ công tương đương (không mở UI):

```cmd
dotnet build desktop-winform\ParkingGateDesktop.csproj --configuration Release
```

Muốn vừa build vừa mở, dùng `scripts/run-desktop.cmd`. Thay Desktop source phải rebuild; không dùng EXE cũ để kết luận API incompatibility đã sửa. Desktop chỉ có Spring URL default `http://localhost:8080`; cả AI health/processing đi qua Spring tới `PARKING_AI_BASE_URL`, shared service token nằm ở Spring/FastAPI process, không đưa vào Desktop. Remote deployment chưa verified. Camera capture vẫn diễn ra trên máy ANPR.

**Cấu hình trạm:** chọn nguồn camera xe/mặt bằng chỉ số USB hoặc RTSP/HTTP(S), frame count default8 (UI cho1–60, proxy/ANPR clamp4–16 cho nhận dạng xe), barrier auto-close default5 giây (1–120). Chỉ driver `SIMULATOR` được hỗ trợ. [GateStationSettingsStore](../../desktop-winform/GateStationSettingsStore.cs) lưu `%LOCALAPPDATA%/ParkingGateDesktop/station-settings.json`; file hợp lệ được ưu tiên, nếu không đọc được/không hợp lệ mới fallback `PARKING_VEHICLE_CAMERA_SOURCE`/`PARKING_FACE_CAMERA_SOURCE` → biến `*_CAMERA_INDEX` → `0`. URL camera có thể chứa credentials, file này không được mã hóa bởi store: giới hạn quyền local, không chụp/log/publish cấu hình. Không gọi mở camera để kiểm tài liệu.

**Outbox:** `%LOCALAPPDATA%/ParkingGateDesktop/offline-outbox.json` giữ entry/exit-confirm gặp lỗi kết nối với `operationId`; không giữ base64 face image (enqueue sẽ từ chối). MainForm tự thử đồng bộ và nút **Kiểm tra** cũng flush; lỗi nghiệp vụ/evidence/session có thể giữ request ở đầu hàng đợi. Không xóa/sửa JSON, đổi server hoặc đăng nhập account khác để ép gửi lại. Xem [user guide](user-guide.md) để đối soát trước retry; source không là bằng chứng offline recovery an toàn.

## Demo face-verification độc lập

[run-demo.cmd](../../face-verification-demo/run-demo.cmd) gọi [run-demo.ps1](../../face-verification-demo/run-demo.ps1), tải YuNet/SFace qua `download-models.ps1` nếu thiếu, bind loopback port8002 và tìm free port tới8012; đọc URL được in thay vì luôn assume8002. `FACE_DEMO_NO_BROWSER=1` ngăn auto browser, `FACE_DEMO_NONINTERACTIVE=1` ngăn prompt duplicate-launcher.

**Path limitation:** script resolve Python từ `parent-of-repository/anpr-service/.venv`, không từ ANPR directory bên trong repo. Nó có thể báo thiếu môi trường dù `scripts/run-anpr.cmd` đã tạo venv đúng trong repo. Không sửa script hoặc tạo venv giả để vượt lỗi. Manual alternative từ root, chỉ khi repo ANPR venv/deps và demo models đã chuẩn bị (chưa thực thi):

```cmd
cd face-verification-demo
..\anpr-service\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8002
```

Nếu8002 đã dùng, operator chọn port trống và dùng URL tương ứng. Demo API `/api/compare` ba ảnh không được chứng minh gate caller; shared model fallback không là integrated enrollment hoặc liveness guarantee.

## Xử lý sự cố an toàn

### Workspace và kiểm thử

Theo [workspace policy](../architecture/workspace-layout.md), `.cache/` có thể tái tạo khi công cụ đã dừng, `.artifacts/` cần giữ/backup riêng. Không tự xóa artifact hoặc private media. Maven có thể được chuẩn bị bằng cách giải nén bản3.9.11 được xác minh từ Apache vào `.cache/toolchains/`, hoặc cung cấp `mvn.cmd` trong PATH; ZIP local nằm ở `.cache/downloads/`, không tự tải khi thiếu.

Từ root dùng `scripts/test/run-tests.ps1 -Suite spring|desktop|anpr|all|tooling|thesis` (chọn một giá trị, không chép dấu `|` thành lệnh). Spring runner yêu cầu `JAVA_HOME` JDK21. Xem [hướng dẫn test](../../tests/README.md) và [kết quả/giới hạn](../testing/strategy.md); runner không chạy các launcher/app servers hoặc cài dependencies. Spring tests tự boot application contexts theo cấu hình test, phải kiểm isolation trước chạy; main còn paths media tương đối/shared. Lựa chọn `thesis` cần tooling riêng, không mặc định sẵn trên main. `scripts/check-workspace.ps1` kiểm các đường dẫn legacy đã biết, không là security/secret audit đầy đủ. Source main health/launcher khớp fields như đã nêu; chưa có startup runtime verification.

| Triệu chứng | Kiểm tra / hành động supported hoặc suy luận vận hành, không claim đã sửa |
| --- | --- |
| Java/Maven không tìm thấy / build Web lỗi | Kiểm JDK21, Maven PATH/bundled path và console build log; launcher chỉ override Java ở path cụ thể. Không suy ra DB lỗi từ compilation failure. |
| Port8080/8001 bị chiếm | Đối chiếu process và health version/service; fields mà launcher kiểm có trên main nhưng không proof đúng environment. Đóng đúng cửa sổ dịch vụ nếu được phép; không tự kill process. Laragon có nhánh force-stop. |
| Datasource/driver/access/schema lỗi | Xác nhận active config, runtime driver, Aiven endpoint/schema/TLS và account qua quản trị. Không dùng nhãn Laragon để đổi sang local3306; `ddl-auto=update` có thể ghi schema, không tự reset/delete DB. |
| Python/venv/pip lỗi | Đặt `PARKING_PYTHON` đúng interpreter>=3.10 hoặc PATH rồi mở terminal mới; đọc pip/network/package log. Launcher giữ incompatible venv và tạo mới; không cần hướng dẫn xóa venv rộng. |
| Model missing/AI503/native restart loop | Xác nhận model paths, deps, file permissions và runtime compatibility; xem ANPR terminal. Model/cache files chưa proof accuracy; retry loop liên tục phải báo quản trị, không nâng timeout/hạ thresholds tùy tiện. |
| Camera không mở/ảnh không đạt422 | Kiểm nguồn đã lưu trong Cấu hình trạm, camera trên máy ANPR, quyền/app giữ USB hoặc khả năng truy cập stream mạng; đủ sáng, tránh che/mờ. Browser webcam dùng browser permission riêng. Main có source hỗ trợ camera mạng, nhưng chưa chạy camera/DirectShow/RTSP thực. |
| Face evidence/reference lỗi hoặc upstream502/503 | Spring đọc ảnh registration hoặc guest entry evidence rồi gửi bytes tới FastAPI. Kiểm reference/private storage trên Spring, `PARKING_INTERNAL_SERVICE_TOKEN`, `PARKING_AI_BASE_URL` qua kênh an toàn. Evidence phải đúng type/operation/account/Desktop session và TTL; không sửa bằng public media hoặc tự điền face flags. |
| Desktop HTML/empty/invalid JSON/404 | [ApiResponseReader](../../desktop-winform/ApiResponseReader.cs):8–74 nêu sai URL/version/redirect login. Kiểm base URLs/routes/server log; restart đúng stack đã xác nhận DB, không mặc nhiên dùng Laragon chỉ vì thông báo gợi ý. |
| HTTP400 nghiệp vụ | Đọc message: plate/card/session/rights/evidence/payment; chỉnh dữ liệu hoặc chuyển quản trị. Main dùng dialog chỉnh sửa có lý do và ca/quyền, không checkbox tự cấp phép; không dùng barrier để che authorization lỗi. |
| HTTP401/403/500/503 | Kiểm session/access hoặc server/model readiness và log; thông báo chung không chứng minh actual role enforcement. Không chép payload biometric/secret vào báo lỗi. |
| Timeout sau entry/exit/payment | Tra lookup/history/session/receipt và outbox trước gửi lại: server có thể đã commit. Main có operationId cache và durable outbox nhưng chưa kiểm recovery live; đừng tạo thao tác mới/different payload hoặc mở barrier để “đồng bộ”. Base64 face payload có thể khiến outbox từ chối lưu, không được hứa mọi timeout đã được queue. |
| Map trống/stale | Kiểm service, filters, bấm Làm mới ở sơ đồ lớn; MainForm refresh có catch bỏ qua lỗi nên empty map không proof DB trống. |
| Nhận dạng sai/chậm | Ảnh rõ/đủ sáng, kiểm plate manual theo evidence; CPU/video có thể chậm. UI warning thresholds khác model thresholds; chưa có SLA/accuracy result. Không suy từ AI type ra quyền/charging. |

Không có verified backup/restore, live offline queue/resync, production datasource/TLS/retention/security deployment trong đợt này; source có outbox/payment/shift không đóng các gaps đó. Xem [security](../security/overview.md), [NV07](../requirements/nv07-exceptions-recovery.md) và [testing strategy](../testing/strategy.md) trước đưa hệ thống vào vận hành thật.
