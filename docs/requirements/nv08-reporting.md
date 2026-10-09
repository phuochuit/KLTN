# NV08 — Tra cứu, thống kê, cảnh báo và báo cáo doanh thu

[Phạm vi nghiệp vụ](README.md) · [Traceability](traceability.md) · [Thẻ/gói/giá](nv02-cards-passes-pricing.md) · [Phân loại và hai biến thể](nv06-vehicle-classification.md) · [Ngoại lệ](nv07-exceptions-recovery.md)

## Mục tiêu, tác nhân và nguồn

**Yêu cầu nguồn:** Ban quản lý theo dõi vận hành, đối soát tiền và điều tra sự cố; số tổng/chi tiết khớp, drill-down tới đúng giao dịch. Quản trị/Ban quản lý xem theo quyền; trạm gác chỉ phạm vi được cấp, không mặc nhiên quyền tài chính/xem CCCD/xuất ảnh.

Nguồn: [generator](../../tools/fill_thesis_proposal_15_20_pages.py):340–357; [PDF no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf) p.13; [PDF no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf) heading p.12, body p.13; [ảnh charging p.13](../../evidence/qa/proposal-with-ev-charging/page-13.png) NV08. Tái dùng comparison R01, không exhaustive source audit. Charging dashboard conflict giữ riêng bên dưới. Approval viết tài liệu không phê duyệt version authoritative/chính sách kế toán mới.

## Điều kiện trước, inputs và outputs

- Theo nguồn: đăng nhập đúng quyền; dữ liệu lượt, thanh toán, cảnh báo đã ghi. Hồ sơ/card/pass/nhóm giá từ NV01/02/06 và evidence manual từ NV07 liên kết được.
- Input: kỳ báo cáo/filter, session/evidence/AI, payment/PriceRule/fee detail, căn hộ/xe/thẻ/người/cổng, alerts/manual/correction/cancel/waiver nếu có.
- Output: history/detail/dashboard/exception reports và doanh thu tổng–chi tiết trace được; export CSV/Excel/PDF **nếu đủ thời gian**. Dữ liệu thiếu/lệch thì không chốt, cảnh báo và chỉ bản ghi gây lệch.

## Luồng nghiệp vụ theo nguồn

| Bước | Thao tác / rule |
| --- | --- |
| 1 | Xác định quyền và mục đích xem báo cáo; hạn chế dữ liệu nhạy cảm |
| 2 | Chọn kỳ và filter: plate/card/apartment/resident-visitor/gate/type/status/alert/time range |
| 3 | Tra cứu lịch sử; xem ảnh vào-ra/AI/bảng đối chiếu/phí/payment/người thao tác/timeline |
| 4 | Dashboard vận hành: số xe/sức chứa/vào-ra/OPEN lâu/alerts chưa xử lý/API-AI-camera; charging vị trí theo variant |
| 5 | Dashboard đăng ký: thẻ-gói sắp hết hạn/xe theo hộ và loại/xe điện/hồ sơ chờ duyệt/ảnh cần chụp lại |
| 6 | Tổng hợp doanh thu theo ngày/tháng, cư dân-khách/type/PriceRule/gate/phương thức/nhân viên thu; loại giao dịch hủy/chưa thanh toán |
| 7 | Đối soát Payment–ParkingSession–PriceRule, so tổng với chi tiết; báo cáo ngoại lệ mất thẻ/manual/sai biển-mặt/ảnh kém/lượt không đóng/giảm-miễn/chênh lệch |
| 8 | Nếu thiếu/lệch, không chốt; Alert và link bản ghi cần xử lý. Đúng thì drill-down tới đúng giao dịch |
| 9 | Nếu có export, ghi filter/time/người xuất; log xem/xuất sensitive data, giới hạn ảnh/CCCD theo role |

Thứ tự này tổ chức các mục yêu cầu :345–356 để đọc nghiệp vụ, không nói nguồn có API “chốt báo cáo” hay enum trạng thái báo cáo. Chưa chốt timezone, ranh giới kỳ, cutoff và role matrix.

## Quy tắc dữ liệu, lineage và alternatives

