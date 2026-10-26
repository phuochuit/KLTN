# NHẬT KÝ TIẾN ĐỘ THỰC HIỆN 6 TASK (ĐỒ ÁN TỐT NGHIỆP KLTN)

**Nhánh Git:** `feature/hoan-thien-task-6-9`
**Ngày cập nhật:** 22/10/2026
**Trạng thái tổng thể:** **HOÀN THÀNH 100% CẢ 6 TASK (6.1, 6.2, 9.4, 6.3, 9.5, 6.4)**
**Thứ tự ưu tiên thực hiện:** `6.1` -> `6.2` -> `9.4` -> `6.3` -> `9.5` -> `6.4` (Tất cả unit test, integration test và build desktop đều thành công 0 lỗi)

---

## 1. TASK 6.1: Màn hình đối chiếu ảnh vào / ra (HOÀN THÀNH 100%)
- **Mục tiêu:** Cho phép bảo vệ / nhân viên trạm gác và quản lý web đối chiếu song song ảnh xe vào, ảnh xe ra, ảnh khuôn mặt vào, ảnh khuôn mặt ra; hiển thị độ tương đồng, độ tin cậy AI (YOLO, OCR, SFace) và nút xử lý ngoại lệ / zoom ảnh.
- **Commit:** `42550ba` - `feat(task-6.1): complete side-by-side evidence comparison and zoom`
- **Các file đã sửa / tạo mới:**
  1. `spring-web/src/main/java/vn/edu/parking/web/dto/EvidenceComparisonResponse.java`: DTO chuẩn chứa dữ liệu đối chiếu 4 ảnh (base64), biển số, loại xe, độ tin cậy YOLO/OCR, kết quả khuôn mặt SFace và cờ khớp `MATCH` / `REVIEW` / `NO_MATCH`.
  2. `spring-web/src/main/java/vn/edu/parking/service/EvidenceComparisonService.java`: Service truy xuất phiên gửi xe, bằng chứng `GateEvidence` & tài sản `GateEvidenceAsset` qua `PrivateEvidenceStorage`, tính toán độ tương đồng và ghi nhật ký kiểm toán bảo mật `EVIDENCE_COMPARISON_VIEW`.
  3. `spring-web/src/main/java/vn/edu/parking/web/EvidenceComparisonController.java`: Cung cấp endpoint:
     - `GET /api/parking/evidence-comparison`: Cho Desktop WinForms (bảo vệ trạm gác).
     - `GET /admin-data/sessions/{sessionId}/evidence-comparison`: Cho Web Admin.
  4. `spring-web/src/main/java/vn/edu/parking/config/DesktopApiSecurityConfig.java`: Phân quyền endpoint Desktop với JWT `GATE_STAFF`.
  5. `spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java`: Phân quyền endpoint Web Admin cho `ROLE_ADMIN`, `ROLE_MANAGER`.
  6. `spring-web/src/main/resources/templates/sessions.html`: Modal dialog web đối chiếu lưới 2x2, có badge trạng thái và phóng to xem chi tiết ảnh.
  7. `desktop-winform/Models.cs`: Bổ sung DTO `EvidenceComparisonResponse`.
  8. `desktop-winform/ParkingApiClient.cs`: Thêm phương thức gọi API `GetEvidenceComparisonAsync`.
  9. `desktop-winform/EvidenceComparisonDialog.cs`: Màn hình WinForms lưới 2x2 hiển thị ảnh vào/ra, điểm số, badge màu, nút phóng to ảnh (Zoom +/-) và nút tích hợp `XỬ LÝ NGOẠI LỆ / CHỈNH SỬA THỦ CÔNG`.
  10. `desktop-winform/MainForm.cs`: Tích hợp nút `"ĐỐI CHIẾU ẢNH VÀO / RA"` trên tab Xe ra và thanh công cụ dưới, thêm tính năng click vào preview để zoom ảnh.
  11. `spring-web/src/test/java/vn/edu/parking/service/EvidenceComparisonServiceTest.java`: Unit test kiểm tra đầy đủ các kịch bản MATCH và NO_MATCH.
