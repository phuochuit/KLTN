# Tổng quan hệ thống bãi đỗ xe ANPR

[Mục lục](README.md) · [Phạm vi nghiệp vụ](requirements/README.md) · [Trạng thái](implementation-status.md) · [Kiến trúc](architecture/system.md)

## Mục tiêu và phạm vi

Mục tiêu đề tài là quản lý hồ sơ hộ/người/xe, quyền theo xe, thẻ/gói/giá, lượt vào-ra và evidence; giảm nhập tay bằng nhận plate/face/type; hỗ trợ điều phối ô đỗ, xử lý ngoại lệ và báo cáo. Đây là mục tiêu/yêu cầu được ghi nhận, không khẳng định đã hoàn thiện mọi kiểm soát. Detailed rules thuộc NV01–NV08; actual behavior và giới hạn thuộc status/technical owners.

## Tác nhân và trách nhiệm

| Tác nhân | Trách nhiệm theo nghiệp vụ, không role enforcement đã kiểm chứng |
| --- | --- |
| Ban quản lý/quản trị | Duyệt hồ sơ, thẻ/gói/giá/hạn mức, ngoại lệ và đối soát/báo cáo |
| Nhân viên trạm gác | Kiểm vehicle/person/card/evidence, xác nhận lượt, chuyển trường hợp không chắc và manual có accountability |
| Cư dân/chủ xe/thành viên | Cung cấp hồ sơ/consent; chỉ dùng xe đã cấp quyền cho người đó; cùng hộ không tự có quyền mọi xe |
| Khách vãng lai | Gửi theo lượt, lưu evidence và thực hiện chính sách phí/ownership/thanh toán |
| Camera/reader/sensor/AI | Cung cấp observation/trigger; AI hỗ trợ, không thay authority; thiết bị thật phải tách khỏi mô phỏng |

## Chuỗi nghiệp vụ

1. **[NV01](requirements/nv01-registration.md)** đăng ký hộ–người–xe, ảnh/giấy tờ và authorized members → **[NV02](requirements/nv02-cards-passes-pricing.md)** card/pass/validity/giá; **[NV06](requirements/nv06-vehicle-classification.md)** xác nhận official type từ giấy tờ và AI proposal.
2. **[NV03](requirements/nv03-resident-entry.md)** cư dân vào: plate, người, card/pass/quyền và evidence → OPEN. Code có warning missing/mismatched card/expired pass vẫn có thể tạo OPEN; không mặc nhiên đã reject vì barrier UI chưa mở.
3. **[NV04](requirements/nv04-resident-exit.md)** cư dân ra: tìm OPEN, kiểm evidence và người lấy, preview/confirm phí. Yêu cầu cho phép người lấy khác người vào nếu có quyền; actual resident-exit mandatory face/rights chưa được chứng minh. Source CLOSED khác observed COMPLETED.
4. **[NV05](requirements/nv05-visitor-parking.md)** khách dùng cùng gate UI nhưng không resident enrollment; capture ảnh vào và compare lúc ra là yêu cầu/flow quan sát. Backend face gate có điều kiện khi entry-face tồn tại, client flag được dùng; không proof mọi visitor được xác minh hoặc đã trả tiền.
5. **[NV07](requirements/nv07-exceptions-recovery.md)** bao quanh các bước: ảnh kém, lệch dữ liệu, outage/retry/manual/privacy/recovery. Error message và manual flag không phải audit/role/offline queue hoặc idempotency guarantee.
6. **[NV08](requirements/nv08-reporting.md)** dùng hồ sơ/session/payment records cho history/dashboard/doanh thu. Observed sums không chứng minh settlement hoặc Payment–Session–PriceRule reconciliation.

Cards hỗ trợ per-visit/monthly và form validity; price/pass-expiry/payment-debt policy còn conflict. NV06 classification-only A và charging-inclusive B chưa được chọn; electric fuel/type hay parking slot không là charging integration. Supporting features còn có lookup, QR autofill, health, slots/borrowing/dispatch/batch và standalone demo; xem [scope map](requirements/README.md).

## Thành phần và integration boundaries quan sát

| Thành phần | Quan hệ thực tế theo static source và giới hạn |
| --- | --- |
| Spring Web / browser | Thymeleaf pages và REST nghiệp vụ; service/repositories ghi database, filesystem media; actual datasource/profile runtime chưa xác minh |
| Desktop WinForms | Gọi Spring lookup/entry/exit/slots; gọi trực tiếp ANPR image/video/face/capture. Base URL local mặc định; client flag/manual không signed AI evidence |
| ANPR FastAPI | YOLO/EasyOCR plate/type, YuNet/SFace face; camera trên máy ANPR và tải reference ảnh từ Spring. Quality/readiness/model files không liveness/accuracy/integration PASS |
| Standalone face demo | Ba ảnh CCCD/registration/realtime qua API riêng; shared model fallback không chứng minh gate gọi demo hoặc integrated three-way identity |
| DB/media/barrier | DB và media có failure boundary riêng; transaction không bao gồm filesystem/barrier. Barrier WinForms là label/timer mô phỏng, không relay/PLC feedback |

Chi tiết và diagram ở [system](architecture/system.md), entities/constraints ở [database](architecture/database.md), method/payload/error/caller ở [contracts](api/contracts.md). Không suy ra Spring tự gọi AI từ mô tả thiết kế tổng quát; gate caller đã quan sát là Desktop → ANPR và Desktop → Spring.

## Bảo mật, vận hành và kiểm chứng

Observed [SecurityConfig](security/overview.md) cho public API/uploads/H2 và CSRF exclusions; form login không full role matrix. Liveness non-blocking/không được chứng minh, client flags, resident exit authorization, settlement, replay/offline và deployed protection còn open. Không mô tả chúng là end-to-end hoạt động.

[Operations](operations/setup-and-troubleshooting.md) giải thích startup order và H2/MySQL/launcher caveats; [user guide](operations/user-guide.md) ghi đúng tên UI và simulation. [Testing](testing/strategy.md) phân biệt existing assertions, proposed tests và thực thi; không test/app được chạy trong các slice tài liệu này. [Thesis requirements](thesis/requirements.md) tách minima học thuật khỏi project commitments. Survey và source authority chưa chốt; các kết luận phải xem [implementation status](implementation-status.md) trước khi demo/nghiệm thu.
