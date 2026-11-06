# Báo cáo nghiệm thu Phase 10 — Quản lý ô đỗ, Việt hóa giao diện, cải tiến nhận dạng biển số

[Chiến lược kiểm thử](strategy.md) · [Traceability](../requirements/traceability.md) · [Tổng quan](../overview.md) · [Vận hành](../operations/setup-and-troubleshooting.md) · [Kiến trúc](../architecture/system.md)

## Phạm vi và mốc tham chiếu

- Ngày nghiệm thu: **2026-11-06**.
- Mốc mã nguồn: `main` tại **`52ed2cf`** (`merge: integrate phase 10 slot management and localized UI`), đã push `origin/main`.
- Nhánh tích hợp: `integrate/sv2-phase10` tại `d05a852`, gồm 5 commit: `a36f7be`, `1df8373`, `600382c`, `e6a1540`, `d05a852`.
- Quy mô thay đổi: **75 file**, +5393 / −321.
- Cây file của `main` tại `52ed2cf` **trùng tuyệt đối** với nhánh đã chạy kiểm thử; do đó kết quả dưới đây áp dụng cho chính `main`, không phải bản nháp.

## Môi trường thực thi

| Thành phần | Giá trị |
| --- | --- |
| Hệ điều hành | Windows |
| Java runtime | JDK 25.0.2 (mã nguồn target 21) |
| Maven | 3.9.11 |
| .NET SDK | 8.0.427 (target `net8.0-windows`) |
| Python | 3.12.14 (`anpr-service/.venv`) |
| CSDL khi chạy | MySQL Laragon (`PARKING_DB_MODE=laragon`) |
| CSDL khi test | H2/profile test của Spring |

## 1. Trạng thái mã nguồn

| Hạng mục | Kết quả |
| --- | --- |
| Merge vào `main` | `52ed2cf`, kiểu `--no-ff`, không xung đột |
| Push `origin/main` | Thành công (`9f3a8f2..52ed2cf`) |
| Working tree | Sạch |
| `git diff --check` (working tree, staged, merge commit) | PASS sau khi xử lý 12 dòng whitespace trong `slots.html` |
| Rà soát secret/nhị phân | Không phát hiện token, khóa, `.env`, `private-data` hay file nhị phân |
| Worktree tạm | Đã xóa; runtime chuyển hẳn về `D:\Code\KhoaLuan` |

## 2. Kiểm thử tự động

Chạy trên trạng thái đã commit `d05a852`, tương đương cây file `main`.

| Bộ test | Lệnh | Kết quả |
| --- | --- | --- |
| Spring Web | `tools\apache-maven-3.9.11\bin\mvn.cmd -f spring-web\pom.xml test` | **195/195**, 45 suite, 0 fail, 0 error, 0 skip |
| ANPR Service | `.venv\Scripts\python.exe -m pytest tests -q` (cwd `anpr-service`) | **54/54**, 8,60 giây |
| Desktop WinForms | `dotnet test desktop-winform.Tests\ParkingGateDesktop.Tests.csproj` | **12/12**, 133 ms |
| Demo khuôn mặt | `python -m pytest tests -q` (cwd `face-verification-demo`) | **7/7**, 0,60 giây |

Ghi chú provenance:

- Các dòng `SQL Error 23505 – Unique index violation` trong log Spring là **chủ ý** của test chống trùng lượt đỗ và trùng thao tác đồng thời; test vẫn PASS, không phải lỗi.
- Test Spring chạy trên H2/profile test, **không** chứng minh hành vi MySQL live.
- Desktop dùng xUnit; không có bước build Release riêng trong lượt test, build Release được thực hiện ở mục 5.

## 3. Kiểm thử live trên Laragon