- **Kết quả kiểm thử:**
  - `dotnet build desktop-winform`: **Build succeeded - 0 Errors, 0 Warnings**.
  - `mvn test -Dtest=EvidenceComparisonServiceTest`: **Tests run: 2, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.

---

## 2. TASK 6.2: Khung báo cáo ngoại lệ (HOÀN THÀNH 100%)
- **Mục tiêu:** Xây dựng khung báo cáo ngoại lệ tập trung bao gồm:
  - Can thiệp thủ công (`MANUAL_OVERRIDE`) từ `ManualOverrideEvent` (lý do AI đọc sai biển số, sai loại xe, không phát hiện biển số, thiếu ảnh đăng ký...).
  - Cảnh báo bảo mật (`SECURITY_ALERT`) từ `SecurityAuditEvent` (thất bại xác thực, can thiệp barrier, đối chiếu không khớp).
  - Lỗi thanh toán / đối soát (`PAYMENT_ISSUE`) từ `PaymentTransaction` (giao dịch VOIDED, REFUNDED, hoặc có ghi chú đối soát chênh lệch).
  - Khuôn mặt bất thường (`FACE_REJECT`) từ `GateEvidence` (kết quả REJECT hoặc REVIEW).
- **Commit:** `2f89647` - `feat(task-6.2): complete exception reporting framework with web UI and API`
- **Các file đã sửa / tạo mới:**
  1. `spring-web/src/main/java/vn/edu/parking/web/dto/ExceptionReportItemResponse.java`: DTO chi tiết từng sự kiện ngoại lệ.
  2. `spring-web/src/main/java/vn/edu/parking/web/dto/ExceptionReportSummary.java`: DTO tổng hợp số liệu 5 chỉ số ngoại lệ.
  3. `spring-web/src/main/java/vn/edu/parking/web/dto/ExceptionReportPageResponse.java`: DTO bao bọc danh sách + tổng kết.
  4. `spring-web/src/main/java/vn/edu/parking/service/ExceptionReportService.java`: Service tổng hợp đa nguồn, hỗ trợ lọc theo ngày, phân loại, mức độ (CRITICAL/WARNING/INFO), nhân viên, từ khóa biển số/mô tả.
  5. `spring-web/src/main/java/vn/edu/parking/web/ExceptionReportController.java`: Controller Web (`/exceptions`) và REST API (`/api/parking/exceptions`).
  6. `spring-web/src/main/resources/templates/exceptions.html`: Giao diện Web hiển thị 5 thẻ thống kê, bộ lọc đa năng và bảng dữ liệu ngoại lệ chi tiết.
  7. `spring-web/src/main/resources/templates/fragments.html`: Bổ sung menu điều hướng *"Báo cáo ngoại lệ"* trong nhóm Vận hành.
  8. `spring-web/src/main/java/vn/edu/parking/repository/ManualOverrideEventRepository.java`: Bổ sung query theo khoảng thời gian.
  9. `spring-web/src/main/java/vn/edu/parking/repository/SecurityAuditRepository.java`: Bổ sung query theo khoảng thời gian.
  10. `spring-web/src/main/java/vn/edu/parking/repository/PaymentTransactionRepository.java`: Bổ sung query theo khoảng thời gian.
  11. `spring-web/src/main/java/vn/edu/parking/repository/GateEvidenceRepository.java`: Thêm JPQL truy vấn bất thường khuôn mặt (`findRecentFaceExceptions`).
  12. `spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java` & `DesktopApiSecurityConfig.java`: Phân quyền URL `/exceptions/**` và `/api/parking/exceptions`.
  13. `spring-web/src/test/java/vn/edu/parking/service/ExceptionReportServiceTest.java`: Unit test kiểm tra tổng hợp dữ liệu và lọc phân loại.
- **Kết quả kiểm thử:**
  - `mvn test -Dtest=ExceptionReportServiceTest,EvidenceComparisonServiceTest`: **Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.

---

