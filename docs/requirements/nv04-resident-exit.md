# NV04 — Kiểm soát xe cư dân ra khỏi bãi

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân vào](nv03-resident-entry.md) · [Khách](nv05-visitor-parking.md)

## Mục tiêu, tác nhân và evidence

**Yêu cầu nguồn:** đúng xe, đúng người được phép lấy xe, đóng đúng lượt vào; đủ bằng chứng vào-ra, phí và cập nhật sức chứa bãi. Nhân viên trạm gác kiểm tra/xác nhận; cư dân hoặc người được gán quyền từng xe hợp tác xác minh. Camera/Desktop/API/AI hỗ trợ; Ban quản lý nhận ngoại lệ chưa giải quyết, không tự thay nhân viên quyết định cho qua.

Nguồn: generator :243–265; PDF no-EV final pp.9–10; ảnh charging p.9 workflow/exceptions 1–2 đã inspect R01. Phần còn lại p.10 của variant ảnh chưa inspect; không claim full variant parity. Quyết định được duyệt là phạm vi viết tài liệu, không chọn source authority hoặc resolve chính sách phí/quyền. Bảng code/test bên dưới là quan sát tĩnh, không test-executed.

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
2. Người không được quyền, plate/face mismatch hoặc mất thẻ: NV07, không tự mở. Manual kiểm tra hồ sơ/ảnh/thẻ/quyền/giấy tờ, quyết định có lý do/người/thời gian và evidence riêng; không đổi AI FAIL thành PASS (generator :316–338).
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

| Symbol / lines | Quan sát tĩnh / giới hạn |
| --- | --- |
| `findOpenSession:383–395` | Ưu tiên OPEN mới nhất theo plate normalized; có match thì trả ngay, không cross-check card. Nếu không, resolve card rồi tìm theo plate của card.vehicle; không thu hẹp time/gate hay hỏi chọn nhiều ứng viên |
| `resolveCard:470–473` | Supplied unknown card lỗi khi tới fallback; blank → null. Nếu plate đã match, card fallback không được gọi; không mô tả unknown card luôn bị từ chối ở exit |
| [ExitRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/ExitRequest.java):5–6; [controller](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java):49–53 | API @Valid yêu cầu plate không blank. Helper card fallback không có nghĩa API hỗ trợ chỉ card/plate rỗng; DTO không có payment confirmation/amount/reason |
| `previewExit:97–105` | Lookup, verifyGuestExit, tính phí thời điểm preview; không cưỡng chế preview trước confirm phía server |
| `confirmExit:108–126` | Lookup lại, verifyGuestExit, ghi normalized **request** exitPlate/time/fee/COMPLETED/face flags/score rồi clear slot.currentSession. Không so plate/type vào-ra hoặc gọi verifyDriver cho cư dân; không set exitMember/manualOverride trong body |
| `hasValidMonthlyPass:463–467` | Cần vehicle/card.vehicle IDs trùng, card.isMonthlyValid tại **ngày entry**. [ParkingCard](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingCard.java):43–46 vẫn cần MONTHLY, ACTIVE hiện tại, nonnull dates, inclusive bounds |
| `calculateFee:426–460` | Valid monthly → zero; còn lại PricingRule theo session type, tính duration tối thiểu 1 phút, car block/overnight hoặc non-car day/night/overnight; chi tiết canonical tại NV02. Thiếu rule gây lỗi; không thấy configured surcharge/payment check trong body này |
| Lookup/closure | Plate không match nhưng card fallback tìm được vẫn có thể ghi request plate khác entry; confirm lặp tuần tự thường lỗi vì không còn OPEN, không trả lại kết quả lần đầu. Nếu có OPEN mới hoặc concurrent requests, chưa có bảo đảm idempotency/locking đã xác minh |

Fee được tính lại khi confirm, không khóa bằng số tiền preview; không gọi preview quote là hóa đơn thanh toán đã xác nhận. Gói hợp lệ theo ngày entry nhưng status hiện tại đổi có thể thay eligibility; không khẳng định “valid lúc vào luôn miễn phí”.

## Actual call path xác thực cư dân ra và barrier

