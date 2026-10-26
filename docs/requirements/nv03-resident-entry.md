# NV03 — Kiểm soát xe cư dân vào bãi

## Implementation main `abd6183` — đối chiếu tĩnh

Desktop gọi `/api/ai/**` rồi `/api/parking/entry`. ParkingService mặc định tiêu thụ vehicle/face evidence do Spring lưu, tra card theo plate, kiểm OPEN và member, ghi session/evidence/manual correction/slot. Entry còn nhánh legacy thiếu face profile/member và mode required=false/no-ID có thể tin request flags. Warning card/pass vẫn có thể tạo OPEN. Main dùng ManualOverrideService/AI_RESULT_CORRECT/active shift, không policy ba ngoại lệ outage của feature. `operationId` có replay cache nhưng thiếu ID bỏ qua cơ chế; không suy exactly-once hoặc uniqueness mọi concurrent plate. Xem [system](../architecture/system.md), [contract](../api/contracts.md), [security](../security/overview.md).

**Phạm vi lịch sử:** các bảng code, call paths và tests dưới đây thuộc snapshot Security + WIP; nhận xét member luôn bắt buộc, FaceVerificationPolicy, ba override và totals108 không áp nguyên trạng cho main. Luồng/acceptance yêu cầu vẫn giữ nguyên.

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân ra](nv04-resident-exit.md) · [Khách](nv05-visitor-parking.md)

## Mục tiêu, tác nhân và phạm vi

**Yêu cầu nguồn:** cho xe hợp lệ vào nhanh nhưng không bỏ kiểm tra người lái, xe, thẻ và gói; tạo một lượt `OPEN` có bằng chứng và quyết định mở/từ chối cổng.

- Nhân viên trạm gác kiểm tra kết quả, chọn đúng người lái và xác nhận; cư dân/chủ xe/thành viên cung cấp thẻ và hợp tác chụp ảnh, chỉ sử dụng xe được cấp quyền.
- Camera biển/mặt, đầu đọc thẻ và cảm biến/nút mô phỏng cung cấp dữ liệu. Desktop phối hợp; API kiểm tra/lưu nghiệp vụ; AI nhận dạng; MySQL là thành phần trong thiết kế nguồn, không chứng minh datasource đang chạy.
- Ban quản lý xử lý hồ sơ, quyền và sự cố được chuyển cấp. Quyền thủ công cụ thể chưa được chốt.

Các yêu cầu vào bãi được mô tả đầy đủ dưới đây. Quy tắc gói/identity/override và khác biệt giữa mô tả cũ được tập trung tại [P03/P04/P06/P08](../decisions/README.md). Quan sát code và test lịch sử không phải chứng nhận runtime.

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
5. Manual theo NV07: xem hồ sơ/ảnh/thẻ/quyền/giấy tờ cần thiết, CHO QUA hoặc TỪ CHỐI có lý do và xác nhận; giữ quyết định thủ công riêng với kết quả AI. Thẩm quyền/quy trình dự phòng chưa chốt; code correction main không tự phê duyệt chính sách, không coi checkbox là fulfillment audit.
6. Theo nguồn cần bảo vệ ảnh/tham chiếu CCCD và giới hạn người xem. Source main có admin media permission-wide `PRIVATE_EVIDENCE_VIEW` hoặc `ROLE_MANAGEMENT`, không reader scoping theo operator/OPEN session của feature; đây chưa là chứng nhận deployed privacy, consent hay quyền xem CCCD đã đáp ứng toàn bộ yêu cầu.

## Dữ liệu, trạng thái và quan sát implementation

**Nguồn:** không có lượt → `OPEN` sau checks/transaction; từ chối không được tự mở. Evidence theo Bước 12 và quyết định cổng phục vụ NV04/NV07/NV08. Yêu cầu một OPEN và resend không trùng không đồng nghĩa chỉ cần annotation transaction.

Các symbols dưới thuộc [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java), trừ khi ghi khác.

Quan sát tĩnh main `abd6183` ngày 2026-10-24; không backups/artifacts, không chạy app/tests. Giữ requirements/acceptance độc lập với actual behavior; contract hiện hành tại [API](../api/contracts.md).