| Kịch bản | Kết quả | Số đo / bằng chứng |
| --- | --- | --- |
| Nhận dạng biển số trên 6 ảnh thực tế trong `private-data/evidence` | **6/6 đúng** | 2,311 – 4,888 giây/ảnh; yêu cầu ≤ 5 giây |
| Phân loại loại xe trên cùng bộ ảnh | **6/6 đúng** | – |
| Hồi quy `59F241578 → 59F241528` | PASS | Quy tắc bỏ chữ số thừa sau mã tỉnh, bỏ early-stop đơn phiếu, xếp hạng confidence + bonus đồng thuận, giới hạn `tight_otsu` 420 px |
| Quyền thành viên gia đình dùng xe của nhau | PASS | `HouseholdVehicleAuthorizationIntegrationTest`: 2 thành viên, 2 xe, đủ 4 tổ hợp vào–ra, lưu đúng `entryMember`/`exitMember` |
| Đồng bộ Web → Laragon → Desktop | PASS | Ô `T-D01` (id 333): Web đặt bảo trì → DB `status_override=BLOCKED` → Desktop hiển thị “Cấm đỗ / Đang bảo trì” |
| Đồng bộ Desktop/Web ← Laragon (mở lại) | PASS | Web mở lại → DB `NORMAL` → Web “Ô trống sẵn sàng”, Desktop xác nhận |
| Khôi phục dữ liệu sau kiểm thử | PASS | 0 lượt đang mở, 2 ô bảo trì (đúng baseline trước kiểm thử) |
| Việt hóa thông báo Web | PASS | Modal nội bộ thay `window.alert`; không lộ thuật ngữ kỹ thuật |
| Việt hóa thông báo Desktop | PASS | Hộp thoại tiếng Việt trong `MainForm`, `StationSettingsDialog`, `GateStationSettings` |
| Việt hóa ANPR và demo khuôn mặt | PASS | Thông điệp nghiệp vụ thay thông điệp kỹ thuật |

Ranh giới bằng chứng: không dùng kết quả mock, H2 hay dữ liệu giả cho các kết luận camera/model/MySQL; các mục ở bảng này chạy trên Laragon và model thật.

## 4. Trạng thái runtime sau nghiệm thu

| Thành phần | Trạng thái | Nguồn |
| --- | --- | --- |
| Web `deploy-web-1` | Healthy, `GET /api/parking/health` = 200, `database: MySQL`, `version 4.0-face-slots` | Image build từ `spring-web/` của `main` |
| ANPR `:8001` | UP, `modelLoaded/ocrReady/faceModelsReady = true` | `main/anpr-service/.venv` |
| Desktop | Build Release 0 warning/0 error, đang chạy bản `main` | `main/desktop-winform/bin/Release` |

## 5. Hạng mục BLOCKED

| Hạng mục | Lý do | Cần để gỡ |
| --- | --- | --- |
| Xác thực khuôn mặt live | Hai ảnh đăng ký `resident:5b1cf387-…png` và `resident:5ba03015-…png` không còn trên ổ đĩa | Đăng ký lại ảnh khuôn mặt hợp lệ |
| Liveness / chống ảnh giả | Nhiều evidence gần đây `liveness_status=FAIL`, điểm gần 0 | Camera thật + ảnh đăng ký hợp lệ |
| Tổng thời gian biển số + khuôn mặt ≤ 5 giây | Không chạy được nhánh khuôn mặt live | Cùng điều kiện trên |
| Quyền gia đình E2E qua camera | Mới PASS bằng integration test | Kịch bản camera thật với 2 thành viên |
| Độ chính xác AI chính thức | Chưa có manifest gán nhãn độc lập; 6 ảnh không đại diện | Bộ dữ liệu có nhãn độc lập |
| Camera thật, ảnh in, màn hình điện thoại, video phát lại | Chưa thực hiện | Thiết bị và kịch bản thử |
| Offline/replay, tải đồng thời, RBAC live, backup–restore | Chưa đủ bằng chứng live đầy đủ | Lượt vận hành riêng |
| Tuyên bố “deploy production/Internet” | Hiện là Docker local dùng Laragon | Môi trường triển khai thực tế |

## 6. Kết luận

- **PASS** cho phạm vi đã merge: quản lý ô đỗ Phase 10, đồng bộ Web–Desktop–Laragon, quyền thành viên gia đình, nhận dạng biển số, Việt hóa giao diện và toàn bộ test tự động.
- **BLOCKED** cho các hạng mục phụ thuộc camera thật, ảnh đăng ký hợp lệ và bộ dữ liệu gán nhãn độc lập.
- Không công bố tỷ lệ chính xác AI chính thức cho tới khi có manifest nhãn độc lập.
- Hệ thống đủ điều kiện để tiếp tục hoàn thiện UI/UX trên nhánh mới và bảo vệ đồ án, nhưng chưa đạt 100% yêu cầu đề cương.
