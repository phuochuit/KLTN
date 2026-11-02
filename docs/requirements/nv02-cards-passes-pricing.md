# NV02 — Thẻ, gói 30 ngày, bảng giá và thanh toán

## Implementation main `abd6183` — đối chiếu tĩnh

Main sinh mã thẻ từ plate chuẩn hóa, dùng PricingRule và ngày entry khi xét quyền miễn phí gói. Thu gói tạo SubscriptionPayment cùng PaymentTransaction/receipt, method/reference/collector/shift; chưa có controller-wide transaction bảo đảm toàn luồng card–subscription–ledger atomic. Confirm exit phí dương yêu cầu payment method/reference thích hợp và tạo receipt; vì vậy nhận xét cũ “không payment gate” không còn mô tả main. Ledger không chứng minh settlement bên ngoài, debt/refund hay lịch sử phiên bản giá. Xem [data dictionary](../architecture/data-dictionary.md), [payment contract](../api/contracts.md) và [testing](../testing/strategy.md).

**Phạm vi lịch sử:** các mục “Actual code”, source/code conflicts và test observation bên dưới giữ snapshot Security + WIP. Các công thức cần đối chiếu technical owners hiện hành, không dùng claims thiếu payment/receipt của snapshot làm main status. Quy trình và acceptance theo nguồn vẫn được bảo toàn.

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV06](nv06-vehicle-classification.md)

## Mục tiêu và phạm vi

Quản lý quyền ra-vào theo thời hạn, tính phí nhất quán và đối soát được. Ban quản lý thao tác; gate staff chỉ xem trạng thái cần thiết. Các yêu cầu dưới không đồng nghĩa toàn bộ đã được code/test đáp ứng; activation, expiry và settlement cần chốt tại [P02/P03/P05](../decisions/README.md).

Mức phí, công thức và toàn bộ ví dụ cần giữ đã được viết tại [Mức phí tham khảo](pricing-reference.md). Chưa xác nhận provenance/sampling/consent hoặc phê duyệt biểu phí; không cần mở bản PDF để hiểu cách tính.

## Điều kiện, inputs và outputs

- Xe/cư dân đã duyệt; xe thuộc căn hộ hợp lệ và không vượt giới hạn số xe theo chính sách. Giá trị quota chưa được chốt trong evidence.
- Input: card code, nhóm giá, ngày bắt đầu, kỳ 30 ngày, số tiền và phương thức thanh toán.
- Output: card/pass/payment nhất quán, lịch sử kỳ/thẻ/thu tiền và đối soát; Web liệt kê gói sắp hết hạn và thẻ đang khóa.

## Luồng chính và phương án thay thế

| Bước | Yêu cầu |
| --- | --- |
| 1 | Tạo PriceRule theo cư dân/khách, loại xe, dung tích từ giấy đăng ký, khung giờ, lượt/gói |
| 2 | Chọn đúng nhóm khi đăng ký; hiển thị công thức, tiền, ngày hiệu lực/hết hạn |
| 3 | Ghi payment; chỉ ACTIVE khi số tiền hợp lệ **hoặc có phê duyệt công nợ** |
| 4 | Kích hoạt card; một code chỉ thuộc một hồ sơ đang hoạt động cùng lúc |
| 5 | Gia hạn tạo kỳ mới nối tiếp kỳ cũ; không ghi đè history/không mất quyền chưa sử dụng |
| 6 | Tạm khóa do mất thẻ, vi phạm, sửa xe hoặc yêu cầu cư dân; lưu lý do/người thao tác |
| 7 | Thay card: vô hiệu card cũ trước card mới hoạt động |

Refund/cancel xuất hiện ở acceptance; quy trình, điều kiện và số tiền refund chưa có đủ evidence để đặc tả thêm. Không tự đặt thời hạn/quy tắc hoàn tiền.

## Exceptions, validations và trạng thái theo nguồn

- Pass overlap, card trùng, số tiền âm/sai hoặc xe vượt quota: từ chối và chỉ rõ field lỗi.
- Pass hết hạn tại cổng: không tự mở; hướng dẫn renewal hoặc guest policy.
- Fee result phải lưu PriceRule, parameters, duration, rounding, discount và final total.
- Lưu card history, pass periods, invoice/receipt, transactions, collector và reconciliation status.
- Activation công nợ khác NV01 “chỉ hoạt động sau thanh toán”; P02 chưa được quyết định.
- Kỳ **30 ngày** là yêu cầu nguồn, không tự đổi thành tháng lịch. `AdminController.saveCard` hiện default duration 30, cho 1–366 ngày và tính `validUntil = from + durationDays - 1`; chưa bảo đảm gia hạn nối tiếp/không overlap như nguồn.

## Actual code: thẻ và quyền miễn phí

Các references dưới đây thuộc `spring-web/src/main/java/vn/edu/parking/`.

Đối chiếu tĩnh main `abd6183` ngày 2026-10-24; không runtime/test PASS, không dùng backups/artifacts. [API](../api/contracts.md) sở hữu contract payment/receipt; [testing](../testing/strategy.md) tách test main khỏi executions feature lịch sử.