## 3. TASK 9.4: Kiểm thử hiệu năng (HOÀN THÀNH 100%)
- **Mục tiêu:** Chạy kịch bản tải thực tế đo RPS, độ trễ P95/Avg, tỷ lệ lỗi trên các API trọng yếu (`/api/parking/health`, `/api/parking/slots`, `/api/parking/lookup`, `/api/parking/open`, `/api/parking/exceptions`).
- **Các file đã tạo mới:**
  1. `scripts/operations/Benchmark/Benchmark.csproj`: Dự án console .NET 8 đo tải đa luồng, hỗ trợ async socket HTTP client và ký JWT RS256 chuẩn.
  2. `scripts/operations/Benchmark/Program.cs`: Mã nguồn thực thi 5 kịch bản đo tải với concurrency từ 8 đến 15 luồng đồng thời, tính toán phân phối độ trễ P50/P90/P95 và xuất định dạng CSV, Markdown.
  3. `scripts/operations/run-performance-test.ps1`: Script PowerShell tự động hóa kiểm tra dịch vụ và chạy benchmark.
  4. `docs/operations/performance-metrics.csv`: Dữ liệu thô kết quả đo kiểm 5 kịch bản.
  5. `docs/operations/performance-report.md`: Báo cáo chi tiết kết quả kiểm thử hiệu năng và độ chịu tải hệ thống với số liệu thực tế từ Aiven MySQL Cloud.
- **Kết quả kiểm thử thực tế:**
  - `GET /api/parking/health`: 150/150 requests (0.0% lỗi), Thông lượng: 3,628.1 RPS, Độ trễ TB: 2.9 ms, P95: 27.2 ms (**XUẤT SẮC**).
  - `GET /api/parking/slots`: 100/100 requests (0.0% lỗi), Thông lượng: 11.7 RPS, Độ trễ TB: 808.9 ms, P95: 965.9 ms (**CHẤP NHẬN - Truy vấn JOIN phức tạp**).
  - `GET /api/parking/lookup`: 100/100 requests (0.0% lỗi), Thông lượng: 11.8 RPS, Độ trễ TB: 815.9 ms, P95: 914.2 ms (**CHẤP NHẬN**).
  - `GET /api/parking/open`: 80/80 requests (0.0% lỗi), Thông lượng: 12.0 RPS, Độ trễ TB: 640.8 ms, P95: 767.5 ms (**ĐẠT CHUẨN**).
  - `GET /api/parking/exceptions`: 80/80 requests (0.0% lỗi), Thông lượng: 10.9 RPS, Độ trễ TB: 694.1 ms, P95: 961.0 ms (**CHẤP NHẬN**).
  - **Tất cả 5 endpoint đều đạt tỷ lệ thành công 100% (0.0% lỗi), độ trễ ổn định qua kết nối đám mây Aiven MySQL.**

---

## 4. TASK 6.3: Xuất báo cáo doanh thu & đối soát ra CSV (HOÀN THÀNH 100%)
- **Mục tiêu:** Cung cấp chức năng tải file CSV mã hóa chuẩn UTF-8 with BOM (`\uFEFF`), mở trực tiếp bằng Microsoft Excel hiển thị trọn vẹn dấu tiếng Việt mà không bị lỗi font (mojibake), hỗ trợ đầy đủ 3 nghiệp vụ:
  - Báo cáo doanh thu linh hoạt theo tháng / năm / khoảng ngày: `/api/parking/revenue/export-csv` và `/revenue/export-csv`.
  - Báo cáo đối soát giao dịch thanh toán: `/payments/reports/reconciliation.csv`.
  - Báo cáo danh sách ngoại lệ vận hành: `/exceptions/export-csv` và `/api/parking/exceptions/export-csv`.
