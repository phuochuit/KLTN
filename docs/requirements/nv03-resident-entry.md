# NV03 — Kiểm soát xe cư dân vào bãi

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân ra](nv04-resident-exit.md) · [Khách](nv05-visitor-parking.md)

## Mục tiêu, tác nhân và giới hạn bằng chứng

**Yêu cầu nguồn:** cho xe hợp lệ vào nhanh nhưng không bỏ kiểm tra người lái, xe, thẻ và gói; tạo một lượt `OPEN` có bằng chứng và quyết định mở/từ chối cổng.

- Nhân viên trạm gác kiểm tra kết quả, chọn đúng người lái và xác nhận; cư dân/chủ xe/thành viên cung cấp thẻ và hợp tác chụp ảnh, chỉ sử dụng xe được cấp quyền.
- Camera biển/mặt, đầu đọc thẻ và cảm biến/nút mô phỏng cung cấp dữ liệu. Desktop phối hợp; API kiểm tra/lưu nghiệp vụ; AI nhận dạng; MySQL là thành phần trong thiết kế nguồn, không chứng minh datasource đang chạy.
- Ban quản lý xử lý hồ sơ, quyền và sự cố được chuyển cấp. Quyền thủ công cụ thể chưa được chốt.

**Nguồn:** generator :215–240; PDF no-EV final pp.8–9; ảnh variant charging p.7 phần mở đầu, p.8 workflow và p.9 exception/output/acceptance. Exact paths ở cuối trang. Đây là yêu cầu được nguồn mô tả, chưa xác lập bản authoritative. Phê duyệt viết tài liệu không phê duyệt các chính sách còn tranh chấp. Quan sát code và test hiện diện bên dưới không phải kết quả runtime.

## Điều kiện trước, inputs và outputs

- Theo nguồn: cổng ở chế độ nhận xe, không có lượt khác đang xử lý cùng làn. Hồ sơ xe/người và quyền theo từng xe từ NV01; thẻ/gói từ NV02; nhóm chính thức từ NV06 phải đáp ứng các kiểm tra trước khi cho qua.
- Input: frames/video biển và mặt, mã thẻ, biển số OCR, loại xe AI, mặt realtime, thời gian/cổng; hồ sơ/thẻ/gói/danh sách người được phép dùng làm tham chiếu.
- Output thành công: đúng một `ParkingSession OPEN`, evidence và quyết định mở barrier mô phỏng. Từ chối: không tự mở cổng, hiển thị lý do/cảnh báo, chuyển xử lý ngoại lệ khi cần.
- Không tự đặt ngưỡng chất lượng, thời gian phản hồi, quota hay số lần retry; nguồn nói cấu hình, giá trị được duyệt chưa có đủ bằng chứng.

## Luồng chính theo nguồn

| Bước | Thao tác / điều kiện |
| --- | --- |
| 1 | Cảm biến/nút mô phỏng kích hoạt; Desktop lấy nhiều frames camera biển và camera mặt |
| 2 | Quality gate chọn khung tốt; mờ/cháy/che thì chụp lại trước nhận dạng |
| 3 | YOLO phát hiện biển, OCR ký tự, chuẩn hóa để tra cứu |
| 4 | AI đề xuất nhóm xe; so với loại chính thức trong hồ sơ, không tự đổi hồ sơ |
| 5 | Phát hiện mặt, kiểm tra chất lượng/liveness, tính điểm với các mẫu được phép |
| 6 | Quét thẻ; API tìm Card, Vehicle, MonthlyPass, Apartment và VehicleAuthorization theo mô hình nguồn |
| 7 | Kiểm tra ACTIVE, gói còn hạn, plate/type phù hợp, xe chưa trong bãi và không bị khóa |
| 8 | Người lái là chủ xe hoặc thành viên được gán quyền **chính xe đó**; cùng hộ không tự có quyền |
| 9 | So realtime với ảnh đăng ký và tham chiếu CCCD được bảo vệ; không yêu cầu xuất trình CCCD mỗi lượt, giữ khác biệt variant dưới đây |
| 10 | Desktop hiển thị từng ĐẠT/KHÔNG ĐẠT: biển, mặt, thẻ, gói, loại và quyền người lái |
| 11 | Chỉ khi tất cả kiểm tra bắt buộc đạt mới tạo `OPEN` trong transaction |
| 12 | Lưu panorama/plate/face, kết quả AI/model version, thời gian, cổng và nhân viên |
| 13 | Mở barrier mô phỏng; khóa xác nhận để cùng sự kiện không tạo lượt lần hai |

## Alternatives, validations và xử lý thủ công theo nguồn

