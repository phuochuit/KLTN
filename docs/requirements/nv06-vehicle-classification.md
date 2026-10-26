# NV06 — Phân loại phương tiện và hai biến thể phạm vi

## Implementation main `abd6183` — đối chiếu tĩnh

Main giữ phân loại YOLO/OCR/heuristic và official type hồ sơ cho xe đăng ký; có ManualOverrideService lưu original/corrected plate/type, reason, actor và audit events. Không còn phù hợp khi dùng snapshot cũ để khẳng định chưa có correction-history source. Correction không chứng minh accuracy sáu nhóm hoặc mọi enum reason chạy được: FACE_FAILED mong FAIL khác upstream REJECT; blank plate không tạo vehicle evidence cho PLATE_NOT_DETECTED. Không có ChargingSession trong inventory20 entity; quyền/connector/sạc vẫn không được suy từ EV/slot. Xem [system](../architecture/system.md), [dictionary](../architecture/data-dictionary.md), [security](../security/overview.md).

**Phạm vi lịch sử:** quan sát code/test và câu “chưa có code/test correction” bên dưới thuộc snapshot trước, không current-main inventory. Hai biến thể A/B và acceptance theo nguồn được giữ nguyên, không tự chọn phạm vi sạc.

[Phạm vi nghiệp vụ](README.md) · [NV01](nv01-registration.md) · [NV02](nv02-cards-passes-pricing.md)

## Mục tiêu, tác nhân và trạng thái quyết định

Phân loại để áp đúng nhóm giá/quy định; AI đề xuất, nhân viên xác minh theo hồ sơ. Ban quản lý, gate staff và dịch vụ AI tham gia. **P01 chưa chốt có quản lý sạc hay không**; hai phương án dưới được bảo toàn nhưng không tự coi đã triển khai hoặc đã duyệt.

| Phương án | Trạng thái quyết định | Phạm vi |
| --- | --- | --- |
| A — chỉ phân loại | Chờ xác nhận phạm vi cuối tại [P01](../decisions/README.md) | Loại chính thức, thông số từ giấy tờ, correction/lý do/lịch sử; không ChargingSession |
| B — phân loại và sạc | Chỉ áp dụng nếu được duyệt rõ | Thêm quyền/connector/thời hạn, eligibility/vị trí/phiên sạc và ranh giới mô phỏng |

Có xe điện trong taxonomy **không** đồng nghĩa có charging integration. Bản DOCX được đọc hiện mô tả A, còn tư liệu cũ có B; nội dung file không thay quyết định phạm vi được duyệt.

## Phần chung: inputs, data và workflow

- Nhóm AI nguồn: xe đạp, xe đạp điện, xe máy, xe máy điện, ô tô, ô tô điện. Đây không phải xác nhận enum/classifier thực tế hỗ trợ đủ sáu nhóm.
- Input hồ sơ: hãng/dòng, plate, loại, nhiên liệu, dung tích, công suất/thông số điện, ảnh và giấy đăng ký; realtime AI khi qua cổng.
- Không suy luận chính xác dung tích xi-lanh chỉ từ ảnh; lấy giấy đăng ký và nhân viên xác minh.

| Bước chung | Quy tắc yêu cầu |
| --- | --- |
| Đăng ký 1 | AI đề xuất nhóm từ ảnh; nhân viên đối chiếu giấy tờ và xác nhận giá trị chính thức |
| Đăng ký 2 | Map nhóm chính thức tới PriceRule và chính sách số xe/căn hộ |
| Tại cổng | So realtime AI với registry; lệch vượt ngưỡng tạo cảnh báo nhưng không tự sửa hồ sơ |
| AI sai | Nhân viên chọn theo giấy tờ; lưu cả dự đoán và giá trị xác minh phục vụ đánh giá/truy vết |

Precondition chung là có dữ liệu cần thiết để xác minh/map nhóm. Không có numeric thresholds hay quota được chứng minh trong nguồn đã ghi; không tự đặt thêm. Thiếu giấy tờ/threshold cần review hoặc NV07, chưa đủ evidence để invent exception-specific state.

Output/data: prediction, confidence, official type và verifier; sử dụng official group cho chính sách/giá, không xóa dự đoán khi manual correction. Mọi quyết định khác AI phải truy vết được.

## Phần riêng A — lý do xác minh khác AI

