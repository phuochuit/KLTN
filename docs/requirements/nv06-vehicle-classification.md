# NV06 — Phân loại phương tiện và hai biến thể phạm vi

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md)

## Mục tiêu, tác nhân và trạng thái quyết định

Phân loại để áp đúng nhóm giá/quy định; AI đề xuất, nhân viên xác minh theo hồ sơ. Ban quản lý, gate staff và dịch vụ AI là tác nhân nguồn. **Chưa duyệt bản authoritative.** Hai biến thể dưới đây cùng được bảo toàn, không chọn theo tên “final”.

| Biến thể | Evidence thực sự đọc | Phạm vi |
| --- | --- | --- |
| A — classification-only | `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf` p.11 và `../../evidence/qa/proposal-without-ev-charging/preview.pdf` p.11 | Classification, official type, document-derived engine specs, correction/traceability; không mô tả ChargingSession trong NV06 |
| B — classification + charging | `../../evidence/qa/proposal-with-ev-charging/page-11.png`–`page-12.png`, NV06 trên pp.11–12 | Thêm rights/connectors/validity, charging eligibility/position/session, simulation boundary |

Có xe điện trong taxonomy **không** đồng nghĩa có charging integration. Không chứng minh bản DOCX hiện tại tương ứng A hay B.

## Phần chung: inputs, data và workflow

- Nhóm AI nguồn: xe đạp, xe đạp điện, xe máy, xe máy điện, ô tô, ô tô điện. Đây không phải xác nhận enum/classifier thực tế hỗ trợ đủ sáu nhóm.
- Input hồ sơ: hãng/dòng, plate, loại, nhiên liệu, dung tích, công suất/thông số điện, ảnh và giấy đăng ký; realtime AI khi qua cổng.
- Không suy luận chính xác dung tích xi-lanh chỉ từ ảnh; lấy giấy đăng ký và nhân viên xác minh.

| Bước chung | Rule theo nguồn A p.11 và B p.11 |
| --- | --- |
| Đăng ký 1 | AI đề xuất nhóm từ ảnh; nhân viên đối chiếu giấy tờ và xác nhận giá trị chính thức |
| Đăng ký 2 | Map nhóm chính thức tới PriceRule và chính sách số xe/căn hộ |
| Tại cổng | So realtime AI với registry; lệch vượt ngưỡng tạo cảnh báo nhưng không tự sửa hồ sơ |
| AI sai | Nhân viên chọn theo giấy tờ; lưu cả dự đoán và giá trị xác minh phục vụ đánh giá/truy vết |

Precondition chung là có dữ liệu cần thiết để xác minh/map nhóm. Không có numeric thresholds hay quota được chứng minh trong nguồn đã ghi; không tự đặt thêm. Thiếu giấy tờ/threshold cần review hoặc NV07, chưa đủ evidence để invent exception-specific state.

Output/data: prediction, confidence, official type và verifier; sử dụng official group cho chính sách/giá, không xóa dự đoán khi manual correction. Mọi quyết định khác AI phải trace được (B p.12; source A cùng nhóm traceability đã ghi).

## Phần riêng A — lý do xác minh khác AI

Theo biến thể A, mỗi lần phân loại nhân viên xác nhận khác dự đoán AI phải lưu **lý do và lịch sử truy vết**, bên cạnh dự đoán và giá trị xác minh. Nguồn: `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf`, trang 11. Đây là yêu cầu nguồn; chưa có code evidence xác nhận việc lưu lý do/lịch sử này đã triển khai.

## Phần riêng B — quyền và phiên sạc

Đây là **yêu cầu nguồn**, không phải feature đã triển khai.

### Inputs, preconditions và luồng

1. Đăng ký xe điện lưu quyền sạc, allowed connector type và thời hạn quyền (`page-11.png`, Đăng ký 3). NV01 Bước 9 `page-06.png` cũng hiển thị quyền sạc.
2. Kiểm xe đã đăng ký, pass/right còn hiệu lực, không bị khóa (Bước sạc 1).
3. Kiểm loại vị trí, tương thích và còn chỗ; MVP có thể mô phỏng vị trí (Bước sạc 2).
4. Tạo `ChargingSession` với xe, cư dân, vị trí, start time và người xác nhận (Bước sạc 3).
5. Kết thúc lưu end time/status; chỉ ghi điện năng nếu có hardware data đáng tin cậy (Bước sạc 4).