1. Người nhà được cấp quyền từng xe có thể vào thay chủ xe; vẫn kiểm tra mặt/quyền, không bỏ kiểm tra vì cùng hộ.
2. Sai biển/mặt/loại, thẻ-gói hết hạn, người không được quyền: giữ cổng đóng và tạo Alert. NV02 cho hướng dẫn gia hạn hoặc guest policy; điều kiện chuyển khách chưa được quyết định, không tự đổi thành khách hợp lệ.
3. API/AI mất kết nối: NV07 dự phòng, không dùng kết quả cũ như xác thực mới. Ảnh kém thì chụp lại; hết retry cấu hình chuyển thủ công hoặc dừng, không giả lập AI đạt.
4. Đã có `OPEN`: báo trùng, kiểm tra lịch sử trước thao tác tiếp theo; không tạo thêm lượt để vượt lỗi.
5. Manual theo NV07 (generator :316–338; evidence R01): xem hồ sơ/ảnh/thẻ/quyền/giấy tờ cần thiết, CHO QUA hoặc TỪ CHỐI có lý do và xác nhận; giữ quyết định thủ công riêng với kết quả AI. Thẩm quyền/quy trình dự phòng cụ thể chưa xác minh, không coi checkbox là fulfillment audit.
6. Theo nguồn cần bảo vệ ảnh/tham chiếu CCCD và giới hạn người xem. API/media public trong code không đáp ứng mặc nhiên yêu cầu này.

## Dữ liệu, trạng thái và quan sát implementation

**Nguồn:** không có lượt → `OPEN` sau checks/transaction; từ chối không được tự mở. Evidence theo Bước 12 và quyết định cổng phục vụ NV04/NV07/NV08. Yêu cầu một OPEN và resend không trùng không đồng nghĩa chỉ cần annotation transaction.

Các symbols dưới thuộc [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java), trừ khi ghi khác.

| Symbol / exact lines | Quan sát code / giới hạn |
| --- | --- |
| `enter:40–49`, `requirePlate:476–479` | Normalize plate, dài tối thiểu 5; precheck OPEN theo entryPlate rồi lookup vehicle và `verifyDriver`. Không chứng minh đầy đủ định dạng biển Việt Nam |
| `verifyDriver:397–417` | Member được chọn phải active và trong authorizedMembers của xe; chọn người không được quyền vẫn bị chặn dù override. Thiếu ảnh hoặc faceVerified=false được bỏ qua khi override; faceSimilarity không được dùng quyết threshold |
| Cùng helper `:400–405` | Không chọn member, không ai có ảnh → legacy bypass; có ảnh nhưng manualOverride → có thể trả null driver. Không phải xác thực CCCD/liveness |
| `resolveCard:470–473`, `enter:55–77` | Blank card → null; unknown supplied card → lỗi; known inactive → lỗi. Missing/mismatched card hoặc matching MONTHLY không hợp lệ → warning/giá lượt nhưng **vẫn tạo OPEN**, khác nguồn fail-closed |
| `resolveVehicleType:482–491`, `enter:51–83` | Registered vehicle dùng loại hồ sơ; cameraType được parse nhưng không có mismatch rejection trong enter. Không coi AI type check đã enforced |
| `enter:79–94` | Lưu plate/vehicle/card/type/entryMember/time/OPEN/face flags/score; chỉ saveCaptured khi base64 không trống; save rồi assignSlot. Không thấy setter manualOverride trong body |
| `assignSlot:337–380` (evidence R01) | Phân vị trí theo borrowing/assigned/free compatible; không có ứng viên thì lượt có thể chưa gán. Không chứng minh một chỗ đỗ vật lý luôn được bảo đảm |
| [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java):7–43; [repository](../../spring-web/src/main/java/vn/edu/parking/repository/ParkingSessionRepository.java):12–18 | Entity có flags/member/entryFaceImagePath nhưng không có unique OPEN declaration; repository có exists/findFirst, chưa thấy locking ở các declarations này. Chưa bảo đảm concurrent duplicate prevention |

## Ranh giới Desktop, ANPR và authorization

