# NV07 — Ngoại lệ, xử lý thủ công và phục hồi

[Phạm vi nghiệp vụ](README.md) · [Traceability](traceability.md) · [Cư dân vào](nv03-resident-entry.md) · [Cư dân ra](nv04-resident-exit.md) · [Khách](nv05-visitor-parking.md) · [Báo cáo](nv08-reporting.md)

## Mục tiêu, tác nhân và nguồn

**Yêu cầu nguồn:** duy trì an toàn khi AI không chắc chắn hoặc hạ tầng lỗi; đóng sự cố hoặc chuyển Ban quản lý, truy vết mọi mở cổng thủ công. Nhân viên trạm gác kiểm tra evidence, hướng dẫn chụp lại và quyết định trong phạm vi được cấp; Ban quản lý duyệt trường hợp nghiêm trọng. Người lái hợp tác xác minh; Desktop/API/AI/camera cung cấp kết quả và thành phần lỗi, không tự cấp quyền dự phòng.

Nguồn: [generator](../../tools/fill_thesis_proposal_15_20_pages.py):316–338; [PDF no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf) pp.11–12; [PDF no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf) pp.11–12; [ảnh charging p.12](../../evidence/qa/proposal-with-ev-charging/page-12.png) trigger/quality/Bước 1–7/outage/privacy và [p.13](../../evidence/qa/proposal-with-ev-charging/page-13.png) data/output/acceptance. PDF/image evidence tái sử dụng R01, không đọc lại toàn QA. R01 không ghi conflict substantive mới trong nhóm NV07 đã so, **không** chứng minh full textual equivalence hoặc DOCX authority.

Phê duyệt tài liệu không duyệt quyền override/threshold/offline policy mới. Code-observed, claims, test-present và unknowns dưới đây tách khỏi yêu cầu nguồn; không có kết quả chạy ứng dụng/tests.

## Điều kiện trước, inputs, triggers và outputs

- Kích hoạt khi ảnh dưới ngưỡng, kết quả mâu thuẫn, card/pass bất thường, API/AI/camera mất kết nối hoặc không tìm thấy lượt.
- Bao gồm sai/không thấy biển; mặt không khớp/nhiều mặt/che mặt; người không được quyền; thẻ mất/unknown/inactive/mismatched; pass hết hạn; AI type khác registry; OPEN trùng, không thấy OPEN hoặc lựa chọn lượt mâu thuẫn. Chi tiết nghiệp vụ gốc tại NV01–NV06, không tạo mã NV mới.
- Input: ảnh vào/hồ sơ/realtime/giấy tờ cần thiết, AI scores/quality, plate/card/pass/member/type, lượt và history, retry count, thành phần lỗi/thời gian; không lấy kết quả cũ làm xác thực mới.
- Trước manual/offline phải xác định nhân viên có quyền nào và giới hạn dự phòng; nguồn chưa nêu role matrix, số retry, thời gian timeout hay mức nghiêm trọng cụ thể, giữ chưa chốt thay vì tự đặt.
- Output: sự cố có Alert/evidence/quyết định/người/lý do/thời gian; giữ cổng đóng, cho qua có thẩm quyền hoặc chuyển quản lý; recovery/resync không nhân đôi lượt/thanh toán. Không coi Alert là enum actual đã xác minh.

## Luồng chính theo nguồn

| Bước | Yêu cầu |
| --- | --- |
| 1 | Hiển thị nguyên nhân cụ thể: tối/cháy/mờ/không thấy biển/nhiều mặt/che mặt |
| 2 | Chọn khung tốt; có thể cân bằng sáng/tương phản/deskew, OCR số khung giới hạn |
| 3 | Hướng dẫn dừng đúng vị trí, bỏ vật che hợp lệ, chụp lại |
| 4 | Hết số lần cấu hình thì dừng tự động, tạo Alert, giữ barrier đóng |
| 5 | Xem ảnh vào/hồ sơ/thẻ/quan hệ hộ/giấy tờ theo quy trình khảo sát; quyền theo từng xe, không chỉ cùng hộ |
| 6 | CHO QUA hoặc TỪ CHỐI, bắt buộc lý do và xác nhận lại |
| 7 | Lưu quyết định thủ công riêng, không đổi AI thành ĐẠT để che sai lệch |