| Symbol / lines | Quan sát tĩnh |
| --- | --- |
| `domain/ParkingCard.java` | `parking_cards`, unique `card_code`; DAILY/MONTHLY là hai gói tạo mới, PER_VISIT chỉ đọc dữ liệu cũ; validFrom/validUntil/subscriptionFee |
| `ParkingCard.isPrepaidPackageValid` | DAILY hoặc MONTHLY hợp lệ khi card ACTIVE, cả hai date có giá trị và ngày kiểm tra nằm **inclusive** giữa validFrom/validUntil |
| `service/ParkingService.java` `enter`; `AdminController.saveCard` | Card được sinh/tra bằng plate chuẩn hóa; entry bỏ qua `cardCode` request. Không dùng helper `resolveCard` chưa được gọi ở đây để khẳng định supplied unknown card bị từ chối |
| Cùng service `enter` | Card tìm theo plate mà inactive → từ chối; thiếu/mismatched card với resident → warning/per-visit; matching MONTHLY expired cũng warning/per-visit nhưng tạo OPEN. Không đồng nhất fallback với nguồn “không tự mở” |
| Cùng service `hasValidPrepaidPackage` | Miễn phí cần session.vehicle/card.vehicle IDs trùng và gói DAILY/MONTHLY hợp lệ tại **ngày entry** |
| Cùng service `confirmExit`; `PaymentTransactionService` | Fee>0 bắt buộc method CASH/BANK_TRANSFER/POS, hai loại sau cần reference; ghi PaymentTransaction/receipt/collector và ca nếu có trong transaction nghiệp vụ. Fee0 không tạo exit payment. Operator input/PAID không chứng minh provider settlement hoặc reasoned waiver |
| `AdminController.saveCard` | MONTHLY lưu kỳ SubscriptionPayment pending rồi ghi PaymentTransaction, clear pending và link receipt; method/reference có kiểm tra. Handler không transaction bao toàn luồng: card/kỳ lưu trước lỗi payment vẫn là partial-failure gap. Renewal continuity/overlap/debt/refund chưa chứng minh |

Không suy luận “card còn hiệu lực lúc vào thì luôn miễn phí”: date dùng ngày vào nhưng entity còn kiểm tra **status hiện tại** và matching vehicle khi tính phí.

## Actual code: công thức phí theo lượt

Reference: [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) `calculateFee`/`hasValidPrepaidPackage`.

1. `hasValidPrepaidPackage` true → fee zero. Nếu không, tìm `PricingRule` theo session detected type và tính như khách vãng lai.
2. Duration tính phút, tối thiểu 1. `midnightCount` là số chênh lệch ngày không âm, không phải duration 24h.
3. Xe `type.isCar()`: basePrice cho `max(1,baseHours)` giờ; vượt thì cộng extraBlockPrice theo `ceil(extraMinutes / (max(1,extraBlockHours)*60))`. Fee cuối là max(timeFee, overnightFee × midnightCount).
4. Loại không-car: nếu qua ngày hoặc duration ≥24h, overnightFee × ceil(minutes/1440).
5. Non-car còn lại: cùng ngày, entry ≥06:00 và exit ≤18:00 → basePrice; ngoài đó → nightPrice.

Default amount, taxonomy `isCar`, historical pricing snapshot, discount/rounding storage, database constraints và các edge-case tests chưa được xác minh. Không lấy mức giá khảo sát chưa kiểm chứng làm chuẩn.

## Source/code conflicts cần quyết định

| Chủ đề | Yêu cầu nguồn / actual observation |
| --- | --- |
| Expired entry | Nguồn không tự mở; service có per-visit warning fallback. Cần review cả UI và chính sách |
| Pass lúc ra | NV04 kiểm pass còn hiệu lực, có thể fee zero hoặc configured surcharge; code eligibility date là entry và valid monthly trả zero; xem P03 |
| Payment/debt | Main có ledger/receipt/reconcile và positive-fee close payment check; không external settlement/debt approval hoặc miễn phí có lý do đầy đủ. Không giữ claim “confirm không payment gate” của feature làm trạng thái main |
| History/refund | Nguồn giữ kỳ và history; `ParkingCard` có dates hiện tại không đủ để kết luận subsystem có/không có history |
| Vehicle group | Registered type lấy hồ sơ, visitor invalid type fallback MOTORBIKE; xem NV06. Không tự coi AI proposal taxonomy trùng enum code |

## Phụ thuộc và acceptance

NV01 identity/authorized vehicle; NV06 group mapping; NV03/NV04 dùng card/pass; NV05 fees; NV07 exceptions; NV08 reconciliation. Các đặc tả liên quan đã có canonical pages: [NV01](nv01-registration.md), [NV03](nv03-resident-entry.md), [NV04](nv04-resident-exit.md), [NV05](nv05-visitor-parking.md), [NV06](nv06-vehicle-classification.md), [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md); implementation evidence/conflicts xem [traceability](traceability.md).

Acceptance theo nguồn, **chưa chạy test**:

- Renewal/lock/replacement/refund-cancel không làm sai history; totals/detail khớp report.
- Unique active card ownership; invalid amount/overlap/quota có field errors.
- Trace được fee tới rule/parameters/duration/rounding/discount.
- Cần test boundaries validFrom/validUntil inclusive, ACTIVE vs inactive, entry-date vs exit-date, missing vs unknown vs mismatched card, day/night/overnight/extra blocks. Đây là kiểm chứng đề xuất cho code đã đọc, không test results.

`ParkingFlowIntegrationTest` main dùng H2/create-drop, evidence required tắt: `completesEntryPreviewAndExitFlow` kiểm thiếu method giữ OPEN/zero payments rồi CASH close/receipt/one payment; `monthlyPassKeepsOwnerAndMakesVisitFeeZero` kiểm cả confirm zero/no exit payment. `surveyPricesAreTheDefaults` không chứng minh survey authority. `PaymentTransactionServiceTest`/`PaymentPageIntegrationTest` có collection/reconcile/fixture-report assertions. Đây là test hiện diện, chưa chạy trong đợt docs; executions feature lịch sử ở [testing strategy](../testing/strategy.md), không full renewal/settlement/production proof.