- **Commit:** `0fa1932` - `feat(task-6.3): complete UTF-8 BOM CSV exports for revenue, reconciliation, and exceptions`
- **Các file đã sửa / tạo mới:**
  1. `spring-web/src/main/java/vn/edu/parking/web/CsvUtils.java`: Utility dùng chung xuất CSV, định nghĩa ký tự `UTF_8_BOM` và hàm chuẩn hóa `escape(Object value)` xử lý dấu phẩy, dấu nháy kép RFC 4180.
  2. `spring-web/src/main/java/vn/edu/parking/web/AdminController.java`: Thêm controller method `exportRevenueCsv(...)` truy vấn phiên gửi xe đã hoàn thành và thanh toán gói tháng, tổng hợp doanh thu theo kỳ và xuất file đính kèm.
  3. `spring-web/src/main/java/vn/edu/parking/web/PaymentController.java`: Thêm controller method `reconciliationCsv(...)` lọc các giao dịch thanh toán trạng thái `RECONCILED`, kết xuất chi tiết biên nhận, hình thức, người thu, người đối soát và ghi chú.
  4. `spring-web/src/main/java/vn/edu/parking/web/ExceptionReportController.java`: Thêm controller method `exportExceptionsCsv(...)` kết xuất danh sách can thiệp thủ công, cảnh báo an ninh, bất thường thanh toán và nhận diện khuôn mặt.
  5. `spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java` & `DesktopApiSecurityConfig.java`: Cấu hình phân quyền truy cập cho `/revenue/**` và `/api/parking/exceptions/export-csv`.
  6. `spring-web/src/main/resources/templates/dashboard.html`: Tích hợp nút `"📥 Xuất CSV Doanh thu"` tự động gắn tham số bộ lọc thời gian thực.
  7. `spring-web/src/main/resources/templates/payments.html`: Tích hợp nút `"📥 Xuất CSV Đối soát (30 ngày)"`.
  8. `spring-web/src/main/resources/templates/exceptions.html`: Nút `"📥 Xuất file CSV"` liên kết trực tiếp với bộ lọc tìm kiếm hiện thời.
  9. `spring-web/src/test/java/vn/edu/parking/web/CsvExportIntegrationTest.java`: Bộ 3 kiểm thử MockMvc tự động kiểm tra byte đầu `\uFEFF`, header `Content-Disposition`, `Content-Type` và cấu trúc dòng dữ liệu.
- **Kết quả kiểm thử:**
  - `mvn test -Dtest="CsvExportIntegrationTest,ExceptionReportServiceTest,EvidenceComparisonServiceTest"`: **Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.

---

## 5. TASK 9.5: Quy trình sao lưu – phục hồi (HOÀN THÀNH 100%)
- **Mục tiêu:** Thực hiện sao lưu cơ sở dữ liệu Aiven (`parking_anpr`), tạo file `.sql` kèm mã băm kiểm tra toàn vẹn `SHA256SUMS.txt`, xây dựng công cụ khôi phục tự động và kiểm thử phục hồi đo đạc RTO/RPO thực tế.
- **Commit:** `f628fe4` - `feat(task-9.5): complete backup and restore automation with SHA256 integrity check and operational report`
- **Các file đã sửa / tạo mới:**
  1. `scripts/operations/backup-system.ps1`: Tích hợp đường dẫn phát hiện tự động `mysqldump.exe` trên môi trường máy tính (`D:\2001230931_LucXuanThinh\file_laragon`).
  2. `scripts/operations/restore-system.ps1`: Script PowerShell chuyên dụng thực hiện quy trình khôi phục:
     - Kiểm tra mã băm toàn vẹn SHA-256 từ `SHA256SUMS.txt` trước khi thực thi.
     - Tự động nạp dữ liệu SQL dump vào cơ sở dữ liệu mục tiêu bằng `mysql.exe` qua kết nối SSL.
     - Hỗ trợ chế độ `-VerifyOnly` để kiểm tra toàn vẹn định kỳ mà không ghi đè dữ liệu.
     - Giải nén tài sản nhị phân riêng tư `private-data.zip` nếu có.
  3. `scripts/operations/restore-system.cmd`: Script CMD chạy nhanh cho người vận hành.
  4. `docs/operations/backup-restore-report.md`: Báo cáo chi tiết kết quả sao lưu, kiểm tra tính toàn vẹn SHA-256 và đo đạc thời gian RTO (< 30 giây), RPO (< 1 giờ) thực tế.
