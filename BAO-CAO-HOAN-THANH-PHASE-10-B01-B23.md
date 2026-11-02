# BÁO CÁO NGHIỆM THU & BÀN GIAO TIẾN ĐỘ PHASE 10 (TASKS B01 – B23)
**Hệ thống Quản lý Bãi đỗ xe Thông minh Nhận diện Biển số & Khuôn mặt (KLTN)**

---

## 📌 THÔNG TIN CHUNG
* **Sinh viên thực hiện**: SV2 — Lục Xuân Thịnh (MSSV: 2001230931)
* **Người nhận báo cáo**: Trưởng nhóm dự án (Phước) & Hội đồng Khóa luận Tốt nghiệp
* **Nhánh Git triển khai**: `feature/sv2-thinh-phase10-b01-b23`
* **Đường dẫn Pull Request**: [Tạo Pull Request trên GitHub](https://github.com/phuochuit/KLTN/pull/new/feature/sv2-thinh-phase10-b01-b23)
* **Tổng thời lượng phân công**: 80 giờ chuẩn
* **Trạng thái bàn giao**: **100% HOÀN THÀNH — TOÀN BỘ TEST GREEN — SẴN SÀNG MERGE VÀO MAIN**

---

## 🏗️ NỘI DUNG ĐÃ THỰC HIỆN CHI TIẾT THEO TỪNG TASK (B01 – B23)

### Giai đoạn 1: Chuẩn hóa Mô hình Dữ liệu & Nền tảng (B01 – B03)
* **Task B01 (Entity Tầng & Khu vực)**:
  * Tạo bảng `parking_floors` (quản lý tên tầng, cờ `is_basement`, thứ tự hiển thị, sức chứa tối đa).
  * Tạo bảng `parking_zones` (quản lý phân khu, liên kết tầng, tiền tố mã `code_prefix`, đơn vị sức chứa `capacity_units`).
  * Cập nhật `parking_slots`: Bổ sung khóa ngoại `zone_id`, `vehicle_type_id`, cờ `deactivated_reason` phục vụ soft-delete bảo toàn toàn vẹn khóa ngoại.
  * Viết script migration an toàn `security-phase10-floor-zone-vehicle-type-mysql.sql` chạy được nhiều lần (idempotent).
* **Task B02 (Danh mục gõ tay thông minh `ui_option`)**:
  * Tạo entity `UiOption` và API `GET /api/admin/options?category=...`.
  * Tự động ghi nhớ tần suất sử dụng (`use_count`, `last_used_at`) của các giá trị tầng, khu vực, tiền tố khi người dùng nhập liệu, ưu tiên đưa lên đầu danh sách gợi ý.
* **Task B03 (Loại xe động & Bảng giá nguyên tử)**:
  * Tạo bảng `vehicle_types` hỗ trợ danh mục loại xe mở rộng (Xe máy = 1 đv, Ô tô = 5 đv, Xe tải nhỏ = 10 đv...).
  * Gắn chặt 1–1 `vehicle_types` với `pricing_rules`. Bắt buộc tạo bảng giá đi kèm loại xe trong cùng một Transaction; nếu bấm Hủy bảng giá thì rollback hủy tạo loại xe.

---

### Giai đoạn 2: Form Tạo Ô Thông Minh & Live Preview (B04 – B07)
* **Task B04 (Bỏ gắn cứng Tầng hầm B1 & Công tắc Tầng hầm)**:
  * Xóa bỏ hoàn toàn giá trị mặc định `"Tầng hầm B1"`. Bổ sung checkbox/pill công tắc `🏢 Đây là Tầng hầm`.
  * Khi bật tầng hầm: gợi ý tự động `B1, B2, B3...` (lấy số lớn nhất + 1). Khi tắt: gợi ý `Tầng 1, Tầng 2, Tầng 3...`.
* **Task B05 (Nhập liệu linh hoạt & Tái cấu trúc Giao diện 3 Khối Card)**:
  * Chuyển đổi 6 trường nhập liệu sang `<input list="...">` + `<datalist>`.
  * Thiết kế lại toàn bộ Modal Tạo nhanh sơ đồ tầng / khu (`slots.html`) thành 3 card trực quan:
    1. *📍 1. Vị trí & Phân khu*: Tầng, Tầng hầm, Tên khu vực.
    2. *🚗 2. Quy chuẩn phương tiện & Mục đích*: Phân loại ô, Loại xe kèm nút `+ Thêm loại mới`.
    3. *🔢 3. Quy tắc sinh mã tự động*: Tiền tố, Số bắt đầu, Số lượng ô cần tạo.
  * Khắc phục triệt để lỗi xung đột lớp phủ `z-index` giữa modal cha và các modal con (`#vehicleTypeModal` lên `z-index: 10050`, `#zoneConvertModal` lên `z-index: 10040`).
* **Task B06 (Dây chuyền sinh mã tự động, Live Preview & Cảnh báo sức chứa)**:
  * Cài đặt cơ chế cờ `data-dirty`: khi người dùng gõ tiền tố (ví dụ `T`), hệ thống tự cập nhật tên khu thành `Khu T` (nếu chưa bị sửa tay) và tự động gọi API tìm số thứ tự kế tiếp chưa dùng.
  * Bổ sung thẻ xem trước trực quan (Mint Ticket Card) hiển thị dải mã: `T01 ➔ T15`, số lượng ô và tổng đơn vị sức chứa quy chuẩn.
  * Hiển thị banner cảnh báo màu vàng cam khi tổng đơn vị sức chứa vượt ngưỡng khuyến nghị của tầng.
* **Task B07 (Luồng tạo loại xe + Bẫy nghiệp vụ Hủy)**:
  * Bổ sung Modal `#vehicleTypeModal` gồm 2 phần: Thông tin loại xe + Cấu hình bảng giá nguyên tử.
  * Xử lý bẫy nghiệp vụ: Khi bấm "Hủy" hoặc đóng modal, hủy bỏ hoàn toàn việc tạo, không lưu loại xe mồ côi chưa có giá vào database.

---

### Giai đoạn 3: Quy Đổi Sức Chứa Khu Vực & Ràng Buộc Ô Đỗ (B08 – B10)
* **Task B08 (Quy đổi sức chứa khu vực `ZoneCapacityService`)**:
  * Thực hiện thuật toán quy đổi đơn vị chuẩn: `Tổng đơn vị = Số ô × Đơn vị loại cũ`. Số ô mới = `floor(Tổng đơn vị / Đơn vị loại mới)`.
  * Phần dư không chia hết được làm tròn xuống (`floor`). Các ô cũ dôi dư được **soft-delete** (`active = false`, `deactivated_reason = 'CONVERT_REMAINDER'`), tuyệt đối không xóa cứng để bảo vệ khóa ngoại với lịch sử gửi xe.
  * Ghi nhật ký chi tiết vào bảng `slot_conversion_logs`.
* **Task B09 (Chặn đổi loại khu đang có xe)**:
  * Kiểm tra đồng thời 3 nguồn: (1) Ô đang có lượt gửi mở (`OPEN`), (2) Ô đã gán xe cư dân cố định, (3) Ô đang có lịch hẹn đỗ nhờ.
  * Nếu phát hiện xe trong khu: Khóa nút chuyển đổi, hiển thị cảnh báo đỏ và liệt kê chi tiết chính xác các mã ô đang bận (ví dụ: `A01, A02, A03`).
* **Task B10 (Chặn xe sai loại & Ràng buộc cứng 1 xe – 1 ô)**:
  * Chặn tuyệt đối xe vào ô không tương thích loại phương tiện (xe ô tô không vào ô xe máy, xe tải nặng không vào ô ô tô con).
  * Kiểm tra chặt chẽ: 1 biển số xe chỉ được gán tối đa 1 ô cố định hoặc 1 lịch đỗ nhờ, từ chối hành vi gán trùng lặp.

---

### Giai đoạn 4: Đồng Bộ Tuyệt Đối Web ↔ Desktop (B11 – B15)
* **Task B11 (Chuẩn hóa DTO vị trí, Sửa triệt để lỗi `undefined` và `UNKNOWN`)**:
  * Server tính sẵn `locationLabel` (ví dụ: `"Tầng hầm B1 - Khu A (Ô tô)"`, fallback `"Chưa xác định"`). Sửa dứt điểm lỗi ghép chuỗi bị `undefined` trên Web modal.
  * Thay thế toàn bộ nhãn thô `"UNKNOWN"` trên Desktop WinForms bằng thuật ngữ tiếng Việt chuẩn `"Chưa xác định"`.
* **Task B12 (Khôi phục đủ 4 Tab trên WinForms `SlotDetailDialog.cs`)**:
  * Bổ sung đầy đủ 4 tab đồng bộ 100% với Web:
    1. *Tab 1: Gán xe cư dân* (kèm tùy chọn gỡ gán, dòng lưu ý 1 xe - 1 ô).
    2. *Tab 2: Đỗ nhờ (Hàng xóm)* (nhập biển số xe B, thời hạn, lý do, nút hủy đỗ nhờ).
    3. *Tab 3: Điều phối xe vừa vào* (chọn xe vừa quét camera vào ô trống).
    4. *Tab 4: Khóa / Giải phóng* (đặt ô cấm đỗ / bảo trì, giải phóng ô).
* **Task B13 (Desktop nạp danh mục động từ API)**:
  * Cập nhật `ParkingMapForm.cs`: ComboBox Tầng, Khu vực, Loại phương tiện nạp động từ Spring REST API thay vì mảng cứng.
* **Task B14 (Xử lý lỗi nguyên văn tiếng Việt & Tự động xử lý 401 về Đăng nhập)**:
  * Bắt `DesktopAuthenticationException` (401 Unauthorized), hiển thị thông báo phiên làm việc hết hạn và tự động đóng cửa sổ trạm gác quay về màn hình đăng nhập.
* **Task B15 (Bộ kiểm thử đối chiếu Parity Web ↔ Desktop 10 kịch bản)**:
  * Viết integration test `WebDesktopSlotParityIntegrationTest.java` kiểm tra song song kênh Web session và kênh Desktop JWT qua 10 kịch bản điều phối.
  * Đạt kết quả **10/10 PASS**, sinh báo cáo đối chiếu chi tiết.

---

### Giai đoạn 5: Hoàn Thiện WinForms, Mật Khẩu, Thanh Toán & Outbox (B16 – B21)
* **Task B16 (Giao diện trạm gác chuẩn UX)**:
  * Kéo giãn khung thông tin cư dân/gói căn thẳng mép bằng khít bảng kết quả bên dưới ở mọi kích thước màn hình.
  * Nút bấm to rõ, đệm padding chống cắt chữ tiếng Việt.
  * Viền khung chụp khuôn mặt AI hiển thị 3 trạng thái màu sắc: Xám (mặc định), Xanh lá (khớp/PASS), Cam/Đỏ (cảnh báo/REVIEW/REJECT).
* **Task B17 (Đăng xuất Desktop mượt mà)**:
  * Nút "Đăng xuất" trên WinForms dừng toàn bộ 3 timer ngầm (`_sessionHeartbeat`, `_outboxTimer`, `_clockTimer`), đóng trạm gác và mở lại cửa sổ đăng nhập `DesktopLoginDialog`.
* **Task B18 (Lọc sạch dòng AI cảnh báo rỗng)**:
  * Dòng "AI cảnh báo" chỉ hiện khi có dữ liệu cảnh báo thực sự từ camera; ẩn hoàn toàn khi kết quả bình thường.
* **Task B19 (Sinh mật khẩu ngẫu nhiên an toàn)**:
  * Hàm `AccountManagementService.newTemporaryPassword()` sinh mật khẩu ngẫu nhiên 12 ký tự bảo mật, đảm bảo có cả chữ hoa, chữ thường, chữ số và ký tự an toàn.
* **Task B20 (Tự động thanh toán khi xe ra trạm)**:
  * Phân biệt rõ xe miễn phí (cư dân có gói còn hạn, `fee = 0`) và xe tính phí (`fee > 0`).
  * Tự tạo phiếu thu `PENDING` khi xem trước phí và chuyển thành `PAID` khi xác nhận xe ra, mở barrier tự động.
* **Task B21 (Hàng đợi ngoại tuyến Outbox & Chống mất dữ liệu)**:
  * Quản lý trạng thái `PENDING`, `SENT`, `FAILED_RETRY`, `DEAD_LETTER`.
  * Cơ chế thử lại với exponential backoff, cô lập các bản ghi lỗi vào Dead-Letter để hàng đợi không bao giờ bị tắc nghẽn khi rớt mạng.

---

### Giai đoạn 6: Kiểm Thử Toàn Diện & Bàn Giao (B22 – B23)
* **Task B22 (Kiểm thử 4 Suite tự động)**: Toàn bộ các test suite đều đạt kết quả xanh 100%.
* **Task B23 (Checklist nghiệm thu 12 mục)**: Rà soát sạch sẽ các tiêu chuẩn về Null-safety, khóa ngoại, không lỗi 500, tiếng Việt nhất quán, workspace sạch không file rác.

---

## 📊 TỔNG HỢP BẰNG CHỨNG KIỂM THỬ THỰC TẾ (TEST VERIFICATION RESULTS)

Mọi kết quả kiểm thử dưới đây đều được chạy trực tiếp trên môi trường thực tế (MySQL Laragon, Spring Boot, .NET 8, Python FastANPR) và đã được ghi nhận vào nhật ký hệ thống:

| Tầng kiểm thử | Đối tượng kiểm tra | Số lượng test | Kết quả | Trạng thái |
| :--- | :--- | :---: | :---: | :---: |
| **1. Playwright E2E Live** | Toàn bộ giao diện Web Phase 10 (Google Chrome thật, tốc độ chậm, banner hướng dẫn + vòng sáng Neon) | 12 bước E2E | **12 / 12 PASS** | 🟢 XANH 100% |
| **2. WinForms Desktop xUnit** | Logic Outbox, mã hóa thẻ, đồng bộ trạm gác khi rớt mạng (`ParkingGateDesktop.Tests.dll`) | 12 tests | **12 / 12 PASS** | 🟢 XANH 100% |
| **3. Web ↔ Desktop Parity** | Đối chiếu song song 10 thao tác điều phối giữa Web và Desktop (`WebDesktopSlotParityIntegrationTest`) | 10 tests | **10 / 10 PASS** | 🟢 XANH 100% |
| **4. Luồng xe & Thanh toán** | Luồng xe vào/ra, tính phí, tạo phiếu PENDING $\rightarrow$ PAID (`ParkingFlowIntegrationTest`, `PaymentTransactionServiceTest`) | 15 tests | **15 / 15 PASS** | 🟢 XANH 100% |
| **5. Quản lý tài khoản & Mật khẩu** | Sinh mật khẩu an toàn $\ge 8$ ký tự đủ hoa, thường, số (`AccountManagementServiceTest`) | 200 vòng lặp | **PASS** | 🟢 XANH 100% |
| **6. Workspace & Docs Integrity** | Kiểm tra vệ sinh mã nguồn và liên kết tài liệu (`check-docs.py`, `check-workspace.ps1`) | 56 tài liệu | **0 Findings** | 🟢 XANH 100% |

---

## 🚀 HƯỚNG DẪN LEAD REVIEW VÀ CHẠY THỰC TẾ TRÊN MÁY

Để kiểm tra lại toàn bộ kết quả trên máy của Lead:

1. **Kéo nhánh về kiểm tra:**
   ```powershell
   git fetch origin
   git checkout feature/sv2-thinh-phase10-b01-b23
   ```
2. **Chạy kịch bản Playwright E2E tự động trực quan (Google Chrome):**
   ```powershell
   .\scripts\test-e2e-live.ps1
   ```
   *(Trình duyệt sẽ tự động mở lên toàn màn hình, thực hiện chậm rãi 12 bước kiểm thử có banner dính đỉnh hướng dẫn chi tiết và viền Neon dạ quang làm nổi bật vị trí thao tác).*
3. **Mở Trạm gác WinForms để trải nghiệm thực tế:**
   ```powershell
   .\scripts\run-desktop.ps1
   ```
   *(Đăng nhập: `admin` / `Admin@123456`).*
4. **Chạy bộ test xUnit WinForms:**
   ```powershell
   $env:DOTNET_ROOT = "$env:LOCALAPPDATA\dotnet8"; $env:Path = "$env:DOTNET_ROOT;" + $env:Path; dotnet test desktop-winform.Tests --configuration Release --no-build
   ```
5. **Chạy bài test đối chiếu Parity Web ↔ Desktop:**
   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'; $env:Path = "$env:JAVA_HOME\bin;" + $env:Path; tools\apache-maven-3.9.11\bin\mvn.cmd -f spring-web/pom.xml test "-Dtest=WebDesktopSlotParityIntegrationTest"
   ```

---

## 🎯 KẾT LUẬN & ĐỀ XUẤT
Phần việc Phase 10 (Tasks `B01` đến `B23`) đã hoàn tất 100% với chất lượng cao nhất, tuân thủ nghiêm ngặt chuẩn kỹ thuật, không làm ảnh hưởng đến code của các thành viên khác trong nhóm. 

Kính nhờ Trưởng nhóm (Phước) review Pull Request tại:
👉 **https://github.com/phuochuit/KLTN/pull/new/feature/sv2-thinh-phase10-b01-b23**
để tiến hành merge vào nhánh `main`.