- [MainForm](../../desktop-winform/MainForm.cs):154–195 → [AnprApiClient](../../desktop-winform/AnprApiClient.cs):19–20 gửi ảnh/video tới ANPR, lấy plate/type/confidence; low confidence (OCR <0.45 hoặc detection <0.25 hoặc plate blank) tự chọn `_manual`. Đây là ngưỡng UI hiện có, không ngưỡng nghiệp vụ đã duyệt.
- MainForm :233–264 lookup authorized members; :308–335 chọn ảnh đăng ký của member → `VerifyCameraAsync` → `/face/verify-camera`; chỉ decision `PASS` thành faceVerified. Đổi selected member khác verifiedId khiến cờ false. Request không gửi ảnh realtime của cư dân từ kết quả verify; trường base64 entry lấy biến chụp khách.
- [ANPR main.py](../../anpr-service/app/main.py):152–174 tải ảnh tham chiếu từ Spring cục bộ, capture 8 frames, `compare_best`; tính liveness nhưng không đưa vào quyết định chặn. Đây là so ảnh đăng ký–camera, không chứng minh đối chiếu CCCD ba ảnh hay gọi API demo độc lập.
- [EntryRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/EntryRequest.java):5–7 nhận plate/card/type/manual/member/face flags/score/base64; [controller](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java):46–47 chỉ validate/delegate. Spring tin cờ client; call path trên không cho thấy Spring tự gọi AI.
- MainForm :211–230 gọi entry trực tiếp. OPEN có thể đã lưu dù warning khiến UI không tự mở; `!warning || manual` mở mô phỏng. :86–87 có nút mở trực tiếp; :386–387 đổi label/timer tự đóng sau 5 giây, không hardware. Body entry không khóa nút bằng SetBusy; idempotency event/per-lane chưa xác minh.
- [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java):17–21 permitAll `/api/**`, `/uploads/**`, bỏ CSRF API. Không tuyên bố RBAC gate/manual hoặc protected evidence đã triển khai.

## Xung đột nguồn và câu hỏi còn mở

- **Bước 9:** generator :230, ảnh `../../evidence/qa/proposal-with-ev-charging/page-08.png` và PDF final p.8 nói so realtime–registration–CCCD; `../../evidence/qa/proposal-without-ev-charging/preview.pdf` p.8 lại cấp thẻ/chọn gói 30 ngày. Bản đó vẫn có face checks ở Bước 5; không suy ra toàn bản bỏ xác thực. Không chọn authority hay sửa lỗi nguồn.
- Gói/thẻ fail-closed của nguồn khác warning/giá lượt của service; xác nhận thủ công trên UI không tự giải quyết conflict. Chính sách xe khóa/đủ điều kiện guest, Alert, audit/manual reason chưa được chứng minh toàn luồng.
- Liveness bắt buộc, panorama/plate/model/gate/staff evidence, privacy, idempotency và atomic filesystem–DB rollback còn gaps; entity/annotation không phải runtime proof.
- DOCX hiện tại/QA còn lại và lineage vẫn chưa xác minh; charging conflict NV06 giữ nguyên, xe điện không tự có quyền sạc.

## Phụ thuộc và tiêu chí chấp nhận

NV01 hồ sơ/quyền từng xe; NV02 card/pass/payment; NV06 official group; NV04 dùng lượt/bằng chứng vào; NV07 exceptions/manual; NV08 lịch sử/cảnh báo. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa test PASS:**

- Mọi check bắt buộc và lý do fail hiển thị rõ; chỉ xe/người/thẻ/gói hợp lệ được tự mở.
- Người cùng hộ không có quyền xe bị từ chối; người được quyền vẫn phải qua checks.
- Tạo đúng một OPEN đủ evidence; duplicate/resend/concurrent case không thêm lượt; phản hồi trong ngưỡng cấu hình (giá trị còn mở).
- Failure/outage không tự mở bằng kết quả cũ; manual reason/operator/evidence truy vết riêng, không thay AI FAIL bằng PASS.
- Barrier chỉ được mô tả là mô phỏng; evidence truy cập theo quyền.

**Test hiện diện đã đọc, không chạy:** [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) `completesEntryPreviewAndExitFlow:51–72` assert normalized plate/OPEN/no warning; `monthlyPassKeepsOwnerAndMakesVisitFeeZero:74–104` assert owner/no warning/zero preview; `familyMembersCanBeAuthorizedForTheSameVehicle:178–218` assert tạo quyền cho hai người, **không test face/driver gate**. Không dùng các tests này làm proof duplicate, liveness, manual audit hoặc physical barrier.

## Tham chiếu nguồn chính xác

- [Generator](../../tools/fill_thesis_proposal_15_20_pages.py):215–240 NV03; :316–338 NV07 thủ công/quality/outage (generation logic, không chứng minh chạy).
- [PDF no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf):pp.8–9 NV03; pp.11–12 NV07, evidence đã đọc ở R01.
- [PDF no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf):pp.8–9; khác Bước 9 p.8 được R01 ghi nhận.
- [Ảnh p.7](../../evidence/qa/proposal-with-ev-charging/page-07.png) mở đầu NV03; [p.8](../../evidence/qa/proposal-with-ev-charging/page-08.png) checks/Bước 1–13/exceptions 1–2; [p.9](../../evidence/qa/proposal-with-ev-charging/page-09.png) exception 3/output/acceptance, đã inspect R01.

Nguồn QA là nội dung đề cương, không phải QA ứng dụng. Không chọn bản authoritative theo tên thư mục.
