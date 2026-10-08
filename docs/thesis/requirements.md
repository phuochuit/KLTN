# Yêu cầu khóa luận và phạm vi hệ thống

[Phạm vi nghiệp vụ](../requirements/README.md) · [Traceability](../requirements/traceability.md) · [Kiến trúc](../architecture/system.md) · [Chiến lược kiểm thử](../testing/strategy.md)

## Mục đích và giới hạn nguồn

Trang này tổng hợp nghĩa vụ học thuật và yêu cầu nghiệp vụ, **không chứng nhận implementation hoặc nghiệm thu khóa luận**. Yêu cầu nguồn, quan sát code, test hiện diện và kết quả thực thi là các lớp bằng chứng riêng.

| Nguồn | Nội dung sử dụng / giới hạn |
| --- | --- |
| [Bản trích quy định](../../tmp/review/requirements.txt):2–83 | Ghi ngày **22/08/2025**, phân biệt khóa luận cử nhân/kỹ sư và định hướng ứng dụng/nghiên cứu. Chuỗi nguồn và tính áp dụng cho đề tài chưa xác minh; không mặc nhiên là rubric đã được trường duyệt cho nhóm. |
| [Generator đề cương](../../tools/fill_thesis_proposal_15_20_pages.py):150–164,168–395 | Cam kết API, giao dịch, AI, audit, NV01–NV08, kiểm thử và lịch trình. Đây là nội dung đề xuất, không proof generator đã chạy hay DOCX hiện tại chứa cùng nội dung. |
| [QA no-EV final](../../evidence/qa/proposal-without-ev-charging-final/preview.pdf):pp.5–13,15–18 và [QA no-EV khác](../../evidence/qa/proposal-without-ev-charging/preview.pdf) | Bằng chứng về các PDF đã đọc, không authority của DOCX hoặc kết quả kiểm thử phần mềm. NV03/NV04 có khác biệt Bước 9. |
| [QA charging p.11](../../evidence/qa/proposal-with-ev-charging/page-11.png), [p.12](../../evidence/qa/proposal-with-ev-charging/page-12.png), [p.13](../../evidence/qa/proposal-with-ev-charging/page-13.png) | Các trang đã inspect bảo toàn nhánh charging; chưa xác nhận tương đương toàn bộ các variant. |
| [Hợp đồng artifact](../../evidence/archive/artifact.md) | Ràng buộc template và QA 15–20 trang mang tính lịch sử; quan hệ với DOCX hiện tại chưa xác minh. Không biến thành quy định học thuật hiện hành. |
| [Đề cương gốc](../../evidence/thesis/De_cuong_chi_tiet_KLCN_QL_BaiDoXe.docx) | Bản DOCX được giữ nguyên; chưa xác minh authority, lineage hoặc tương đương với các bản render QA. |

R01 vẫn PARTIAL: authority/lineage DOCX, toàn bộ QA variants và nguồn học thuật chưa được chốt. Không chọn bản authoritative vì tên “final”. Survey dữ liệu/consent và việc xuất bản khảo sát vẫn deferred, không có kết luận đại diện hay mức giá được chứng minh bởi khảo sát trong trang này.

## Nghĩa vụ học thuật: tách khỏi cam kết đề tài

Các minima dưới đây được quy cho **bản trích quy định**, không phải quyết định xác định ngành/track của nhóm. Phải xác nhận chương trình, rubric, template và thời hạn với người có thẩm quyền trước nghiệm thu.