Không tự đặt enum state, đơn vị điện năng, giá sạc, protocol/hardware driver hoặc auto-start behavior: nguồn chưa cung cấp đủ.

### Alternatives, exceptions và outputs

- Xe xăng vào khu sạc, xe điện chưa đăng ký/hết quyền hoặc vị trí đầy: từ chối và cảnh báo (`page-11.png`, Ngoại lệ 1).
- AI sai: nhân viên xác minh theo giấy tờ, lưu predicted và verified type (Ngoại lệ 2).
- MVP mô phỏng vị trí là alternative được nguồn cho phép; không được quảng cáo như điều khiển sạc thật.
- Lưu rights và charging-session history bên cạnh classification evidence; chỉ xe điện hợp lệ được tạo phiên (`page-12.png`, dữ liệu/kết quả).
- Dashboard có vị trí sạc trong NV08 `page-13.png`; đó là cross-NV scope B. Không đủ evidence baseline chi tiết để kết luận từng dòng dashboard đã bỏ ở A.

## Quan sát code: không thay thế biến thể yêu cầu

| Source / symbol | Actual observation và giới hạn |
| --- | --- |
| `spring-web/src/main/java/vn/edu/parking/service/ParkingService.java:482–491` | Registered vehicle dùng `vehicle.getVehicleType()`; visitor parse enum upper-case, blank/invalid → null rồi MOTORBIKE fallback |
| Cùng service `:426–430` | Fee chọn `PricingRule` theo session detected type; registered type được helper lấy từ registry. Chưa audit full PricingRule/taxonomy |
| Cùng service `:40–94` | Entry body đã đọc không thấy reject type mismatch riêng; chưa audit đầy đủ Desktop decision path, không kết luận toàn hệ thống bỏ check |
| `anpr-service/app/main.py:142–206` | Recognition image/video routes indexed; classifier/model/settings/body/tests chưa đủ coverage; không accuracy/support-six-types claim |
| `ParkingService.java:129–380` | Slot business code được đọc, không chứng minh slot là charging station hoặc có connector semantics |

Chưa có evidence xác nhận `ChargingSession` entity, quyền sạc được persist, charging API hoặc hardware integration. Không đổi “chưa xác minh” thành tuyên bố toàn repo không có; cũng không coi source entity name là implemented schema.

## Acceptance và phụ thuộc

**Chung:** xác minh type theo giấy tờ, map giá/quota nhất quán, AI correction có evidence/verifier và không tự overwrite registry. Cần kiểm chứng taxonomy mapping và manual mismatch branch.

**Riêng A (classification-only):** mọi phân loại nhân viên xác nhận khác dự đoán AI có lý do được lưu và lịch sử truy vết; giữ cả predicted và verified type. Nguồn: `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf`, trang 11. Đây là acceptance theo nguồn, chưa phải kết quả test hoặc xác nhận implementation.

**Riêng B (p.12):** valid EV mới tạo phiên; mọi decision khác AI trace được; simulation không quảng cáo điều khiển thật. Các scenario expired/blocked/incompatible/full position cần test nếu B được chọn; đây không phải test đã thực hiện.

NV01 cung cấp hồ sơ và authorization; NV02 mapping/validity/payment; NV03/NV04 so tại gate; NV07 manual/failures; NV08 dashboard/report. Chưa có approval chọn A/B hoặc resolve chính sách quyền sạc. Survey giá/giấy tờ và current-DOCX authority vẫn chưa xác minh.

Test evidence: các file test Spring/ANPR đã được index trong audit, nhưng taxonomy/charging assertions chưa được kiểm tra; không chạy tests cho R02, không hardware test hoặc metrics result. Specification này chỉ bảo toàn hai nhánh yêu cầu và actual observations.
