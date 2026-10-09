# NV05 — Khách vãng lai vào, ra và thu phí

[Phạm vi nghiệp vụ](README.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân vào](nv03-resident-entry.md) · [Cư dân ra](nv04-resident-exit.md)

## Mục tiêu, tác nhân và nguồn

**Yêu cầu nguồn:** phục vụ khách không có hồ sơ cư dân nhưng đủ evidence đối chiếu lúc ra; khách nhận đúng xe, tiền thu gắn đúng lượt/báo cáo. Khách cung cấp ảnh/xe, giữ thẻ hoặc mã lượt và thanh toán. Nhân viên kiểm tra/sửa OCR có lý do, xác nhận dữ liệu/thu tiền/xử lý ngoại lệ; Ban quản lý kiểm tra log/ảnh khi chưa giải quyết. Desktop/API/AI/camera hỗ trợ, không tự quyết quyền sở hữu.

Nguồn: generator :268–290; hai PDF no-EV pp.10–11 (R01 không thấy substantive difference NV05 trong phần đã đọc); ảnh charging p.11 chỉ tail data/output/acceptance đã inspect. Không claim toàn NV05 của variant ảnh tương đương, p.10 còn chưa đọc. Approval viết R03 không chọn source authority hoặc phê duyệt mức phí mới. Bên dưới tách requirement, code-observed và test-present; không tests executed.

## Điều kiện trước, inputs và outputs

- Nguồn: xe không được nhận diện là cư dân ACTIVE **hoặc** nhân viên chọn loại lượt KHÁCH. Cách xác định ACTIVE/cho phép chuyển cư dân sang khách cần chính sách; không đồng nhất với warning/giá lượt của code.
- Input: ảnh biển, mặt realtime, loại xe, panorama, card/mã lượt khách, thời gian; khi ra thêm ảnh/AI lúc ra, lượt OPEN/evidence vào, PriceRule, tiền/phương thức/mã giao dịch nếu có.
- Không bắt buộc CCCD và không tạo mẫu cư dân dài hạn; identity evidence là plate/face/panorama/session để đối chiếu, **không phải đăng ký NV01**. Khi tranh chấp mới yêu cầu chứng minh quyền theo quy trình ngoại lệ, không tự đặt loại giấy tờ bắt buộc.
- Output vào: OPEN loại VISITOR, mã lượt/thẻ và evidence. Output ra: đóng đúng lượt, Payment/biên nhận hoặc miễn phí có lý do, fee detail và doanh thu liên kết lượt; thất bại thì chuyển ngoại lệ, không tự mở/thu tùy ý.

## Luồng vào theo nguồn

| Bước | Thao tác |
| --- | --- |
| 1 | Chụp plate/face/panorama, kiểm chất lượng và nhận dạng; sửa OCR chỉ khi có lý do |
| 2 | Ghi loại xe, không yêu cầu CCCD hoặc tạo profile cư dân dài hạn |
| 3 | Cấp mã lượt/thẻ khách, tạo `OPEN` loại `VISITOR`, lưu evidence |
| 4 | Thông báo quy định, bảng giá và cách xử lý mất thẻ |
| 5 | Nhân viên xác nhận dữ liệu tối thiểu rồi mở barrier |

## Luồng ra và payment theo nguồn

| Bước | Thao tác |
| --- | --- |
| 1 | Chụp plate/face/type lúc ra; tìm lượt bằng plate/card khách |
| 2 | So plate/face/type vào-ra, ảnh cạnh nhau |
| 3 | Tính thời lượng, chọn PriceRule; trình bày thành phần phí và tổng |
| 4 | Ghi phương thức thanh toán, số tiền nhận và transaction code nếu có |
| 5 | Đóng lượt, tạo Payment/biên nhận, mở barrier |

**Điều kiện chốt nguồn :289 / PDF p.11 / ảnh p.11:** không đóng khi chưa xác nhận thanh toán **hoặc miễn phí có lý do**. Nguồn không nêu trường hợp cụ thể nào được miễn, ai có quyền duyệt, mức giảm hay form; không tự đặt “khách quen”, thời gian grace hoặc miễn vì manualOverride. Zero fee của code không chứng minh exception approval/reason/payment.

## Alternatives, failures, validations và manual