| Track trong bản trích | Nghĩa vụ được nêu | Bằng chứng cần khi nghiệm thu |
| --- | --- | --- |
| Cử nhân, ứng dụng (:19–45) | Ít nhất **3 nghiệp vụ**, trong đó 1 nghiệp vụ phức tạp/đặc trưng; ít nhất **2 nền tảng đồng bộ**; khảo sát hiện trạng, mục tiêu/yêu cầu, quy trình và biểu mẫu; use case nghiệp vụ/hệ thống, lớp phân tích/thiết kế và mô hình quan hệ; phân tách chức năng hợp lý; hoàn chỉnh danh mục, nghiệp vụ, báo cáo/thống kê/kết xuất; báo cáo đúng mẫu và thời gian. | Hồ sơ khảo sát có provenance; đặc tả/diagram/form được duyệt; traceability tới implementation; demo và kiểm thử đồng bộ Web–Desktop; báo cáo và kết xuất thực tế. Tên tám NV hoặc hai project không chứng minh đã đạt. |
| Kỹ sư, ứng dụng (:63–76) | Toàn bộ yêu cầu cử nhân, ít nhất **4 nghiệp vụ**; bảo mật cao, ràng buộc dữ liệu hoàn chỉnh, UI tối ưu, linh hoạt/mở rộng, AI hỗ trợ và cảnh báo/dự đoán xu hướng cho quản lý. | Đánh giá bảo mật, constraints/concurrency, usability, kiến trúc mở rộng, AI metrics và cảnh báo/dự đoán. Chưa có chứng nhận các tiêu chí này đã đạt. |
| Định hướng nghiên cứu (:46–62,77–81) | Khảo sát phương pháp mới, chọn/giải thích phương pháp, đóng góp cải tiến, thực nghiệm so sánh và phân tích kết quả; kỹ sư thêm yêu cầu đóng góp/ứng dụng nâng cao. | Chỉ áp dụng nếu track được duyệt; cần baseline, phương pháp, dataset, protocol và kết quả tái lập. Dùng YOLO/OCR/face model không tự chứng minh đóng góp nghiên cứu. |

Bản trích còn nêu cử nhân 4 tín chỉ/12 tuần/3 sinh viên và kỹ sư 14 tín chỉ/14 tuần/3 sinh viên (:10–17). Đây là thông tin nguồn cần xác nhận tính hiện hành, không lịch trình nhóm đã được duyệt. Đề cương có lịch trình riêng; không đồng nhất nó với quy định trường.

## Hệ thống, tác nhân và ranh giới

Theo [kiến trúc quan sát](../architecture/system.md), browser quản trị và WinForms dùng Spring cho nghiệp vụ/lưu dữ liệu; Desktop gọi trực tiếp ANPR cho nhận dạng; Spring dùng database/filesystem. Demo đối chiếu ba ảnh độc lập và model fallback **không chứng minh tích hợp demo API vào cổng**. Barrier hiện được mô tả là mô phỏng, không điều khiển vật lý đã nghiệm thu.

| Tác nhân | Trách nhiệm theo yêu cầu |
| --- | --- |
| Ban quản lý/quản trị | Duyệt hồ sơ, thẻ/gói/giá, ngoại lệ và báo cáo; giới hạn quyền, lưu audit. |
| Nhân viên trạm gác | Kiểm xe/người/thẻ và evidence; xác nhận vào-ra; manual phải có quyền/lý do/truy vết. |
| Cư dân/chủ xe/thành viên | Cung cấp hồ sơ/consent; quyền dùng **từng xe**, không mặc nhiên mọi xe cùng hộ. |
| Khách vãng lai | Gửi theo lượt, giữ evidence và thực hiện chính sách phí/thanh toán đã duyệt; không cần resident enrollment. |
| Camera/reader/sensor và AI | Cung cấp observation; AI hỗ trợ chứ không tự quyết quyền. Phân biệt thiết bị thật và simulation. |

## Yêu cầu chức năng và kỳ vọng acceptance

Các dòng dưới là **yêu cầu nguồn**, không completion flags. Chi tiết ngoại lệ và implementation nằm tại từng NV và [traceability](../requirements/traceability.md).