Mỗi lần phân loại nhân viên xác nhận khác dự đoán AI phải lưu **lý do và lịch sử truy vết**, bên cạnh dự đoán và giá trị xác minh. Đây là yêu cầu đã ghi nhận; chưa có code/test chứng minh correction history đầy đủ.

## Phần riêng B — quyền và phiên sạc

Đây là **yêu cầu nguồn**, không phải feature đã triển khai.

### Inputs, preconditions và luồng

1. Đăng ký xe điện lưu quyền sạc, allowed connector type và thời hạn quyền; NV01 chỉ cấp quyền khi phương án B được duyệt.
2. Kiểm xe đã đăng ký, pass/right còn hiệu lực, không bị khóa.
3. Kiểm loại vị trí, tương thích và còn chỗ; MVP có thể mô phỏng vị trí.
4. Tạo `ChargingSession` với xe, cư dân, vị trí, start time và người xác nhận.
5. Kết thúc lưu end time/status; chỉ ghi điện năng nếu có hardware data đáng tin cậy.

Không tự đặt enum state, đơn vị điện năng, giá sạc, protocol/hardware driver hoặc auto-start behavior: nguồn chưa cung cấp đủ.

### Alternatives, exceptions và outputs

- Xe xăng vào khu sạc, xe điện chưa đăng ký/hết quyền hoặc vị trí đầy: từ chối và cảnh báo.
- AI sai: nhân viên xác minh theo giấy tờ, lưu predicted và verified type.
- MVP mô phỏng vị trí là alternative được nguồn cho phép; không được quảng cáo như điều khiển sạc thật.
- Lưu rights và charging-session history bên cạnh classification evidence; chỉ xe điện hợp lệ được tạo phiên.
- Nếu duyệt B, NV08 cần dashboard vị trí/phiên sạc. Không tự thêm yêu cầu này vào phạm vi A.

## Quan sát code: không thay thế biến thể yêu cầu

| Source / symbol | Actual observation và giới hạn |
| --- | --- |
| [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) `resolveVehicleType`/`parseDetectedType` | Registered vehicle dùng `vehicle.getVehicleType()`; visitor parse enum upper-case, blank/invalid → null rồi MOTORBIKE fallback |
| Cùng service `calculateFee` | Fee chọn `PricingRule` theo session detected type; registered type được helper lấy từ registry. Không six-class AI evaluation/price-policy acceptance |
| Cùng service `enter` | Entry body không thấy reject type mismatch riêng; không kết luận toàn hệ thống bỏ check hoặc đã có correction-history policy |
| `anpr-service/app/main.py:142–206` | Recognition image/video routes indexed; classifier/model/settings/body/tests chưa đủ coverage; không accuracy/support-six-types claim |
| `ParkingService` slot operations/`assignSlot` | Slot business code được đọc, không chứng minh slot là charging station hoặc có connector semantics |

Chưa có evidence xác nhận `ChargingSession` entity, quyền sạc được persist, charging API hoặc hardware integration. Không đổi “chưa xác minh” thành tuyên bố toàn repo không có; cũng không coi source entity name là implemented schema.

## Acceptance và phụ thuộc

**Chung:** xác minh type theo giấy tờ, map giá/quota nhất quán, AI correction có evidence/verifier và không tự overwrite registry. Cần kiểm chứng taxonomy mapping và manual mismatch branch.

**Riêng A:** mọi phân loại nhân viên xác nhận khác dự đoán AI có lý do và lịch sử; giữ cả predicted và verified type. Đây là acceptance yêu cầu, chưa phải kết quả test.

**Riêng B:** valid EV mới tạo phiên; mọi decision khác AI trace được; simulation không quảng cáo điều khiển thật. Expired/blocked/incompatible/full position cần test nếu B được chọn; chưa có kết quả chạy.

NV01 cung cấp hồ sơ và authorization; NV02 mapping/validity/payment; NV03/NV04 so tại gate; NV07 manual/failures; NV08 dashboard/report. Chưa có approval chọn A/B hoặc resolve chính sách quyền sạc. Survey giá/giấy tờ và current-DOCX authority vẫn chưa xác minh.

Test evidence: [testing strategy](../testing/strategy.md) ghi recognizer helpers/internal-auth và Spring fee/default-price subsets; không six-class benchmark, charging/hardware hoặc site accuracy result. Trang này bảo toàn hai nhánh, không tự chọn phương án.