| Symbol / exact lines | Quan sát code / giới hạn |
| --- | --- |
| `enter`, `requirePlate` | Normalize plate, dài tối thiểu 5; precheck OPEN theo entryPlate rồi lookup vehicle và `verifyDriver`. Không chứng minh đầy đủ định dạng biển Việt Nam |
| `verifyDriver` | Có member ID thì kiểm active + authorizedMembers của chính xe; thiếu ID được qua nếu không authorized member nào có ảnh hoặc override đã authorize. Có ảnh cần PASS trừ override. Main vẫn có nhánh null driver, khác claim mandatory-member của feature và yêu cầu NV03 |
| `ManualOverrideService.authorize`/`validate` | Main cần `AI_RESULT_CORRECT`, account/Desktop session/ca active, đủ VEHICLE+FACE evidence, code và text10..500. Codes gồm sửa biển/loại, FACE_REVIEW/REVIEW, FACE_FAILED/FAIL, REGISTRATION_IMAGE_MISSING/CAPTURED; không ba ngoại lệ `OVERRIDE_CREATE`/outage của feature. `FAIL` khác upstream `REJECT`; source code reason không chứng minh nhánh hoạt động E2E |
| `enter`; `AdminController.saveCard` | Card được sinh/tra bằng plate chuẩn hóa; entry không dùng request cardCode. Card tìm thấy inactive → lỗi; missing/mismatched hoặc MONTHLY không hợp lệ → warning/giá lượt nhưng **vẫn tạo OPEN**, khác nguồn fail-closed |
| `resolveVehicleType`, `enter` | Registered vehicle dùng loại hồ sơ; cameraType được parse nhưng không có mismatch rejection trong enter. Không coi AI type check đã enforced |
| `enter` + `ParkingApiController.entry` | Evidence mode lấy plate/type/member/face từ Spring-owned evidence, manual authorized có thể sửa plate/type; kiểm READY/type/ENTRY/owner/sid/TTL/member, lock/consume và gắn OPEN, ghi manual event rồi assign slot. FACE optional; không full face–vehicle/plate/session pre-binding. Explicit required=false + cả IDs trống dùng legacy flags/base64; tracked required=true |
| `assignSlot` | Phân vị trí theo borrowing/assigned/free compatible; không có ứng viên thì lượt có thể chưa gán. Không chứng minh một chỗ đỗ vật lý luôn được bảo đảm |
| [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java); [repository](../../spring-web/src/main/java/vn/edu/parking/repository/ParkingSessionRepository.java); `ParkingIdempotencyService` | OPEN precheck không unique concurrent OPEN guarantee. Optional UUID operationId scope actor/type/key + payload SHA-256, completed trả cached response, khác payload bị chặn; không bảo đảm distinct-key race/MySQL exactly-once |

## Ranh giới Desktop, ANPR và authorization

- [MainForm](../../desktop-winform/MainForm.cs) → [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) gửi image/video/camera qua Spring `/api/ai/**`; `AiProxyController`/`AiProxyService` tạo evidence private. Low-confidence UI không tự cấp quyền; correction plate/type cần `ManualOverrideService`.
- Resident face gửi member ID + operation, không bắt buộc recognition ID/plate trong route main. `RegisteredFaceService` đọc registration bytes server-side; Spring giữ decision/similarity/threshold upstream, không `FaceVerificationPolicy`. Route load ảnh không tự kiểm active/quyền xe; entry kiểm member khi có ID.
- [ANPR main.py](../../anpr-service/app/main.py) dùng `X-Parking-Service-Token`, không internal Bearer của feature. Registration–camera pairwise và liveness tính nhưng không block PASS không chứng minh ba ảnh CCCD hoặc demo integration.
- [EntryRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/EntryRequest.java) có vehicleEvidenceId/faceEvidenceId, manualOverrideReasonCode/Text và legacy flags/base64. Evidence mode bỏ qua client face claims; required=false/no-ID legacy vẫn tin chúng. Không unconditional flag-rejection claim.
- MainForm `EntryAsync`: warning có thể đã lưu OPEN dù UI không tự mở; `!warning || manual` mở mô phỏng. `IBarrierController`/`SimulatedBarrierController` và station auto-close (default5s, configurable1–120s) thay label-only claim; không hardware/audit mọi nút. Client `PostDurableAsync` sinh operationId và queue connectivity failure, `FlushOutboxAsync` retry cùng payload; chưa live recovery/per-lane proof.
- [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java)/DesktopApiSecurityConfig tách Web/JWT; JWT bật mặc định thì gate/AI cần `GATE_OPERATE`, tắt thì compatibility permit-all. Resident media qua admin route, không public `/uploads/**`; DB grants còn legacy role fallback/ROLE_MANAGEMENT. Xem [security](../security/overview.md), không deployed authorization PASS.

