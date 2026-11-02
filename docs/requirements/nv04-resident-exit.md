# NV04 — Kiểm soát xe cư dân ra khỏi bãi

## Implementation main `abd6183` — đối chiếu tĩnh

Main preview không consume/bind evidence; confirm tiêu thụ evidence có row lock, ghi COMPLETED/fee/exit-face và clear slot trong transaction. Phí dương có payment method/reference và receipt PaymentTransaction; không còn đúng khi nói DTO/body hoàn toàn không có payment. Resident exit face vẫn optional và confirm không điền exitMember. `operationId` qua ParkingIdempotencyService hỗ trợ cached replay; outbox retry không là exactly-once hoặc offline admission. Barrier đi qua IBarrierController simulator với auto-close cấu hình được, chưa có driver vật lý. Xem [contracts](../api/contracts.md), [database](../architecture/database.md), [user guide](../operations/user-guide.md).

**Phạm vi lịch sử:** phần quan sát code/test bên dưới bảo toàn snapshot Security + WIP; mô tả DTO/override/payment/timer và tên tests phải đọc như lịch sử, không main contract. Các yêu cầu người lấy xe, payment-before-close và trạng thái CLOSED theo nguồn không bị sửa để hợp thức hóa implementation COMPLETED.

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân vào](nv03-resident-entry.md) · [Khách](nv05-visitor-parking.md)

## Mục tiêu, tác nhân và evidence

**Yêu cầu nguồn:** đúng xe, đúng người được phép lấy xe, đóng đúng lượt vào; đủ bằng chứng vào-ra, phí và cập nhật sức chứa bãi. Nhân viên trạm gác kiểm tra/xác nhận; cư dân hoặc người được gán quyền từng xe hợp tác xác minh. Camera/Desktop/API/AI hỗ trợ; Ban quản lý nhận ngoại lệ chưa giải quyết, không tự thay nhân viên quyết định cho qua.

Các yêu cầu ra bãi được mô tả đầy đủ bên dưới. Chính sách phí/người lấy/closure còn mở tại [P03/P04/P05/P09](../decisions/README.md); code/test evidence không tự giải quyết chính sách.

## Điều kiện trước, inputs và outputs

- Theo nguồn: xe có lượt `OPEN` hoặc quy trình xử lý không tìm thấy lượt; nhân viên được quyền thao tác/xem evidence cần thiết.
- Input: plate/face/type lúc ra, card, lượt OPEN và ảnh/kết quả lúc vào; hồ sơ/quyền theo xe; trạng thái card/pass/debt/alerts và bảng giá.
- Output: đóng đúng lượt với giờ ra/thời lượng/fee/AI/người xác nhận/manual reason/trạng thái cuối; evidence truy cập theo quyền, doanh thu gắn đúng lượt. Fail thì không tự mở, chuyển NV07.

## Luồng chính theo nguồn

| Bước | Thao tác |
| --- | --- |
| 1 | Lấy nhiều frames lúc ra, chọn ảnh đạt quality |
| 2 | Nhận dạng biển/mặt/loại, đọc thẻ |
| 3 | Tìm OPEN theo plate chuẩn hóa; card/time/gate thu hẹp nếu nhiều ứng viên |
| 4 | So plate/type vào-ra và với hồ sơ chính thức |
| 5 | So mặt ra với ảnh vào, ảnh đăng ký và danh sách người được quyền |
| 6 | Kiểm tra gói còn hiệu lực, công nợ, cảnh báo chưa xử lý và trạng thái thẻ |
| 7 | Gói tháng hợp lệ: zero hoặc phụ phí cấu hình, vẫn lưu fee detail |
| 8 | Hiển thị ảnh vào-ra cạnh nhau, từng ĐẠT/KHÔNG ĐẠT |
| 9 | Nhân viên xác nhận; API ghi giờ ra/thời lượng/AI rồi chuyển `CLOSED` trong transaction, giữ variant conflict dưới đây |
| 10 | Mở barrier mô phỏng, đánh dấu event đã xử lý để không double-close |

## Alternatives, authorization, failures và manual

1. **Khác người lúc vào:** người lấy xe thuộc hộ **và có quyền chính xe đó** được phép khi so khớp đạt; lưu người thực tế lấy. Không bắt buộc cùng một người, không tự cho mọi người cùng hộ lấy xe.
2. Người không được quyền, plate/face mismatch hoặc mất thẻ: NV07, không tự mở. Manual kiểm tra hồ sơ/ảnh/thẻ/quyền/giấy tờ, quyết định có lý do/người/thời gian và evidence riêng; không đổi AI FAIL thành PASS.
3. Không có lượt vào: tìm theo thời gian/ảnh; vẫn không có thì lập biên bản xử lý thủ công. Không tự tạo lượt giả để đóng; cách khôi phục/duyệt cụ thể còn chưa rõ.
4. Ảnh kém/camera-AI-API lỗi: retry/chụp lại hoặc NV07 dự phòng; không giả định kết quả cũ hợp lệ. Khi chưa chứng minh quyền hoặc chưa giải quyết sai lệch thì giữ cổng đóng.
5. Phí ngoài quyền gói dùng NV02; nguồn NV04 không đặc tả đầy đủ form/payment. NV02 payment/debt và NV05 payment-before-close không chứng minh code resident có payment gate. Chính sách công nợ/phụ phí/ngày xét hạn cần quyết định, không tự đặt mức thu.
6. Repeated confirmation phải không đóng nhầm/đóng hai lần; nguồn không cung cấp schema idempotency key hay response replay, không tự thiết kế API mới.

