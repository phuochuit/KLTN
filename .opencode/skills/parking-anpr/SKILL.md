---
name: parking-anpr
description: Bối cảnh dự án quản lý bãi đỗ xe ANPR (Spring Web + FastAPI + WinForms). Dùng khi cần hiểu kiến trúc, sửa nghiệp vụ, chạy/test, hoặc viết báo cáo cho KLTN này.
---

# Parking ANPR — bối cảnh dự án

Hệ thống quản lý bãi đỗ xe dùng nhận dạng biển số (ANPR) thay thẻ RFID. Biển số đã chuẩn hoá là định danh chính; barrier và camera chạy bằng simulator.

## Ba service

| Service | Công nghệ | Port | Vai trò |
|---|---|---|---|
| `spring-web` | Java 21, Spring Boot 3.5.5, Thymeleaf | 8080 | Web quản trị + REST API nghiệp vụ |
| `anpr-service` | Python, FastAPI, YOLO, EasyOCR, OpenCV | 8001 | Nhận dạng biển số, xác thực khuôn mặt |
| `desktop-winform` | C# .NET 8, WinForms | — | Trạm gác |
| `face-verification-demo` | Python, FastAPI | 8002 | Demo độc lập đối chiếu 3 ảnh |

**Luồng dữ liệu:** Desktop → Spring (nghiệp vụ, JWT) → FastAPI (xử lý ảnh) → Spring lưu evidence → Database.

Desktop **không** gọi thẳng FastAPI: mọi xử lý ảnh/video/khuôn mặt đi qua Spring để gắn evidence và kiểm quyền.

## Lệnh

```cmd
run-all.cmd              :: Web + ANPR + Desktop (Aiven)
run-all-laragon.cmd      :: đường dự phòng MySQL local
setup-new-machine.cmd    :: kiểm tra 13 mục trên máy mới
```

Chạy lẻ: `run-web.cmd`, `run-anpr.cmd`, `run-desktop.cmd`. Launcher nằm ở **thư mục gốc repo**, không phải `scripts/`.

Test:

```cmd
cd spring-web && ..\tools\apache-maven-3.9.11\bin\mvn.cmd test
cd anpr-service && .venv\Scripts\python.exe -m pytest tests/
dotnet test desktop-winform.Tests
```

Không bấm đúp file `.ps1` — Windows có thể mở bằng Notepad. Luôn dùng `.cmd`.

## Quy ước nghiệp vụ

- **Biển số** thay RFID; chuẩn hoá trước khi so khớp.
- **Liveness tách khỏi face-match**: `SPOOF` không mở barrier, `REVIEW` cần xác nhận qua `ManualOverrideDialog`, ảnh tĩnh trả `NOT_EVALUATED`.
- **Hai tác vụ song song** (biển số + khuôn mặt), mỗi tác vụ và tổng ≤5s.
- **Presence Desktop**: heartbeat 2s, polling 2s, timeout 6s — Desktop chết tự rời danh sách trạm.
- **Outbox**: ghi atomic, chống trùng bằng UUID + SHA-256 payload ở Spring.
- **OCR**: `denoised → enlarged → straightened → equalized`, early-stop khi `conf ≥ 0.80 && format_score ≥ 0.35`.

## Bản đồ module

**Controller (Spring):** `ParkingApi`, `Admin`, `Login`, `Account`, `AccountAdministration`, `AccountManagementPage`, `Payment`, `SecurityRolePage`, `EvidenceComparison`, `ExceptionReport`, `GateEvidenceAdmin`, `AiProxy`, `CccdQr`, `DesktopAuth`.

**Template chính:** `dashboard`, `slots`, `sessions`, `exceptions`, `households`, `vehicles`, `cards`, `registrations`, `pricing`, `payments`, `account-management`, `security-roles`, `security-audit`, `settings`.

**Desktop:** `MainForm`, `ParkingMapForm`, `ManualOverrideDialog`, `EvidenceComparisonDialog`, `OfflineOutboxStore`, `GateStationSettingsStore`.

## Tài liệu nguồn

Trỏ tới `docs/` thay vì đọc lại code:

| Cần gì | Đọc |
|---|---|
| Kiến trúc, luồng, ranh giới | `docs/architecture/system.md` |
| ERD, database, từ điển dữ liệu | `docs/architecture/erd.md`, `database.md`, `data-dictionary.md` |
| Hợp đồng API | `docs/api/contracts.md`, `endpoints.md`, `authentication.md` |
| Yêu cầu NV01–NV08 | `docs/requirements/` |
| Quyết định kiến trúc + chính sách mở P01–P09 | `docs/decisions/README.md` |
| Trạng thái thực tế + số đo | `docs/implementation-status.md` |
| Cài đặt, xử lý sự cố | `docs/operations/setup-and-troubleshooting.md` |
| Chiến lược test | `docs/testing/strategy.md` |

## Cạm bẫy đã gặp

- **`.env.local` bị gitignore** — chỉ có `.env.local.example`. `run-web.ps1` chốt cứng URL phải chứa `/parking_anpr`, nếu không thì từ chối chạy.
- **`application.properties` không có giá trị mặc định** cho `${PARKING_DB_URL/USERNAME/PASSWORD}` → `run-web-laragon.ps1` không nạp `.env.local` nên không khởi động được.
- **Không có Flyway.** Schema do `ddl-auto=update` + 11 script tay trong `scripts/database/`.
- **Không có H2 runtime.** H2 chỉ ở scope `test`.
- **`private-data/` bị gitignore** (138 ảnh) — máy mới sẽ trống ảnh bằng chứng.
- **`mvn clean` song song** giữa 2 instance xoá `target/classes` → `ClassNotFoundException: vn.edu.parking.domain.LivenessStatus`.
- **`mvn test` khi Web đang chạy** có thể fail tạm thời (`ArrayIndexOutOfBoundsException` trong `ExceptionReportService.class`); chạy lại khi không có app thì pass.
- **Outbox chỉ bắt lỗi mất kết nối** (`HttpRequestException`/`IOException`/`OperationCanceledException`); lỗi 5xx từ Spring **không** vào outbox.
- **Database trống không có tài khoản** — `DemoDataConfig` không seed account; `ManagementBootstrap` cần `parking.security.bootstrap.username/password`.