1. Mất thẻ: tìm plate/ảnh; quy trình/phụ phí phải dựa khảo sát, **không tự đặt mức phạt**. Missing-card ở entry khác lost-card verification lúc exit.
2. Đổi/mờ biển, mặt che hoặc người khác lấy xe: chứng minh quyền sở hữu, Alert và manual confirmation. Không áp quy tắc người nhà cư dân để tự cho người khác lấy xe khách.
3. Không thấy lượt: không thu tùy ý; chuyển quản lý kiểm log/ảnh, lập biên bản. Không tạo lượt/giờ vào giả hoặc đoán phí.
4. Ảnh thiếu/kém, verification FAIL/REVIEW, camera/AI/API lỗi: chụp lại hoặc NV07; không giả PASS, dùng manual có evidence/lý do/người/thời gian và quyền dự phòng đã được duyệt. Giá trị retry/outage permissions còn chưa xác minh.
5. Duplicate OPEN/resend: phải bảo vệ một lượt đúng và không nhân đôi session/payment; yêu cầu chung NV03/NV07, không suy ra đã có idempotency chỉ từ transactional annotation.
6. Bảo vệ ảnh khách, giới hạn quyền truy cập/retention theo NV07; không tạo biometric enrollment dài hạn để giải quyết lượt khách. Thời hạn lưu cụ thể chưa đủ evidence.

## Dữ liệu và trạng thái

- Nguồn lưu lượt, photos vào-ra, đối chiếu, fee detail, payment, collector và exceptions; khoản thu tái tính được và trace tới đúng lượt/PriceRule. OPEN VISITOR lúc vào → đóng sau payment hoặc reasoned free exception. Nguồn NV05 không tự định nghĩa enum đóng; NV04 dùng CLOSED.
- [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java):7–43 không có enum resident/visitor riêng; service phân khách bằng `vehicle == null`, lưu type/time/plate/card/flags/score và optional entryFaceImagePath. Code đóng `COMPLETED`, không quảng bá field VISITOR/Payment/exit-photo/collector/reason từ proposal như schema actual.
- Resident không đủ card/pass nhưng vẫn có vehicle không trở thành guest face policy chỉ vì tính phí lượt; quan hệ với NV03/NV04 phải giữ rõ.

## Quan sát code và actual integration

Symbols service thuộc [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java).

| Exact reference | Code-observed / giới hạn |
| --- | --- |
| `enter:40–94`, `verifyDriver:397–399` | Require normalized plate, precheck duplicate OPEN; vehicle lookup null → guest, bỏ resident driver helper. Không có request selector KHÁCH; không kiểm ACTIVE resident theo precondition nguồn trong nhánh này |
| `resolveCard:470–473`, `enter:55–63` | Không thẻ được vào; supplied unknown code lỗi, known inactive lỗi. Guest vẫn có thể lưu known active card khác xe, body không cấp thẻ khách mới; session ID là output, không proof quy trình issuing visitor card |
| `resolveVehicleType:482–491` | Guest dùng enum parsed, invalid/blank fallback MOTORBIKE; không tự coi taxonomy code bằng sáu nhóm proposal |
| `ParkingApiController.entry` + `ParkingService.enter` | Guest capture remains optional. When supplied, Spring-owned CAPTURED evidence is bound and consumed with the resulting parking session; the client base64 is not authoritative |
| [MainForm](../../desktop-winform/MainForm.cs) → [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) | Capture and exit verification go through authenticated Spring. No direct Desktop face/camera-processing call to ANPR; no visitor enrollment |
| [ANPR main.py](../../anpr-service/app/main.py) | `/face/capture-camera` and `/face/verify-camera` require internal Bearer. Spring stores private evidence; liveness calculation does not establish blocking anti-spoof |
| [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) | Guest exit verification uses the private entry-face image for the OPEN session and requires a recent PASS proof bound to plate/session when that image exists. This is pairwise entry–camera comparison, not CCCD/standalone-demo three-image verification |
| `findOpenSession:383–395` | OPEN mới nhất theo plate trước, card fallback qua **card.vehicle plate**; không dùng trực tiếp repository visitor card-code query trong helper. Chưa chứng minh lost visitor card/session ownership recovery |
| `verifyGuestExit` | Only guest sessions **with a stored entry-face path** require a matching recent PASS face proof at preview/confirm. Capture is optional, so a guest session without entry evidence does not trigger this conditional check. Client scores/flags cannot satisfy it |
| `previewExit:97–105`, `confirmExit:108–126` | Preview tính tại now; confirm tính lại fee rồi COMPLETED/exit flags/score, clear slot. Không payment-confirmation/receipt/reason gate, không so plate/type hoặc lưu exit image trong body |
| [EntryRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/EntryRequest.java):5–7; [ExitRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/ExitRequest.java):5–6 | Plate @NotBlank; flags/scores client, exit không base64/payment/amount/reason. Card-only blank plate không phải contract được validation cho phép |