| NV / owner | Yêu cầu và kỳ vọng acceptance chưa được chứng nhận |
| --- | --- |
| [NV01](../requirements/nv01-registration.md) | Hồ sơ hộ–người–xe, consent, duplicate handling, ba đối chiếu ảnh, approval và quyền theo xe; lookup card/plate đúng links, ảnh mâu thuẫn không tự kích hoạt; manual có lý do/evidence. |
| [NV02](../requirements/nv02-cards-passes-pricing.md) | Thẻ, gói/validity, gia hạn/khóa/thay thế, giá, payment/debt/history; ngày biên, phí và lịch sử nhất quán. Payment record không tự là settlement. |
| [NV03](../requirements/nv03-resident-entry.md) | Nhận plate/type, kiểm người/card/pass/quyền và evidence; một OPEN hợp lệ, entry evidence/gate outcome; replay/concurrent request không tạo trùng. |
| [NV04](../requirements/nv04-resident-exit.md) | Tìm OPEN, đối chiếu vào-ra, người lấy có quyền (có thể khác người vào), preview/confirm phí và đóng lượt có evidence; kiểm payment/waiver theo quyết định đã duyệt, repeat-safe. |
| [NV05](../requirements/nv05-visitor-parking.md) | Entry/exit khách không resident enrollment, capture/evidence/ownership, phí/thanh toán trước đóng và receipt/report; mất thẻ/sai mặt/manual phải theo policy. |
| [NV06](../requirements/nv06-vehicle-classification.md) | AI đề xuất, nhân viên xác minh giấy tờ, map taxonomy tới giá/quota; lưu prediction/verified type/verifier và correction reason/history. Nhánh B thêm charging rights/session nhưng chưa được chọn. |
| [NV07](../requirements/nv07-exceptions-recovery.md) | Quality/retry/mismatch/outage/manual/privacy và partial failure recovery; giữ accountability, không mất/trùng session/payment/slot; offline/resync phải được kiểm chứng riêng. |
| [NV08](../requirements/nv08-reporting.md) | Tra cứu/cảnh báo/dashboard/doanh thu; known-total dataset, filters/time boundaries và Payment–Session–PriceRule reconciliation; sensitive view/export có quyền/audit. |

Slots/borrowing/batch, CCCD QR autofill, health/media và standalone demo là supporting features trong [scope map](../requirements/README.md), không thêm mã NV chính thức. QR autofill không phải đối chiếu khuôn mặt CCCD.

## Yêu cầu phi chức năng

- **Bảo mật/privacy:** authentication và operation-level authorization, bảo vệ biometric/media, minimization/consent/retention, secret/TLS và audit. [Security observations](../security/overview.md) không chứng minh yêu cầu đã đáp ứng.
- **Toàn vẹn/tài chính:** uniqueness/relationships/state, fee/payment/waiver/refund/report lineage; tránh duplicate và half-commit. Transaction annotation không chứng minh DB–file–barrier atomicity.
- **Tin cậy/phục hồi:** timeouts, dependency outages, response loss, retry limits, recovery/backup và idempotency có kết quả kiểm chứng, không chỉ error message.
- **Đồng bộ/interoperability:** Web–Desktop cùng state, contracts/validation/errors nhất quán; xem [API contracts](../api/contracts.md). Static caller không là E2E PASS.
- **Usability/accessibility:** thao tác phù hợp, feedback rõ, busy/error/manual states và đánh giá người dùng; chưa có kết quả usability/accessibility.
- **Hiệu năng/mở rộng:** đặt mục tiêu latency/concurrency/availability theo site và đo trên môi trường đại diện. Nguồn hiện chưa chốt numeric SLA, không tự đặt ngưỡng.
- **AI quality:** xác định dataset/support taxonomy, model version, confidence/false accept/reject/quality/liveness và human review. Unit tests không chứng minh accuracy hoặc chống giả mạo.
- **Maintainability/observability:** contracts, cấu hình/model/media assumptions, correlation và audit; phân biệt desired controls với observed behavior.

## Quyết định và evidence còn mở

NV06 A classification-only vs B charging chưa chốt; electric type/parking slot không chứng minh charging integration. NV01 payment-only activation vs NV02 debt approval; pass expiry tại ngày vào vs lúc ra; NV03/NV04 Bước 9 và source `CLOSED` vs code `COMPLETED` vẫn khác. Ba đối chiếu ảnh, liveness, mandatory resident-exit face/rights, conditional visitor face và client-trusted flags chưa đạt evidence đầy đủ. Payment/waiver/refund, authorization/manual audit, replay/idempotency, offline recovery, production DB/security và survey authority vẫn mở.

Nghiệm thu cần: xác nhận academic track/approved proposal; xử lý quyết định trên; map nghĩa vụ tới implementation/test; thực thi và lưu kết quả thực tế; công bố remaining gaps. [Testing strategy](../testing/strategy.md) là kế hoạch kiểm chứng, không kết quả hoặc chứng nhận khóa luận.
