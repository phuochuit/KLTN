# NV02 — Thẻ, gói 30 ngày, bảng giá và thanh toán

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV06](nv06-vehicle-classification.md)

## Mục tiêu và nguồn

Quản lý quyền ra-vào theo thời hạn, tính phí nhất quán và đối soát được. Ban quản lý thao tác; gate staff chỉ xem trạng thái cần thiết. Yêu cầu nguồn: `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf` p.7; `../../evidence/qa/proposal-with-ev-charging/page-07.png` (trang 7/18, toàn thân NV02; heading ở p.6). Chưa chọn version authoritative.

Tài liệu tham chiếu phí: [PDF mức phí](../../evidence/pricing-reference/source.pdf), có tiêu đề “Kết quả tổng hợp mức phí khảo sát”. Nguồn gốc khảo sát, phương pháp, sự đồng thuận và tính đại diện chưa được xác minh; tiêu đề không chứng minh provenance hoặc phê duyệt giá.

## Điều kiện, inputs và outputs

- Xe/cư dân đã duyệt; xe thuộc căn hộ hợp lệ và không vượt giới hạn số xe theo chính sách. Giá trị quota chưa được chốt trong evidence.
- Input: card code, nhóm giá, ngày bắt đầu, kỳ 30 ngày, số tiền và phương thức thanh toán.
- Output: card/pass/payment nhất quán, lịch sử kỳ/thẻ/thu tiền và đối soát; Web liệt kê gói sắp hết hạn và thẻ đang khóa.

## Luồng chính và alternatives theo nguồn p.7

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
- Activation công nợ mâu thuẫn với NV01 p.6 “chỉ hoạt động sau thanh toán”; chưa có quyết định nghiệp vụ resolve.
- Kỳ **30 ngày** là yêu cầu nguồn, không tự đổi thành tháng lịch. Cách sinh validUntil ở UI/service gia hạn chưa audit.

## Actual code: thẻ và quyền miễn phí

Các references dưới đây thuộc `spring-web/src/main/java/vn/edu/parking/`.

| Symbol / lines | Quan sát tĩnh |
| --- | --- |
| `domain/ParkingCard.java:7–25` | `parking_cards`, unique `card_code`, length 30/not-null; vehicle ManyToOne; default ACTIVE, PER_VISIT; validFrom/validUntil/subscriptionFee. Không chứng minh global registration dedup/concurrent replacement policy |
| `ParkingCard.java:35,43–46` | Null passType đọc thành PER_VISIT. MONTHLY valid khi card ACTIVE, cả hai date có giá trị và ngày kiểm tra nằm **inclusive** giữa validFrom/validUntil |
| `service/ParkingService.java:470–473` | Blank/null card code → null; supplied unknown code → exception, không phải missing-card fallback |
| Cùng service `:55–77` | Known inactive card từ chối entry. Missing card hoặc known card khác vehicle có warning/per-visit; matching MONTHLY expired cũng có warning/per-visit. Không đồng nhất expired fallback với nguồn “không tự mở” |
| Cùng service `:463–467` | Miễn phí cần session.vehicle/card.vehicle IDs trùng và `isMonthlyValid` tại **ngày entry**; status ACTIVE vẫn được helper kiểm tra qua entity |
| Cùng service `:108–126` | Confirm tính fee và đóng COMPLETED; body không thể hiện payment confirmation như yêu cầu nguồn. Desktop/payment toàn luồng chưa xác minh |

Không suy luận “card còn hiệu lực lúc vào thì luôn miễn phí”: date dùng ngày vào nhưng entity còn kiểm tra **status hiện tại** và matching vehicle khi tính phí.

## Actual code: công thức phí theo lượt

Reference: `ParkingService.java:426–460`; đây là mô tả implementation, **không phải giá/chính sách được phê duyệt**.

1. `hasValidMonthlyPass` true → fee zero. Nếu không, tìm `PricingRule` theo session detected type; thiếu rule → lỗi.
2. Duration tính phút, tối thiểu 1. `midnightCount` là số chênh lệch ngày không âm, không phải duration 24h.
3. Xe `type.isCar()`: basePrice cho `max(1,baseHours)` giờ; vượt thì cộng extraBlockPrice theo `ceil(extraMinutes / (max(1,extraBlockHours)*60))`. Fee cuối là max(timeFee, overnightFee × midnightCount).
4. Loại không-car: nếu qua ngày hoặc duration ≥24h, overnightFee × ceil(minutes/1440).
5. Non-car còn lại: cùng ngày, entry ≥06:00 và exit ≤18:00 → basePrice; ngoài đó → nightPrice.

Default amount, taxonomy `isCar`, historical pricing snapshot, discount/rounding storage, database constraints và các edge-case tests chưa được xác minh. Không lấy mức giá khảo sát chưa kiểm chứng làm chuẩn.

## Source/code conflicts cần quyết định

| Chủ đề | Yêu cầu nguồn / actual observation |
| --- | --- |
| Expired entry | Nguồn không tự mở; service có per-visit warning fallback. Cần review cả UI và chính sách |
| Pass lúc ra | NV04 `../../evidence/qa/proposal-with-ev-charging/page-09.png`, Bước 6–7 kiểm pass còn hiệu lực, có thể fee zero hoặc configured surcharge; code eligibility date là entry và valid monthly trả zero |
| Payment/debt | Nguồn yêu cầu activation payment hoặc debt approval; renewal/payment service/forms chưa audit. NV05 p.11 payment-before-close khác confirm body chưa thấy check |
| History/refund | Nguồn giữ kỳ và history; `ParkingCard` có dates hiện tại không đủ để kết luận subsystem có/không có history |
| Vehicle group | Registered type lấy hồ sơ, visitor invalid type fallback MOTORBIKE; xem NV06. Không tự coi AI proposal taxonomy trùng enum code |

## Phụ thuộc và acceptance

NV01 identity/authorized vehicle; NV06 group mapping; NV03/NV04 dùng card/pass; NV05 fees; NV07 exceptions; NV08 reconciliation. Các đặc tả liên quan đã có canonical pages: [NV01](nv01-registration.md), [NV03](nv03-resident-entry.md), [NV04](nv04-resident-exit.md), [NV05](nv05-visitor-parking.md), [NV06](nv06-vehicle-classification.md), [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md); implementation evidence/conflicts xem [traceability](traceability.md).

Acceptance theo nguồn, **chưa chạy test**:

- Renewal/lock/replacement/refund-cancel không làm sai history; totals/detail khớp report.
- Unique active card ownership; invalid amount/overlap/quota có field errors.
- Trace được fee tới rule/parameters/duration/rounding/discount.
- Cần test boundaries validFrom/validUntil inclusive, ACTIVE vs inactive, entry-date vs exit-date, missing vs unknown vs mismatched card, day/night/overnight/extra blocks. Đây là kiểm chứng đề xuất cho code đã đọc, không test results.

`spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java:38–40` có H2 in-memory/create-drop test settings; assertions/pricing/renewal coverage chưa review, không test execution. Không dùng test datasource như proof production config.