## Dữ liệu và state transitions

- **Nguồn:** `OPEN → CLOSED`, lưu exit images/match scores/duration/fee/operator/manual reason; cập nhật sức chứa. Photos cần truy cập đúng quyền, fee/revenue khớp lượt.
- **Code:** preview trả status `PREVIEW` nhưng không đổi status DB; confirm `OPEN → COMPLETED`. [SessionStatus](../../spring-web/src/main/java/vn/edu/parking/domain/SessionStatus.java):3–4 có OPEN/COMPLETED/CANCELLED; không đổi `CLOSED` của nguồn thành COMPLETED để che khác biệt.
- [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java):7–43 có exitMember/exit flags/score/time/plate/fee, nhưng không field exit-image path/receipt/collector/manual reason/model/gate ở entity này. Có field không chứng minh caller đã lưu; không suy ra toàn subsystem thiếu những dữ liệu đó.

## Quan sát code: tìm lượt, phí và đóng

Symbols thuộc [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java).

Đối chiếu tĩnh main `abd6183` ngày 2026-10-24; không dùng backups/artifacts, không chạy app/tests. Contract current và giới hạn evidence/payment tại [API](../api/contracts.md), không thay requirements ở trên.

| Symbol / lines | Quan sát tĩnh / giới hạn |
| --- | --- |
| `findOpenSession` | Ưu tiên OPEN mới nhất theo plate normalized, không có thì normalize cardCode và tìm card.vehicle plate. Controller không có `openSessionId`/pre-binding của feature; chưa bảo đảm cross-evidence face/plate/OPEN correspondence |
| Card fallback | Main helper tự lookup, unknown/blank không có card match thì báo không tìm OPEN; không gọi `resolveCard` để throw supplied unknown card. Plate match bỏ qua fallback; DTO vẫn yêu cầu plate không blank |
| [ExitRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/ExitRequest.java); [controller](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `preview`/`confirm` | vehicleEvidenceId/faceEvidenceId, flat manualOverrideReasonCode/Text, optional UUID operationId và paymentMethod/paymentReference; không nested override.reason/amount/waiver contract của feature |
| `previewExit` | Resolve EXIT evidence không consume, chọn plate trusted trừ manual, find OPEN, authorize override/verifyGuestExit rồi tính phí. Không bind recognition vào OPEN ở preview; server không yêu cầu preview token trước confirm |
| `confirmExit` | Lock/revalidate/consume evidence, ghi exitPlate/time/fee/COMPLETED/face, manual event và clear slot. Fee>0 ghi PaymentTransaction/receipt trong transaction, method bắt buộc, BANK_TRANSFER/POS cần reference; không gọi verifyDriver cho mọi resident hoặc set exitMember |
| `hasValidPrepaidPackage` | Cần vehicle/card.vehicle IDs trùng, DAILY/MONTHLY còn hiệu lực tại **ngày entry**; không có gói thì thu giá khách vãng lai |
| `calculateFee` | Valid monthly → zero; còn lại PricingRule theo session type, tối thiểu1 phút, car block/overnight hoặc non-car day/night/overnight; NV02 giữ công thức. Fee0 không tạo payment/waiver record; fee0 không monthly có potential payment-null dereference trong message confirm, chưa chạy kiểm chứng |
| Lookup/closure; `ParkingIdempotencyService` | Optional operationId scope actor/type/key + payload hash trả cached response sau completion, khác payload bị chặn. Blank ID bỏ qua, confirm mới thường không còn OPEN; distinct-key/new OPEN/concurrency/MySQL và face–session binding chưa được chứng minh. Không outage evidence policy của feature |

Fee được tính lại khi confirm, không khóa bằng số tiền preview; không gọi preview quote là hóa đơn thanh toán đã xác nhận. Gói hợp lệ theo ngày entry nhưng status hiện tại đổi có thể thay eligibility; không khẳng định “valid lúc vào luôn miễn phí”.

## Actual call path xác thực cư dân ra và barrier

- [MainForm](../../desktop-winform/MainForm.cs) `BuildExitTab`/`LookupResidentAsync` có lookup/người lấy/verify/preview/confirm; authorized members active do [controller](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `lookup` trả về.
- MainForm gọi Spring `/api/ai/face/verify-*`; `RegisteredFaceService` đọc ảnh theo member ID, không tự kiểm active/per-vehicle rights. Evidence FACE kiểm owner/sid/type/operation/TTL/member khi resolve, giữ decision upstream, không full recognition/plate/OPEN binding như feature. Registration–camera không đủ ba-way exit–entry–registration/CCCD.
- MainForm cần preview cùng plate trước confirm. Server không bắt face proof/quyền người lấy cho mọi resident exit và không persist `exitMember`. **Optional face proof không tương đương authorized-collector policy bắt buộc.**
- DTO gửi vehicleEvidenceId/faceEvidenceId; evidence mode lấy face result server-side. Required=false/no-ID legacy vẫn dùng client flags. Ảnh ở gate evidence riêng và consume khi đóng; side-by-side/CCCD matching/demo integration chưa chứng minh.
- ANPR tính liveness nhưng không block match. `IBarrierController` chỉ có simulator; MainForm auto-close theo station setting default5s, không hardware/audit mọi nút header. `ParkingApiClient.PostDurableAsync` queue connectivity failure và retry operationId qua outbox, không exactly-once admission/physical actuation guarantee.
- JWT bật mặc định cho Desktop, tắt chọn permit-all compatibility; private media Web permission-wide với success audit, không feature owner/OPEN-session read scope. Main manual dùng `AI_RESULT_CORRECT`/ca active/đủ hai evidence, không ba exception `OVERRIDE_CREATE`; xem [security](../security/overview.md).

## Conflicts và unresolved behavior

- Một mô tả cũ đặt cấp thẻ/gói ở bước 9 thay cho closure; yêu cầu đầu ra vẫn là đóng đúng lượt. Không coi đó là phê duyệt bỏ đóng lượt: cấp thẻ thuộc NV01/NV02, P08 giữ vấn đề authority.
- Source CLOSED vs code COMPLETED giữ nguyên. Hạn gói lúc ra/công nợ/alerts/phụ phí cấu hình khác entry-date/zero policy; chưa có approved business resolution.
- Main có positive-fee payment/receipt/reconcile, manual reason/audit, private media và optional cached replay; chưa chứng minh mandatory resident face/exit-driver rights, người lấy thực tế, external settlement/waiver, distinct-key concurrency, deployed privacy hoặc filesystem–DB rollback.
- Chỉ quyết định nghiệp vụ được duyệt ở [sổ quyết định](../decisions/README.md) mới đóng các vấn đề này; không chọn rule từ tên bản render.

## Phụ thuộc và acceptance

NV03 cung cấp OPEN/evidence; NV01 quyền người theo xe; NV02 card/pass/fees/payment; NV06 nhóm xe; NV07 missing-session/manual/outage; NV08 doanh thu/trace. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa thực thi:**

- Tìm và đóng đúng OPEN; plate/type/evidence comparison rõ, không đóng nhầm hoặc lần hai khi resend/concurrent/new session.
- Authorized family driver khác lúc vào được phép khi matching đạt và lưu actual collector; cùng hộ không đủ quyền.
- Monthly policy/fee detail/debt/alert/card checks rõ; khoản thu khớp lượt và dữ liệu doanh thu, không gọi preview là payment.
- Missing/lost-card/mismatch có NV07/manual evidence/lý do; không tự mở khi chưa đủ xác minh.
- Exit evidence, operator/reason/time và trạng thái truy vết; ảnh xem theo quyền, cập nhật sức chứa, barrier ghi rõ mô phỏng.

**Test hiện diện:** main [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) `completesEntryPreviewAndExitFlow` kiểm thiếu method giữ OPEN/zero payments, CASH close/receipt/one payment; `monthlyPassKeepsOwnerAndMakesVisitFeeZero` kiểm cả confirm zero/no payment. Class tắt evidence required; authorized-list test không different-exit-driver enforcement. `ParkingIdempotencyIntegrationTest` có cached replay/conflict/retry/UUID, payment service/page có receipt/reconcile subsets; chưa chạy ở đợt docs. Các IDs `residentFaceVerificationUsesServerRegistrationImageAndBindsEntryAndExitEvidence`, `backendReviewExitRequiresReasonedOverrideAndAuditsTheOpenSession` và reports54/108/128 thuộc **feature lịch sử**, không main verification; [testing](../testing/strategy.md) giữ provenance. Mandatory resident face/rights, settlement/concurrent replay vẫn cần kiểm chứng.

## Tài liệu liên quan

[API](../api/contracts.md), [database](../architecture/database.md), [security](../security/overview.md), [testing](../testing/strategy.md). Chưa xác minh không tự thành “đã triển khai” hoặc “không có trong toàn hệ thống”.