Desktop :283–305 yêu cầu preview cùng plate rồi confirm, nhưng server không đòi đã preview; không có thanh toán form trong exit-tab :139–151/confirmation body đã đọc. Không xem chuỗi thông báo “tổng thu” là payment đã thu. :86–87,386–387 có barrier label/manual buttons/timer 5 giây, không thiết bị thật hoặc log manual fulfillment.

ANPR camera routes require internal Bearer and receive registration bytes from Spring; liveness is calculated but does not gate the decision. Spring uses JWT/RBAC and private gate evidence. H2/fake tests do not prove deployed security, genuine face accuracy, liveness or payment policy.

## Pricing, payment và gaps

- Canonical formula tại [NV02](nv02-cards-passes-pricing.md), source code `calculateFee:426–460`: PricingRule theo session type; minimum 1 phút; car base/extra rounded blocks/max overnight, non-car overnight cycles hoặc day/night. Không suy ra bảng giá phân cư dân/khách hay lost-card surcharge đã implemented chỉ từ lời mô tả nguồn.
- `hasValidMonthlyPass:463–467` cần session.vehicle/card.vehicle khớp nên guest vehicle null không có monthly exemption. Không tự biến manualOverride thành miễn phí; helper fee không có discount/reason logic trong body đã đọc.
- Khách thiếu entry-face, request cờ faceVerified, payment-before-close, fee breakdown lưu bền, lịch sử giá/Payment/receipt/reconciliation/manual reasons và protected media là discrepancies/gaps cần kiểm tra/resolve, không claim toàn hệ thống hoàn toàn thiếu.
- Sequential confirm lặp thường không tìm OPEN; không replay idempotent success. New OPEN/concurrent confirm/duplicate payment chưa được chứng minh bằng precheck/annotation.
- Hai no-EV không thấy substantive NV05 conflict trong R01; charging variant p.11 cùng payment/reason acceptance nhưng full NV05 chưa so đủ. DOCX/remaining QA authority/lineage vẫn mở, không bỏ variant theo tên “final”.

## Phụ thuộc và acceptance

NV02 pricing/payment, NV06 nhóm xe, NV07 quality/manual/outage/lost-card, NV08 revenue/reconciliation. Dùng chung lifecycle với NV03/NV04 nhưng không cần NV01 enrollment để tạo lượt khách. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa chạy tests:**

- Khách không CCCD/enrollment dài hạn vẫn có plate/face/panorama/type/mã lượt và OPEN đủ evidence; OCR correction có reason.
- Exit so evidence vào-ra, đúng xe/người có quyền lấy; lost-card/mismatch/missing-session không tự mở hoặc thu tùy ý.
- Duration/PriceRule/components/rounding/final fee tái tính được; mỗi payment/receipt/collector/exception gắn đúng lượt và báo cáo.
- Không đóng trước payment confirmation hoặc free exception có reason; không invent eligible miễn/phạt.
- Retry/resend không duplicate session/payment; manual giữ kết quả AI riêng, evidence được bảo vệ; barrier mô tả đúng simulation.

**Test/execution evidence:** `guestFaceCaptureAndExitVerificationStayBehindSpringAndBindToOneParkingSession` exercises authenticated synthetic guest capture, entry binding, session-bound exit verification and evidence consumption; `clientFaceAndOverrideAssertionsCannotBypassGuestExitVerification` rejects client-supplied assertions. The 2026-10-09 full Spring suite passed 108/108 on H2/fake ANPR. This does not prove real AI/liveness, mandatory capture policy, payment, pricing or production behavior.

## Tham chiếu nguồn chính xác

- [Generator](../../tools/fill_thesis_proposal_15_20_pages.py):268–290 NV05 đầy đủ, :316–338 NV07 cross-cutting; generation logic không phải current DOCX proof.
- [PDF no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf):pp.10–11 NV05, payment/free-with-reason acceptance p.11; [PDF no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf):pp.10–11, tái sử dụng substantive comparison R01.
- [Ảnh charging p.11](../../evidence/qa/proposal-with-ev-charging/page-11.png):NV05 tail trước NV06, stored data/output/payment acceptance; p.10 chưa inspect, không full variant equivalence.

Không có source/version authoritative mới hoặc phê duyệt mức giá từ survey. Rendered QA là evidence đề cương, không test result ứng dụng.