## Xung đột nguồn và câu hỏi còn mở

- **Bước 9:** yêu cầu xác minh realtime–registration–CCCD khác một mô tả cũ đặt cấp thẻ/chọn gói tại bước này. Không dùng khác biệt đó để bỏ xác thực; việc cấp thẻ thuộc NV01/NV02. P08 giữ vấn đề authority để người có thẩm quyền xác nhận.
- Gói/thẻ fail-closed của nguồn khác warning/giá lượt của service; xác nhận thủ công trên UI không tự giải quyết conflict. Chính sách xe khóa/đủ điều kiện guest, Alert, audit/manual reason chưa được chứng minh toàn luồng.
- Liveness bắt buộc, panorama/plate/model/gate/staff evidence, privacy, idempotency và atomic filesystem–DB rollback còn gaps; entity/annotation không phải runtime proof.
- Approval phạm vi cuối vẫn cần người có thẩm quyền; P01 giữ chính sách sạc riêng, xe điện không tự có quyền sạc.

## Phụ thuộc và tiêu chí chấp nhận

NV01 hồ sơ/quyền từng xe; NV02 card/pass/payment; NV06 official group; NV04 dùng lượt/bằng chứng vào; NV07 exceptions/manual; NV08 lịch sử/cảnh báo. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa test PASS:**

- Mọi check bắt buộc và lý do fail hiển thị rõ; chỉ xe/người/thẻ/gói hợp lệ được tự mở.
- Người cùng hộ không có quyền xe bị từ chối; người được quyền vẫn phải qua checks.
- Tạo đúng một OPEN đủ evidence; duplicate/resend/concurrent case không thêm lượt; phản hồi trong ngưỡng cấu hình (giá trị còn mở).
- Failure/outage không tự mở bằng kết quả cũ; manual reason/operator/evidence truy vết riêng, không thay AI FAIL bằng PASS.
- Barrier chỉ được mô tả là mô phỏng; evidence truy cập theo quyền.

**Test hiện diện/execution:** main `ParkingFlowIntegrationTest` có happy path và `ignoresForeignCardCodeAndUsesRecognizedPlateAsIdentifier`, nhưng evidence required tắt. `DesktopJwtIntegrationTest`, `GateEvidenceConsumptionServiceTest`/`ManualOverrideServiceTest` có proxy/JWT/evidence/manual subsets; `ParkingIdempotencyIntegrationTest` kiểm cached replay/payload conflict/retry/UUID. Chưa chạy trong đợt docs. Các IDs `residentEntryWithoutSelectedMemberFailsWithoutConsumingEvidenceOrCreatingSession`, `residentEntryRequiresActiveAuthorizedMemberAndOverrideForMissingReferenceImage`, `residentFaceVerificationUsesServerRegistrationImageAndBindsEntryAndExitEvidence`, guest binding/outage/AI_REVIEW-only và Spring108/128 thuộc **feature lịch sử**, không test main hiện hành. [Testing](../testing/strategy.md) giữ ledger đầy đủ; không real AI/liveness/MySQL/physical barrier proof.

## Tài liệu liên quan

[Quyết định](../decisions/README.md), [trạng thái implementation](../implementation-status.md), [hợp đồng API](../api/contracts.md) và [kiểm thử](../testing/strategy.md) là các điểm đối chiếu; không dùng tên bản render để chọn rule.