Quality metrics nguồn: sáng, tương phản, nét, góc, kích thước vùng biển/mặt, số mặt, che khuất. Giải pháp camera nguồn: WDR/HDR/IR, đèn chống ngược sáng, camera biển/mặt riêng, khoảng cách/tốc độ cố định, góc phụ; đây là yêu cầu/đề xuất, không danh mục thiết bị đã lắp.

## Alternatives và recovery theo nguồn

| Trường hợp | Hành vi yêu cầu / giới hạn quyết định |
| --- | --- |
| API mất kết nối | Lưu tạm event có mã idempotency; chỉ thao tác theo quyền dự phòng; có kết nối thì resync và phát hiện trùng |
| AI mất kết nối | Không confidence giả; manual có evidence hoặc tạm ngừng làn theo chính sách |
| Camera lỗi | Camera dự phòng/ảnh thủ công; ghi thiết bị lỗi và thời gian gián đoạn |
| Plate/face/type sai | Retry/đối chiếu hồ sơ rồi manual theo quyền; không tự sửa hồ sơ hoặc coi cùng hộ là đủ quyền |
| Card/pass lỗi | Không tự mở theo yêu cầu NV02/NV03; renewal/guest policy cần quyết định. Warning/per-visit code là discrepancy, không approved override |
| Không thấy hoặc có OPEN mâu thuẫn | Kiểm history/time/ảnh; NV04/NV05 yêu cầu biên bản/chuyển quản lý nếu không tìm được. Không đoán giờ vào/thu phí tùy ý |
| Mất thẻ/người khác lấy xe khách | Xác minh quyền sở hữu/ảnh, Alert và manual; phí mất thẻ dựa khảo sát chưa xác minh, không đặt mức phạt |

### Retry, resync và lỗi một phần

Nguồn :330,336 yêu cầu event idempotency và không duplicate session/payment; NV03/NV04 yêu cầu không tạo/đóng hai lần. Do đó **tiêu chí recovery cần kiểm chứng**, không phải quy trình code đã có:

- Khi client mất response sau request, phải phân biệt “chưa gửi”, “server đã xử lý” và “chưa rõ kết quả” bằng dữ liệu đáng tin trước gửi lại/thu lại/mở lại. Cách tra event và schema replay/ack chưa được nguồn chi tiết hóa hoặc code xác minh.
- Partial failure giữa media–DB–slot–barrier hay giữa card–payment phải đối chiếu bản ghi đã lưu, giữ liên kết evidence, chuyển người có quyền khi chưa xác định trạng thái. Không tuyên bố một transaction DB hoàn tác được file hoặc thao tác cổng.
- Resync phải phát hiện duplicate entry/exit/payment, không tạo lượt mới để che lỗi hoặc overwrite AI/manual history. Thuật toán conflict resolution, thứ tự replay, retry/backoff, lưu bền queue và quyền xử lý xung đột còn mở.
- Không xóa evidence/đổi thời gian hoặc xác nhận thanh toán chưa có chứng cứ nhằm hoàn tất recovery. Đây là ràng buộc nhất quán từ yêu cầu truy vết/không duplicate, không mô tả API sửa giao dịch đã tồn tại.

## Dữ liệu, audit, authorization và privacy

Theo nguồn lưu Alert, ảnh, quality result, số lần thử, thành phần lỗi, người quyết định/lý do/thời gian. Mỗi manual opening phải truy vết, quyết định manual riêng với AI; trường nghiêm trọng chuyển Ban quản lý. Yêu cầu bảo vệ ảnh: phân quyền, mã hóa truyền/lưu, giới hạn tải, retention/xóa, dữ liệu giả/làm mờ demo.

Trạng thái nghiệp vụ mô tả sự cố đang xử lý → đóng hoặc chuyển cấp; nguồn chưa cung cấp enum/schema hay SLA. Session source `OPEN/CLOSED` và code `OPEN/COMPLETED` giữ riêng tại NV03/NV04, không invent trạng thái offline/pending của entity. Dữ liệu resync cần liên kết event với lượt/thanh toán gốc; schema cụ thể chưa xác minh.

## Quan sát code, claims và implementation gaps

