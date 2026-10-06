# AGENTS.md

Hệ thống quản lý bãi đỗ xe ANPR: Spring Web (quản trị + API), Python FastAPI (AI nhận dạng biển số/khuôn mặt), C# WinForms (trạm gác).

## Chạy hệ thống

```cmd
run-all.cmd              # Web + ANPR + Desktop (H2)
run-all-laragon.cmd      # Web + ANPR + Desktop (MySQL Laragon)
```

Chạy từng phần: `run-web.cmd`, `run-anpr.cmd`, `run-desktop.cmd` (mở 3 terminal riêng).

**Không bấm đúp file `.ps1`** — Windows có thể mở bằng Notepad. Luôn dùng file `.cmd`.

## Kiến trúc

| Service | Công nghệ | Port | Vai trò |
|---|---|---|---|
| `spring-web` | Java 21, Spring Boot 3.5.5, Thymeleaf | 8080 | Web quản trị + REST API |
| `anpr-service` | Python, FastAPI, YOLO, EasyOCR, OpenCV | 8001 | Nhận dạng biển số, xác thực khuôn mặt |
| `desktop-winform` | C# .NET 8, WinForms | - | Trạm gác (giao diện người dùng) |
| `face-verification-demo` | Python, FastAPI, OpenCV | 8002 | Demo độc lập đối chiếu 3 ảnh |

**Luồng dữ liệu**: Desktop → ANPR Service (nhận dạng) → Spring Web (nghiệp vụ) → Database

## Lệnh quan trọng

### Build & Run

```cmd
# Spring Web (tự tìm Maven trong tools\)
cd spring-web
..\tools\apache-maven-3.9.11\bin\mvn.cmd clean spring-boot:run

# ANPR Service (tự tạo .venv, tự cài requirements)
cd anpr-service
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8001

# Desktop WinForms
dotnet build desktop-winform\ParkingGateDesktop.csproj --configuration Release
```

### Test

```cmd
# Spring Web
cd spring-web
..\tools\apache-maven-3.9.11\bin\mvn.cmd test

# ANPR Service
cd anpr-service
.venv\Scripts\python.exe -m pytest tests/
```

### Download Models (lần đầu)

```powershell
.\face-verification-demo\download-models.ps1
```

## API Endpoints

### Spring Web (`/api/parking/`)

- `POST /entry` — Xe vào
- `POST /exit-preview` — Xem trước phí
- `POST /exit-confirm` — Xác nhận xe ra
- `GET /lookup?plate=&cardCode=` — Tra cứu cư dân
- `GET /slots` — Sơ đồ bãi xe
- `GET /health` — Kiểm tra trạng thái

### ANPR Service

- `POST /recognize/image` — Nhận dạng biển số từ ảnh
- `POST /recognize/video` — Nhận dạng biển số từ video
- `POST /face/verify-camera` — Đối chiếu ảnh đăng ký với camera
- `POST /face/capture-camera` — Chụp ảnh khuôn mặt

## Database

- **H2** (mặc định): `spring-web/data/parking-demo.mv.db`
- **MySQL Laragon**: `127.0.0.1:3306/parking_anpr`, user `root`

## Biến môi trường

| Biến | Mặc định | Mô tả |
|---|---|---|
| `PARKING_PYTHON` | - | Đường dẫn Python executable |
| `ANPR_PLATE_MODEL` | `models/license_plate_detector.pt` | Model YOLO biển số |
| `ANPR_VEHICLE_MODEL` | `models/vehicle_detector.pt` | Model YOLO phân loại xe |
| `FACE_MATCH_THRESHOLD` | `0.363` | Ngưỡng khớp khuôn mặt |
| `FACE_REVIEW_THRESHOLD` | `0.300` | Ngưỡng cần kiểm tra |

## Lưu ý quan trọng

- **Spring Web**: Tự động reload khi dùng `spring-boot:run`
- **ANPR Service**: Cần restart server khi thay đổi code
- **Desktop WinForms**: Cần build lại sau khi thay đổi
- **Port 8080/8001**: Launcher tự kiểm tra port, đóng ứng dụng cũ trước khi chạy lại
- **Python**: Cần Python 3.10+, script tự tìm qua `PARKING_PYTHON`, `py`, `python`
- **.NET SDK**: Cần .NET 8 SDK để build WinForms.

## Tài khoản

- **Web**: `admin` / `admin123`

## Dữ liệu mẫu

- Xe máy: `59A112345`, thẻ `CARD001`
- Ô tô: `51H88888`, thẻ `CARD002`

## Tài liệu tham khảo

- `README.md` — Hướng dẫn chi tiết
- `PROJECT_GAPS.md` — Các phần còn thiếu
- `artifact.md` — Hợp đồng thực hiện artifact

### STRICT EXECUTION & TOOL USAGE RULES
1. **Execution Limit**: DO NOT exceed a maximum of 3 consecutive tool calls (Read, Edit, Grep, Shell) in a single turn.
2. **Mandatory Pause**: After executing a batch of tool calls or completing a logical step, you MUST stop, summarize the actions taken, and explicitly ask for user confirmation before proceeding.
3. **Anti-Loop Mechanism**: If a tool returns an error (e.g., Rate limit, File not found), fails, or yields the same result twice, STOP immediately. DO NOT automatically retry. Report the exact error and wait for further instructions.
4. **Read/Edit Precision**: Pinpoint the exact file and lines when analyzing or modifying code. Do not scan or read the entire project pipeline unless explicitly commanded.