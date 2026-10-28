# NV05 — Khách vãng lai vào, ra và thu phí

## Implementation main `abd6183` — đối chiếu tĩnh

Main khách có vehicle null; capture lúc vào optional, lưu tham chiếu FACE evidence nếu được cung cấp. Guest verify dùng EXIT vehicle evidence để tìm OPEN/reference ảnh vào, nhưng final consumption không giữ đầy đủ cross-link face proof tới chính OPEN đó. Stored entry face kích hoạt kiểm PASS trừ manual correction phù hợp; required=false/no-ID vẫn có legacy client-flag branch. Confirm phí dương tạo payment receipt, không chỉ đóng COMPLETED; UI có phương thức thu/tham chiếu. Manual reasons và token header khác feature snapshot. Xem [guest/payment contract](../api/contracts.md), [security](../security/overview.md), [data](../architecture/database.md).

**Phạm vi lịch sử:** các đoạn schema/code/payment gaps/test observations bên dưới là Security + WIP, kể cả nhận xét exit DTO không payment và chưa có receipt/replay. Không dùng chúng phủ nhận ledger/outbox trên main. Yêu cầu ownership, evidence, payment/miễn phí có lý do và acceptance theo nguồn vẫn giữ nguyên; chưa chứng minh liveness/settlement hoặc recovery live.

[Phạm vi nghiệp vụ](README.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md) · [Cư dân vào](nv03-resident-entry.md) · [Cư dân ra](nv04-resident-exit.md)

## Mục tiêu, tác nhân và nguồn

**Yêu cầu nguồn:** phục vụ khách không có hồ sơ cư dân nhưng đủ evidence đối chiếu lúc ra; khách nhận đúng xe, tiền thu gắn đúng lượt/báo cáo. Khách cung cấp ảnh/xe, giữ thẻ hoặc mã lượt và thanh toán. Nhân viên kiểm tra/sửa OCR có lý do, xác nhận dữ liệu/thu tiền/xử lý ngoại lệ; Ban quản lý kiểm tra log/ảnh khi chưa giải quyết. Desktop/API/AI/camera hỗ trợ, không tự quyết quyền sở hữu.

Trang này bảo toàn quy trình khách và payment-before-close; phí/miễn/mất thẻ/identity còn cần xác nhận theo [P04/P05/P08](../decisions/README.md). Yêu cầu, code và kết quả kiểm chứng được tách riêng; không chạy lại tests chỉ để viết docs.

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

**Điều kiện chấp nhận yêu cầu:** không đóng khi chưa xác nhận thanh toán **hoặc miễn phí có lý do**. Chưa chốt trường hợp được miễn, ai duyệt, mức giảm hay form; không tự đặt “khách quen”, thời gian grace hoặc miễn vì manualOverride. Zero fee của code không chứng minh exception approval/reason/payment.

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
| `enter`, `verifyDriver` | Require normalized plate, precheck duplicate OPEN; vehicle lookup null → guest, không yêu cầu resident member. Không có request selector KHÁCH; không kiểm ACTIVE resident theo precondition nguồn trong nhánh này |
| `resolveCard`, `enter` | Không thẻ được vào; supplied unknown code lỗi, known inactive lỗi. Guest vẫn có thể lưu known active card khác xe, body không cấp thẻ khách mới; session ID là output, không proof quy trình issuing visitor card |
| `resolveVehicleType` | Guest dùng enum parsed, invalid/blank fallback MOTORBIKE; không tự coi taxonomy code bằng sáu nhóm proposal |
| `ParkingApiController.entry` + `ParkingService.enter` | Guest capture remains optional. When supplied, Spring-owned CAPTURED evidence is bound and consumed with the resulting parking session; the client base64 is not authoritative |
| [MainForm](../../desktop-winform/MainForm.cs) → [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) | Capture and exit verification go through authenticated Spring. No direct Desktop face/camera-processing call to ANPR; no visitor enrollment |
| [ANPR main.py](../../anpr-service/app/main.py) | `/face/capture-camera` and `/face/verify-camera` require internal Bearer. Spring stores private evidence; liveness calculation does not establish blocking anti-spoof |
| [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) | Guest exit verification uses the private entry-face image for OPEN and recognition `evidenceId`; Spring derives the decision and binds face proof to operation/plate/session. PASS or a supported linked reasoned override is required when entry-face exists. Pairwise entry–camera comparison is not CCCD/standalone-demo three-image verification |
| `findOpenSession`; controller `openSessionId` | Helper ưu tiên OPEN theo plate, card fallback qua **card.vehicle plate**; REST tìm OPEN theo request plate và kiểm recognition evidence trước khi gọi helper. Không visitor-card-only/lost-card ownership recovery guarantee |
| `verifyGuestExit`, `validateExitOverride` | Guest **có stored entry-face path** cần matching recent PASS proof, trừ linked backend AI_REVIEW hoặc backend-recorded face service outage có reasoned override đúng quyền. Capture optional nên guest không entry-face không kích hoạt conditional check. Client scores/flags không thay proof |
| `previewExit`, `confirmExit`; controller `preview`/`confirm` | Preview tính tại now; confirm tính lại fee rồi COMPLETED/exit proof state và clear slot. Spring kiểm recognition gắn OPEN, consume recognition/face proof khi confirm và audit override khi có. Không payment-confirmation/receipt/waiver-reason gate; ảnh nằm trong gate evidence riêng, không exit DTO |
| [EntryRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/EntryRequest.java); [ExitRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/ExitRequest.java) | Plate @NotBlank; recognition/face evidence IDs và optional override.reason; legacy flags không phải proof. Exit không base64/payment/amount. Card-only blank plate không phải contract được validation cho phép |