| Evidence / exact reference | Quan sát tĩnh, không phải fulfillment toàn NV07 |
| --- | --- |
| [MainForm](../../desktop-winform/MainForm.cs) | Low confidence can select UI manual/correction state; recognition and face requests now go through authenticated Spring APIs. Warning entry can still persist OPEN while UI does not auto-open |
| MainForm :86–87,386–387 | Nút mở/đóng trực tiếp và label/timer barrier 5 giây; không có audit call/reason record trong bodies này. Không thiết bị thật |
| [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) | Client face claims and exit override claims are rejected; entry override is limited to selected active authorized member without registration image and is audited. Guest exit requires PASS only when entry-face evidence exists |
| Parking service/controller; [ParkingSession](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingSession.java) | Entry/exit evidence is server-owned, operation-bound and single-use; resident exit face proof is supported but not universally required. OPEN warning, filesystem/DB atomicity and concurrent session completion are not fully verified |
| [ANPR main.py](../../anpr-service/app/main.py) | Processing routes require internal Bearer; face compare/camera handlers calculate liveness but do not gate PASS. No proof of anti-spoof effectiveness or live service behavior |
| [ApiResponseReader](../../desktop-winform/ApiResponseReader.cs):8–37,47–74 | Non-success/empty/HTML/JSON lỗi → exception/thông báo kiểm tra/restart; không queue/resync trong helper này |
| [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) | Web sessions and Desktop JWT APIs use separate chains; gate and evidence routes are protected. H2 tests do not prove deployed TLS/proxy/session configuration |
| [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java):635–675 | Save monthly card rồi SubscriptionPayment; đoạn đã đọc không annotation transaction trên method, không thể kết luận atomic toàn luồng/card-payment rollback |
| [PROJECT_GAPS](../../evidence/archive/PROJECT_GAPS.md):3–12 | Deployment gaps là claims tác giả, không independent runtime audit; không dùng claim thay evidence implementation |

Không có direct evidence trong phạm vi đã đọc cho durable offline queue/resync, event idempotency, log mọi manual mở cổng, replay financial transaction, per-lane locking hoặc hệ quyền dự phòng. Ghi **Not verified**, không tuyên bố toàn repo chắc chắn không có. Annotation transactional/precheck OPEN không chứng minh concurrency/rollback filesystem.

## Phụ thuộc và acceptance

NV01/02 cung cấp hồ sơ/quyền/card/pass; NV03/04/05 nguồn sự cố session/driver/payment; NV06 classification correction và charging exceptions theo variant; NV08 dùng evidence/audit/reconciliation. Không tự áp chính sách charging khi chưa chọn authority.

**Acceptance theo nguồn và tiêu chí recovery cần kiểm chứng, chưa tests executed:**

- Lý do quality/failure rõ; retry hữu hạn theo cấu hình, hết retry giữ cổng đóng/Alert, không confidence giả.
- Manual có quyền, lý do, operator/time/evidence và xác nhận; AI/manual không bị gộp thành PASS giả; không đường tắt mở cổng không log.
- Missing/conflicting session/card/pass/member/type không bị bỏ qua để tự mở/thu tùy ý; escalation theo trách nhiệm.
- Offline/resync không duplicate session/confirmation/payment; lost-response/partial-failure cases giữ dữ liệu nhất quán và được đối chiếu, không claim đã giải quyết khi trạng thái chưa rõ.
- Evidence truy cập theo quyền/retention; outage duration/component và quyết định chuyển cấp/đóng truy vết được.

**Test hiện diện, không kết quả chạy:** [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java):114–126 test guest false face flag → preview 400 và true → confirm COMPLETED. :51–72 flow tuần tự; không assert offline/manual-reason/audit/concurrent/replay/partial rollback. ANPR `tests/test_recognizer.py`/demo `tests/test_decision.py` đã index R01, assertions recovery chưa review; không dùng như integrated recovery PASS.

## Câu hỏi chưa chốt

Ai được override/offline, case nào phải quản lý duyệt; thresholds/retry limits; event identity và lưu queue; transaction/media/slot compensation; manual log/Alert schema; retention/encryption; payment recovery và điều kiện mở cổng khi API down. DOCX/QA lineage/authority vẫn mở; R01 PARTIAL, không chuyển tên “final” thành quyết định được duyệt.