- [MainForm](../../desktop-winform/MainForm.cs):139–149 có lookup/người lấy/verify/preview/confirm. :233–264 lấy authorized members active từ [controller](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java):58–101.
- MainForm requests face verification through authenticated Spring; Spring resolves the selected active member against the vehicle, reads the registration image server-side and proxies image bytes to protected FastAPI. PASS is stored as operation/session-bound face evidence; no client URL/score is trusted. This remains registration–camera comparison, not the three-way exit–entry–registration/CCCD requirement.
- MainForm requires a matching preview before confirm. Server supports resident exit face evidence and tests binding/consumption, but does not require a face proof for every resident exit; the selected member is not persisted as `exitMember` on confirmation. **Optional face proof is not equivalent to a required authorized-collector policy.**
- DTO exit không gửi ảnh ra; ảnh ANPR được hiển thị :322 nhưng không lưu qua request. Chưa chứng minh side-by-side entry/exit evidence storage hay CCCD-reference matching trong luồng này; demo ba ảnh độc lập không phải bằng chứng tích hợp.
- ANPR tính liveness nhưng bỏ khỏi blocking decision. MainForm :295,386–387 mở mô phỏng sau success, timer đóng 5 giây; :86–87 còn nút mở trực tiếp. Không phải hardware/audit fulfillment; không khóa event/confirm bằng idempotency code trong những body này.
- [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) routes Desktop operations through the JWT chain. Exit recognition is bound to the open session and consumed at confirmation; private evidence reads are audited. H2 evidence does not establish production authorization or concurrency behavior.

## Conflicts và unresolved behavior

- Bước 9 generator :257, PDF final p.9 và ảnh charging p.9 ghi close `CLOSED`; PDF no-EV khác p.9 lại cấp thẻ/gói 30 ngày. Output/acceptance của bản khác vẫn nói đóng đúng lượt, không coi registration-like step là approved change bỏ đóng lượt.
- Source CLOSED vs code COMPLETED giữ nguyên. Hạn gói lúc ra/công nợ/alerts/phụ phí cấu hình khác entry-date/zero policy; chưa có approved business resolution.
- Chưa chứng minh resident face/exit-driver rights enforcement, lưu người lấy thực tế, payment/receipt/reconciliation, manual reason, protected media, locking/idempotent replay hoặc hoàn tác filesystem–DB.
- Current DOCX/remaining QA authority/lineage vẫn mở; R01 PARTIAL, không chốt version từ tên “final”.

## Phụ thuộc và acceptance

NV03 cung cấp OPEN/evidence; NV01 quyền người theo xe; NV02 card/pass/fees/payment; NV06 nhóm xe; NV07 missing-session/manual/outage; NV08 doanh thu/trace. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa thực thi:**

- Tìm và đóng đúng OPEN; plate/type/evidence comparison rõ, không đóng nhầm hoặc lần hai khi resend/concurrent/new session.
- Authorized family driver khác lúc vào được phép khi matching đạt và lưu actual collector; cùng hộ không đủ quyền.
- Monthly policy/fee detail/debt/alert/card checks rõ; khoản thu khớp lượt và dữ liệu doanh thu, không gọi preview là payment.
- Missing/lost-card/mismatch có NV07/manual evidence/lý do; không tự mở khi chưa đủ xác minh.
- Exit evidence, operator/reason/time và trạng thái truy vết; ảnh xem theo quyền, cập nhật sức chứa, barrier ghi rõ mô phỏng.

**Test hiện diện:** [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) `completesEntryPreviewAndExitFlow:51–72` assert OPEN/PREVIEW/COMPLETED và fee 5000 hoặc 8000; `monthlyPassKeepsOwnerAndMakesVisitFeeZero:74–104` chỉ preview zero, không monthly exit confirmation/boundary/surcharge. `familyMembersCanBeAuthorizedForTheSameVehicle:178–218` test registration authorization list, không different exit driver. Chưa có kết quả chạy tests; các cases repeated/concurrent/payment/face enforcement chưa được chứng minh bởi assertions này.

## Tham chiếu nguồn chính xác

- [Generator](../../tools/fill_thesis_proposal_15_20_pages.py):243–265 NV04 đầy đủ; :316–338 NV07 manual/outage, là generation logic.
- [PDF no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf):pp.9–10 NV04, evidence R01; [PDF no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf):pp.9–10, conflict Bước 9 p.9.
- [Ảnh charging p.9](../../evidence/qa/proposal-with-ev-charging/page-09.png):mục đích/actors/input/precondition/Bước 1–10/exceptions 1–2, đã inspect R01; không claim p.10 variant ảnh đã đọc.

Rendered proposal QA không phải application test results; unknown không tự thành “implemented” hoặc “missing toàn hệ thống”.