Desktop `ConfirmExitAsync` yêu cầu preview cùng plate, nhưng server không đòi preview token; `BuildExitTab` không có payment form. Không xem “tổng thu” là payment đã thu. `BuildHeader`/`AutoOpenBarrier` có barrier label/manual buttons/timer 5 giây, không thiết bị thật hoặc audited override fulfillment.

ANPR camera routes require internal Bearer and receive registration bytes from Spring; liveness is calculated but does not gate the decision. Spring uses JWT/RBAC and private gate evidence. H2/fake tests do not prove deployed security, genuine face accuracy, liveness or payment policy.

## Pricing, payment và gaps

- Canonical formula tại [NV02](nv02-cards-passes-pricing.md), source code `calculateFee`: PricingRule theo session type; minimum 1 phút; car base/extra rounded blocks/max overnight, non-car overnight cycles hoặc day/night. Không suy ra bảng giá phân cư dân/khách hay lost-card surcharge đã implemented chỉ từ lời mô tả nguồn.
- `hasValidPrepaidPackage` cần session.vehicle/card.vehicle khớp nên khách và cư dân không có gói không được miễn phí. Manual override không tự biến thành miễn phí.
- Khách thiếu entry-face, request cờ faceVerified, payment-before-close, fee breakdown lưu bền, lịch sử giá/Payment/receipt/reconciliation/manual reasons và protected media là discrepancies/gaps cần kiểm tra/resolve, không claim toàn hệ thống hoàn toàn thiếu.
- Sequential confirm lặp thường không tìm OPEN; không replay idempotent success. New OPEN/concurrent confirm/duplicate payment chưa được chứng minh bằng precheck/annotation.
- Quy trình khách không dùng quyền hộ cư dân để thay ownership. Mức phí tham khảo và ví dụ nằm tại [pricing-reference](pricing-reference.md), không phải biểu phí được duyệt hoặc chứng cứ đã thu tiền.

## Phụ thuộc và acceptance

NV02 pricing/payment, NV06 nhóm xe, NV07 quality/manual/outage/lost-card, NV08 revenue/reconciliation. Dùng chung lifecycle với NV03/NV04 nhưng không cần NV01 enrollment để tạo lượt khách. [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md) có trang đặc tả riêng; implementation evidence và gaps xem [traceability](traceability.md).

**Acceptance theo nguồn, chưa chạy tests:**

- Khách không CCCD/enrollment dài hạn vẫn có plate/face/panorama/type/mã lượt và OPEN đủ evidence; OCR correction có reason.
- Exit so evidence vào-ra, đúng xe/người có quyền lấy; lost-card/mismatch/missing-session không tự mở hoặc thu tùy ý.
- Duration/PriceRule/components/rounding/final fee tái tính được; mỗi payment/receipt/collector/exception gắn đúng lượt và báo cáo.
- Không đóng trước payment confirmation hoặc free exception có reason; không invent eligible miễn/phạt.
- Retry/resend không duplicate session/payment; manual giữ kết quả AI riêng, evidence được bảo vệ; barrier mô tả đúng simulation.

**Test/execution evidence:** `guestFaceCaptureAndExitVerificationStayBehindSpringAndBindToOneParkingSession` exercises authenticated synthetic guest capture, entry binding, session-bound exit verification and evidence consumption; `clientFaceAndOverrideAssertionsCannotBypassGuestExitVerification` rejects client-supplied assertions. The 2026-10-09 full Spring suite passed 108/108 on H2/fake ANPR. This does not prove real AI/liveness, mandatory capture policy, payment, pricing or production behavior.

## Tài liệu liên quan

[Pricing](pricing-reference.md), [quyết định](../decisions/README.md), [API](../api/contracts.md), [testing](../testing/strategy.md). Chưa có phê duyệt mức giá hoặc kết quả khảo sát đại diện mới từ việc tổng hợp tài liệu.
