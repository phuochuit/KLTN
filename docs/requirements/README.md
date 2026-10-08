# Phạm vi nghiệp vụ quản lý bãi đỗ xe ANPR

## Cách đọc và giới hạn bằng chứng

Đây là bản đồ nghiệp vụ cho developer và AI agent, không phải chứng nhận hệ thống đã hoàn tất. Tám mã chính thức là NV01–NV08; các tính năng hỗ trợ bên dưới không tạo thêm mã NV.

| Nhãn | Ý nghĩa |
| --- | --- |
| Yêu cầu nguồn | Nội dung đề cương/QA được chỉ rõ phiên bản và trang; chưa mặc nhiên là yêu cầu authoritative |
| Quyết định được duyệt | Chỉ áp dụng khi có phê duyệt nghiệp vụ cụ thể; hiện chưa chốt bản NV06 hoặc các xung đột bên dưới |
| Quan sát code | Đọc tĩnh symbol/call path; không chứng minh toàn UI, concurrency hoặc runtime |
| Test hiện diện | Đã tìm thấy file test; không đồng nghĩa assertions đã review hoặc test đã chạy |
| Chưa xác minh | Không suy diễn thành “đã làm” hoặc “không có” |

Hai PDF no-EV đã được đọc; ảnh `../../evidence/qa/proposal-with-ev-charging` mới đọc trang 6–9, 11–13. Current DOCX, các QA khác và nhiều luồng UI/test chưa được audit đầy đủ. Không chọn phiên bản authoritative từ tên “final”.

## Miền dữ liệu và tác nhân

- **Căn hộ/hộ gia đình:** đơn vị đăng ký cư dân và xe; quan hệ cùng hộ không tự cấp quyền lái mọi xe. Proposal dùng `Apartment`, code lookup dùng `Household`/`FamilyMember`; không coi hai tên là schema tương đương đã xác minh.
- **Người, xe và quyền:** người đăng ký, chủ xe, thành viên được sử dụng từng xe; ảnh tham chiếu và ảnh realtime phục vụ kiểm tra người lái. Xe có biển số, nhóm chính thức và dữ liệu giấy tờ.
- **Thẻ/gói/giá:** `ParkingCard`, `PassType`, thời hạn và `PricingRule`; kỳ gói, thanh toán, công nợ và lịch sử là yêu cầu nguồn cần đối chiếu implementation.
- **Lượt gửi xe:** `ParkingSession` liên kết evidence, thời gian, xe/thẻ và phí. Nguồn dùng `OPEN`/`CLOSED`; code đã đọc dùng `OPEN`/`COMPLETED` khi xác nhận ra.
- **Vị trí đỗ:** `ParkingSlot`, xe được gán, lượt đang chiếm, cho mượn và override trạng thái; đây là tính năng code hỗ trợ quản lý bãi, không đồng nghĩa vị trí sạc.
- **Evidence/cảnh báo:** ảnh, kết quả AI, người xác nhận, ngoại lệ và timeline. `Alert`/audit/offline sync là các yêu cầu nguồn; độ bao phủ thực tế chưa xác minh.
- **Sạc:** quyền sạc và `ChargingSession` chỉ thuộc một biến thể yêu cầu; chưa chứng minh entity, API hoặc hardware integration đã triển khai.

| Tác nhân | Trách nhiệm theo nguồn / ranh giới |
| --- | --- |
| Ban quản lý/quản trị | Duyệt hồ sơ, thẻ/gói/giá; xử lý trường hợp nghiêm trọng; đối soát và báo cáo |
| Nhân viên trạm gác | Kiểm tra xe/người/thẻ, xác nhận vào-ra, xử lý thủ công có evidence/lý do; chỉ xem phạm vi được cấp |
| Cư dân/chủ xe/thành viên | Cung cấp hồ sơ, consent và sử dụng xe theo quyền cụ thể |
| Khách vãng lai | Gửi xe theo lượt, giữ evidence và thanh toán; không mặc nhiên được đăng ký như cư dân |
| Camera/đầu đọc/sensor | Cung cấp ảnh, mã thẻ, trigger; barrier và một số thiết bị được mô tả dưới dạng mô phỏng |
| Desktop/Spring/ANPR | Desktop phối hợp thao tác; Spring thực hiện nghiệp vụ/lưu dữ liệu; ANPR cung cấp nhận dạng. AI là hỗ trợ, không tự quyết định quyền |

## Tám nghiệp vụ và quan hệ

| NV | Nội dung và đầu ra chính | Phụ thuộc / tài liệu |
| --- | --- | --- |
| NV01 | Hồ sơ hộ-người-xe, ba đối chiếu ảnh, quyền theo xe, thẻ ban đầu | [Đăng ký](nv01-registration.md); dùng NV02/NV06, chuyển ngoại lệ NV07 |
| NV02 | Thẻ, gói 30 ngày, bảng giá, thanh toán/gia hạn và lịch sử | [Thẻ/gói/giá](nv02-cards-passes-pricing.md); dựa NV01/NV06; cấp điều kiện/phí cho NV03–NV05 |
| NV03 | Cư dân vào: plate/face/card/pass/driver quyền, tạo một `OPEN`, evidence và barrier mô phỏng | [Cư dân vào](nv03-resident-entry.md); dựa NV01/NV02/NV06; lỗi sang NV07 |
| NV04 | Cư dân ra: tìm `OPEN`, đối chiếu vào-ra, người lấy có quyền, phí và đóng lượt | [Cư dân ra](nv04-resident-exit.md); dựa NV03 và NV01/NV02/NV06; ngoại lệ NV07 |
| NV05 | Khách vào-ra không resident enrollment; evidence, đối chiếu, thu phí và payment-before-close | [Khách vãng lai](nv05-visitor-parking.md); NV02/NV06/NV07; doanh thu sang NV08 |
| NV06 | AI đề xuất phân loại, nhân viên xác minh; biến thể bổ sung quyền/phiên sạc | [Phân loại và hai biến thể](nv06-vehicle-classification.md); liên kết NV01/NV02/NV03/NV04/NV08 |
| NV07 | Ảnh kém, sai lệch, mất dịch vụ, retry/manual/recovery/privacy | [Ngoại lệ và phục hồi](nv07-exceptions-recovery.md); xuyên suốt NV01–NV06; evidence phục vụ NV08 |
| NV08 | Tra cứu/cảnh báo/dashboard/doanh thu; đối soát Payment–Session–PriceRule, quyền và known-total acceptance | [Báo cáo và thống kê](nv08-reporting.md); dữ liệu NV01–NV07 |