- Nguồn dùng `Payment–ParkingSession–PriceRule`: fee tái tính được từ rule/parameters/duration/rounding/discount/final total (NV02); thu tiền gắn đúng lượt/collector/receipt (NV05). Không coi tên nguồn là entity actual.
- Unpaid/cancelled không tính vào revenue theo nguồn. Miễn phí phải có reason; giảm/miễn/manual/correction/chênh lệch phải xuất hiện trong ngoại lệ. Không gán zero fee là “đã thu” hoặc COMPLETED là “payment verified”.
- Refund/cancel/corrected transactions cần history/đối soát nhất quán (NV02 acceptance); source chưa đủ quy trình hạch toán, số tiền refund/correction, cutoff hoặc approval. Không invent auto-refund/report sửa giao dịch.
- Không có dữ liệu: phân biệt không phát sinh với thiếu dữ liệu; không chốt khi chưa chứng minh đủ/khớp. Mất API/lỗi báo cáo: không dùng số cũ như kỳ đã đối soát; xử lý NV07, cụ thể cơ chế code chưa xác minh.
- Occupancy hiện tại, số lượt vào/ra trong kỳ, tổng xe đăng ký và snapshot cuối kỳ là các ý nghĩa khác nhau; phải nêu rõ tiêu chí đếm, không đồng nhất vehicleCount với số xe đang đỗ.
- Privacy nguồn: role không cần không xem/tải ảnh/CCCD; log truy cập/xuất nhạy cảm. Dữ liệu giả/làm mờ demo theo NV07.

## Code observed: báo cáo và màn hình thực tế

References controller thuộc [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java).

| Entry point / exact lines | Quan sát / giới hạn |
| --- | --- |
| `dashboard:59–83`, GET `/` | Count households/vehicles/OPEN hiện tại, tổng hôm nay/tháng hiện tại và 8 sessions gần nhất. Visit = sum fee COMPLETED theo exitTime; subscription = sum amount theo paidAt |
| `sessions:725–728`, GET `/sessions`; [sessions.html](../../spring-web/src/main/resources/templates/sessions.html):1–2 | Toàn bộ history order entryTime desc; table plate vào-ra/type/card/time/status/fee. Không filters/ảnh/timeline/payment detail trong body/template này |
| `monthlyRevenue:731–755`, GET `/api/parking/revenue/monthly` | 12 tháng tới tháng hiện tại; year/month/label, visitRevenue/subscriptionRevenue/totalRevenue; không nhóm gate/collector/PriceRule |
| `revenueFilter:757–808` | type month/year/range, nếu thiếu tham số kỳ hoặc type không match thì default tháng hiện tại; type là request parameter bắt buộc. Range to +1 ngày 00:00; trả sessions/payments/sums và household/vehicle counts trước toTime, occupancy tại toTime |
| `monthlyRevenueDetail:810–835` | Chi tiết kỳ tháng: session COMPLETED exitTime desc và payment paidAt desc, cùng sum logic |
| `monthlyStats:837–858` | Counts trước cuối tháng/occupancy, tổng tháng; keys todayRevenue và monthRevenue đều dùng cùng tổng kỳ, không hai khoảng ngày/tháng độc lập |
| [dashboard.html](../../spring-web/src/main/resources/templates/dashboard.html):130–164,166–226 | JS gọi filter/monthly, render tổng + rows fees/payments; click chart gọi filter tháng và hiển thị chi tiết. Là static call path, chưa runtime UI/known-total test |

**Thời điểm/kỳ:** controller lấy COMPLETED theo **exitTime**, payment theo **paidAt**, không dùng entryTime làm kỳ thu. [ParkingSessionRepository](../../spring-web/src/main/java/vn/edu/parking/repository/ParkingSessionRepository.java):18,21–23 và [SubscriptionPaymentRepository](../../spring-web/src/main/java/vn/edu/parking/repository/SubscriptionPaymentRepository.java):9 dùng `Between`; code controller không thể hiện điều kiện `< to` tường minh. Boundary inclusion/double count đúng 00:00 giữa hai kỳ cần kiểm chứng; không gọi query hiện tại là half-open đã bảo đảm. `countPresentAt(end)` dùng entryTime < end và exit null hoặc exit >= end, không filter status; đây là snapshot, không count vào/ra trong kỳ.

`todayRevenue/monthRevenue` ở filter/stats dùng tổng cùng kỳ đã chọn; dashboard ban đầu :59–81 tính hai khoảng riêng. Không diễn giải keys API sau filter như luôn tổng ngày hiện tại. Validation from>to, invalid year/month, timezone, empty/missing-data handling chưa được chứng minh đầy đủ.

## Lineage thực tế, payment và pricing gaps