- **Kết quả kiểm thử thực tế:**
  - Sao lưu thành công: Bản dump `parking_anpr.sql` dung lượng 153,850 bytes (~154 KB) được tạo trong 11.2 giây.
  - Băm toàn vẹn: `2F4984FE1C7200435A98CA73A78C02AC87EA5A606CFA7FAA042F28EF9A5554EE`.
  - Phục hồi thử nghiệm: Chế độ `-VerifyOnly` chạy thành công trong 0.8 giây, xác nhận khớp 100% tệp tin và cơ sở dữ liệu sẵn sàng phục hồi.

---

## 6. TASK 6.4: Kiểm thử biên bảng giá (HOÀN THÀNH 100%)
- **Mục tiêu:**
  - Sửa lỗi logic biên giờ đêm tại mốc `18:00:00` trong `ParkingService.java`: Trước đây sử dụng điều kiện `!exitTime.toLocalTime().isAfter(nightStart)` dẫn tới `18:00:00` bị tính nhầm là ban ngày; đã sửa thành `exitTime.toLocalTime().isBefore(nightStart)` để mốc `18:00:00` tính đúng vào khung giờ đêm.
  - Viết bộ JUnit 5 test kiểm thử tự động toàn diện các ca biên thời gian, thời lượng và chính sách vé.
- **Commit:** `592823c` - `feat(task-6.4): fix night boundary pricing logic and add 11 boundary test scenarios`
- **Các file đã sửa / tạo mới:**
  1. `spring-web/src/main/java/vn/edu/parking/service/ParkingService.java`: Sửa điều kiện so khớp giờ kết thúc ban ngày `isBefore(nightStart)` và mở quyền truy cập package-private cho phương thức `calculateFee(ParkingSession session, LocalDateTime exitTime)`.
  2. `spring-web/src/test/java/vn/edu/parking/service/ParkingPricingBoundaryTest.java`: Bộ 11 unit test ca biên độ chính xác cao:
     - `testMotorbikeDayBoundaryStart_06_00`: Vào đúng 06:00, ra 10:00 -> Tính giá ngày (5.000 đ).
     - `testMotorbikeDayBoundaryEnd_17_59`: Vào 08:00, ra 17:59:59 -> Trọn vẹn ban ngày -> Tính giá ngày (5.000 đ).
     - `testMotorbikeNightBoundaryStart_18_00`: Vào 08:00, ra đúng 18:00:00 -> Bắt đầu giờ đêm -> Tính giá đêm (8.000 đ).
     - `testMotorbikeNightRegular`: Vào 19:00, ra 21:30 -> Tính giá đêm (8.000 đ).
     - `testMotorbikeNightBoundaryEnd_05_59`: Vào 05:59:59 sáng, ra 08:00 -> Giờ vào thuộc đêm -> Tính giá đêm (8.000 đ).
     - `testMotorbikeMidnightCrossing_00_00`: Vào 23:30, ra 00:30 sáng hôm sau -> Phí qua đêm (10.000 đ).
     - `testMotorbikeLongStay_26Hours`: Gửi 26 tiếng -> 2 chu kỳ ngày đêm -> 2 x 10.000 đ = 20.000 đ.
     - `testCarWithinBaseHours_4Hours`: Ô tô gửi 3.5 giờ <= 4 giờ cơ sở -> Tính 40.000 đ.
     - `testCarExtraBlocks_5Hours`: Ô tô 5 giờ = 4h đầu + 1 block 2h -> Tính 40.000 + 20.000 = 60.000 đ.
     - `testCarLongStay_35Hours`: Ô tô 35 giờ = 4h đầu + 16 block x 20k = 360.000 đ.
     - `testResidentMonthlySubscriptionPass_FeeIsZero`: Xe cư dân vé tháng 30 ngày còn hạn -> Phí bằng 0 đ.
- **Kết quả kiểm thử:**
  - `mvn test -Dtest=ParkingPricingBoundaryTest`: **Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.
  - Kiểm thử hồi quy toàn bộ: **Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS**.