Không tạo link đến trang chưa tồn tại. Quy trình tổng quát: NV01 đăng ký → NV02 cấp điều kiện và NV06 nhóm xe → NV03/NV04 hoặc NV05 tạo/đóng lượt → NV08 tra cứu/đối soát; NV07 bao quanh mọi bước có ngoại lệ.

Tra cứu bằng chứng: [Traceability NV01–NV08 và các tính năng hỗ trợ](traceability.md).

## Tính năng hỗ trợ phải được giữ trong phạm vi

Các quan sát sau không đủ chứng nhận toàn luồng đã hoạt động:

| Tính năng | Evidence code và mức kiểm tra | Liên hệ |
| --- | --- | --- |
| Lookup cư dân/thẻ/thành viên/gói, ảnh vào của khách | `spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java:58–101` đã đọc | NV01–NV05 |
| Slots: danh sách, gán xe, cho mượn/hủy, override, release, dispatch session | Controller `:104–146`; `service/ParkingService.java:129–236,267–380` đã đọc; UI/tests chưa đủ | Hỗ trợ NV03/NV05 và quản trị bãi |
| Lượt/xe chưa gán và batch slot generation | Controller `:148–195`; service `:239–264` tạo tối đa 100 slot/lần, bỏ code tồn tại | Hỗ trợ quản trị, không bảo đảm scheduling/concurrency |
| Health/database metadata | Controller `:197–204` đã đọc; fallback `Unknown` khi lỗi | Vận hành; không runtime UP claim |
| Recognition ảnh/video, face verify/capture-camera | `anpr-service/app/main.py:142–206` mới indexed; xử lý/models/tests còn pending | NV01/NV03–NV07 |
| Admin CRUD, CCCD QR, media và cấu hình | `AdminController.java`, `CccdQrController.java`, `CccdQrService.java`, `ResidentImageStorage.java`, `WebConfig.java` mới indexed | Không coi QR autofill là CCCD-photo face matching |
| Web login/API/media access | `config/SecurityConfig.java:17–32` đã đọc: public paths/CSRF exclusions/in-memory ADMIN; chưa runtime security test | RBAC/privacy nguồn không mặc nhiên implemented |
| Demo ba ảnh độc lập | `face-verification-demo/app.py:28,207–223`, ANPR model fallback `app/face_engine.py:11` indexed | Dùng chung model resource không chứng minh demo API tích hợp vào gate |

## Quyết định còn mở

- NV06 classification-only so với classification + charging; không có quyết định chọn bản authoritative.
- NV01 “thẻ/gói chỉ hoạt động sau thanh toán” so với NV02 cho phép phê duyệt công nợ.
- Pass hết hạn: nguồn không tự mở cổng; service có nhánh cảnh báo/tính lượt. Miễn phí code dựa ngày vào, nguồn ra yêu cầu gói còn hiệu lực.
- NV03/NV04 Bước 9 khác giữa hai PDF no-EV; trạng thái `CLOSED` của nguồn khác `COMPLETED` của code.
- README mô tả không dùng ảnh CCCD để so mặt; nguồn đòi ba đối chiếu. Liveness, payment confirmation, offline/audit và concurrency chưa được kiểm chứng đầy đủ.
- Datasource/profile/schema-init production, mức giá mặc định, chính sách refund/công nợ và survey backing chưa xác minh. Không dùng launcher label để chọn database.

## Nguồn và test evidence

- `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf`: NV01 pp.5–6, NV02 p.7, NV03 pp.8–9, NV04 pp.9–10, NV05 pp.10–11, NV06 p.11, NV07 pp.11–12, NV08 p.13.
- `../../evidence/qa/proposal-without-ev-charging/preview.pdf`: đã đọc; NV03 p.8/NV04 p.9 có bước cấp thẻ/gói khác bản final.
- `../../evidence/qa/proposal-with-ev-charging/page-06.png`–`page-09.png`, `page-11.png`–`page-13.png`: source charging-inclusive đã inspect; các trang khác chưa đủ coverage.
- `README.md:5–9,78–91`, `evidence/archive/PROJECT_GAPS.md:3–12`: documentation claims, không test result.
- `spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java:38–40`: test-only H2/create-drop đã thấy. Assertions chưa audit; **không test nào được chạy cho việc viết tài liệu này**.

Các path code trong bảng controller/service đều nằm dưới `spring-web/src/main/java/vn/edu/parking/`. Nguồn đề cương là nghĩa vụ được đề xuất, không thay thế actual schema hoặc kết quả runtime.