| Data / exact reference | Quan sát tĩnh và khác biệt nguồn |
| --- | --- |
| [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java):7–43; [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java):108–126,426–467 | Session có vehicle/card/type/time/fee/face flags; fee tính khi confirm rồi COMPLETED, không payment-status/PriceRule snapshot/reason/collector fields ở entity đã đọc. Không payment gate trong confirm body |
| [SubscriptionPayment](../../spring-web/src/main/java/vn/edu/parking/domain/SubscriptionPayment.java):8–22 | Record kỳ gói với ManyToOne card, periodStart/end, amount, paidAt. Không session/PriceRule/method/collector/cancel/unpaid state tại entity này |
| Controller `saveCard:635–675` | Lưu MONTHLY card tạo payment từ monthlyPrice theo type và paidAt now; input không actual amount/method/transaction reference. Default duration 30 nhưng chấp nhận 1–366 ngày, không proof thu thực tế/gói nối tiếp/không duplicate |
| [PricingRule](../../spring-web/src/main/java/vn/edu/parking/domain/PricingRule.java):7–25 | Unique vehicleType; base/night/overnight/monthly/extra-block amounts. Session/payment entity đã đọc không link rule version; không historical fee reproducibility guarantee |

Lineage code: session → vehicle/card/type, service chọn PricingRule theo type lúc tính; subscription payment → card → vehicle, dùng current rule monthlyPrice khi tạo. Không liên kết một Payment theo lượt–Session–PriceRule snapshot như mô hình nguồn. **Có reporting tổng phí + record payment gói không đồng nghĩa đã có functioning visitor-payment/reconciliation workflow.**

Revenue body chỉ lọc status COMPLETED cho sessions; không kiểm proof payment/waiver reason. Payment list không cancellation/status filter vì các fields đó không có tại entity này. Chưa xác minh corrected transaction/refund/unpaid exception reports, grouping theo cư dân/khách/loại, exports, Alerts và đối soát sâu; không kết luận missing toàn repo.

## Quyền truy cập và charging variant conflict

- [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) routes `/api/parking/revenue/**` through the Web session chain and requires MANAGEMENT; GATE_STAFF is denied and anonymous JSON receives 401 in integration tests. Revenue DTO minimization, sensitive-view/export audit and known-total correctness remain unverified.
- Generator :347 và ảnh charging p.13 yêu cầu dashboard có **vị trí sạc**; NV06 B có quyền/ChargingSession. NV06 A trong PDF no-EV p.11 chỉ classification. R01 chưa đủ comparison chi tiết để kết luận từng dòng NV08 no-EV đã xóa vị trí sạc; giữ conflict/câu hỏi scope, không tự chọn B hoặc tự bỏ yêu cầu này.
- ParkingSlot/xe điện trong enum không chứng minh station/ChargingSession/report charging hay hardware integration. Báo cáo code mới đọc không đủ evidence charging metrics.

## Phụ thuộc, acceptance và test evidence

NV01 hồ sơ/quyền; NV02 pricing/payment/history; NV03/04/05 session/evidence/fee; NV06 official group/charging theo variant; NV07 manual/alerts/recovery/privacy.

**Acceptance theo nguồn, chưa chạy tests:**

- Known-total dataset trả đúng tổng/chi tiết và đúng quyền; drill-down tới giao dịch đúng, fee trace rule/parameters/rounding/payment.
- Filter/grouping source thể hiện đúng ý nghĩa kỳ/occupancy/vào-ra; boundary/cutoff không duplicate hoặc bỏ giao dịch.
- Revenue loại unpaid/cancelled; manual/waiver/correction/refund/unknown mismatch trace history/reason/collector. Thiếu/lệch không chốt, Alert link offending records.
- Dashboard vận hành/đăng ký/exceptions và optional export giữ các trường source; scope charging không quảng cáo khi chưa có authority/implementation evidence.
- Sensitive evidence xem/xuất đúng quyền, log và privacy; không coi permitAll API là kiểm soát đã đáp ứng.

**Test hiện diện:** [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) `rendersAllAdminPages:106–112` assert authenticated GET pages 200, không assert report totals/filter/privacy/export; `completesEntryPreviewAndExitFlow:51–72` và `monthlyPassKeepsOwnerAndMakesVisitFeeZero:74–104` assert fee/state subset, không revenue reconciliation. :168–176 `surveyPricesAreTheDefaults` là assertions về giá, không proof survey backing hoặc known-total report. Không test execution cho tài liệu này, không reporting PASS từ rendered proposal.

## Câu hỏi còn mở

DOCX/QA authority/lineage; charging dashboard phạm vi nào; payment/waiver/debt/cancel/refund status và quyền; fee-rule snapshots; correct transaction history; role matrix/media log; date/timezone/boundary validation; export format/permission; liveness/idempotency và production data integrity. R01 PARTIAL, không đồng nhất publication với feature completion.
