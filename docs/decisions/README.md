# Architecture Decision Records

Thư mục này lưu các quyết định kiến trúc quan trọng của hệ thống quản lý bãi đỗ xe ANPR. ADR giải thích bối cảnh, lý do lựa chọn và hệ quả; tài liệu không thay thế mã nguồn hoặc kết quả kiểm thử.

[Mục lục](../README.md) · [Nghiệp vụ](../requirements/README.md) · [Trạng thái](../implementation-status.md)

## Quy ước

- Tên file: `NNNN-ten-quyet-dinh.md`.
- Không xóa ADR cũ. Khi quyết định thay đổi, tạo ADR mới và đánh dấu ADR cũ là `Superseded`.
- Trạng thái sử dụng: `Proposed`, `Accepted`, `Superseded`, `Deprecated`.
- `Proposed` không được xem là bằng chứng tính năng đã triển khai.

## Danh sách quyết định

| ADR | Trạng thái | Nội dung |
|---|---|---|
| [ADR-0001](0001-spring-backend-trung-tam-va-tich-hop-ai.md) | Accepted | Spring Boot là backend nghiệp vụ trung tâm; FastAPI là dịch vụ AI độc lập |
| [ADR-0002](0002-rbac-database-va-jwt-cho-desktop.md) | Proposed | Tài khoản database, RBAC và JWT cho Desktop |
| [ADR-0003](0003-bang-chung-ai-dang-tin-cay.md) | Proposed | Gate Evidence làm nguồn tin cậy cho kết quả AI |
| [ADR-0004](0004-tich-hop-bao-mat-tung-giai-doan.md) | Accepted | Port bảo mật theo giai đoạn thay vì merge nguyên branch |
| [ADR-0005](0005-docs-as-project-reference.md) | Accepted | Docs là tài liệu tham khảo chính của dự án |
| [ADR-0006](0006-workspace-and-test-artifacts.md) | Accepted | Tách `.cache/`, `.artifacts/` và chuẩn hóa tools/test runner |
| [ADR-0007](0007-schema-migration-khong-dung-flyway.md) | Accepted | Không dùng Flyway; schema do `ddl-auto=update` và script SQL thủ công |

## Ràng buộc kỹ thuật đang áp dụng

Đây là ràng buộc dự án/source hiện hành, không tự tạo lịch sử phê duyệt kiến trúc mới:

- Giữ Spring Java 21/Thymeleaf, WinForms .NET 8 và FastAPI; không chuyển stack chỉ để viết tài liệu.
- Desktop gửi nghiệp vụ và image/video/face processing qua Spring; FastAPI xử lý camera trên máy ANPR. Demo ba ảnh là ứng dụng riêng.
- Web dùng session/CSRF; Desktop JWT mặc định bật, explicit disabled compatibility cho phép gate/AI routes. Main dùng grants cùng nhánh legacy role, không coi AccountRole luôn chỉ là metadata. Revenue vẫn thuộc Web session; các quyền thực tế tại [security](../security/overview.md).
- Main mặc định yêu cầu Spring-owned evidence, kiểm operation/session/quyền; explicit required=false/no-ID legacy vẫn tin client face fields. Không mô tả client flag là proof hoặc coi mọi nhánh đều đã chặn tampering.
- Ba nhóm reasoned override của snapshot feature là lịch sử, không phải policy main. Main dùng ManualOverrideService/AI_RESULT_CORRECT, active account/session/shift và vehicle/face evidence. Khác biệt FAIL/REJECT và blank-plate cần kiểm chứng; permission/lý do/audit không thay bằng checkbox OCR hoặc barrier mô phỏng. Chi tiết [security](../security/overview.md).
- Không đưa credentials, khóa ký, token hoặc dữ liệu sinh trắc học thật vào docs/examples.

## Chính sách cần xác nhận

Các mã P bên dưới chỉ là mã theo dõi trong tài liệu, không thay mã NV hay mã lỗi API. Khi chốt cần ghi người có thẩm quyền, ngày, nội dung, phạm vi và tác động tới code/test/docs. Chưa có quyết định thì không sửa acceptance để hợp thức hóa code.

| Mã | Câu hỏi cần chốt | Nội dung được bảo toàn / tác động |
| --- | --- | --- |
| P01 | NV06 chỉ phân loại hay gồm quản lý sạc? | Phần chung là xác minh loại xe/giá/quota/correction history. Phần mở rộng là quyền, connector, validity, vị trí và ChargingSession; không suy từ xe điện hoặc ParkingSlot rằng sạc đã có |
| P02 | Kích hoạt thẻ/gói chỉ sau thanh toán hay cho phép công nợ được duyệt? | Hai chính sách khác nhau; cần authority, trạng thái và lịch sử phê duyệt; tạo SubscriptionPayment không tự chứng minh settlement |
| P03 | Gói xét hạn lúc vào hay lúc ra; hết hạn có được vào giá lượt? | Yêu cầu hạn chế tự mở khác warning OPEN của code; helper miễn phí hiện dùng ngày entry và card ACTIVE hiện tại. Surcharge/renewal continuity chưa chốt |
| P04 | Mức xác minh danh tính và người lấy xe bắt buộc? | Bảo toàn yêu cầu ba phép so CCCD–đăng ký–realtime, liveness, quyền theo từng xe và người lấy có thể khác người vào. Code pairwise/optional resident exit proof không đáp ứng toàn bộ; chưa chọn mẫu pháp lý/consent/manual enrollment |
| P05 | Quy trình thanh toán, miễn/giảm, mất thẻ, hoàn/hủy và đối soát? | Main đã có positive-fee payment gate, receipt/ledger/shift/reconciliation source; chưa external settlement hoặc đủ debt/refund/waiver policy. Giữ yêu cầu không đóng trước thu/miễn có lý do; không tự đặt mức phạt hoặc grace period |
| P06 | Quality/retry/threshold và quyền dự phòng cụ thể? | Threshold cấu hình chưa site-calibrated; main ManualOverrideService khác policy outage ba nhóm của feature. FAIL/REJECT và blank-plate branches cần xác minh; có outbox/idempotency source không đồng nghĩa recovery guarantee |
| P07 | Ai xem dữ liệu nào, giữ bao lâu và xóa/backup thế nào? | Main retention default30 ngày, skip OPEN, scheduled cleanup mặc định tắt; finite holds là feature history, không main control. Consent/encryption/minimization/retention từng nhóm và deployed restore vẫn cần authority/evidence |
| P08 | Quy định/đề cương nào áp dụng và khảo sát nào được công nhận? | Có bản trích quy định ngày 22/08/2025, DOCX và nhiều render khác nhau; chưa có xác nhận authority/lineage/equivalence toàn bộ. Bảng giá có số liệu nhưng chưa có sampling/provenance/consent đủ để công nhận khảo sát |
| P09 | Tên trạng thái và hành vi replay/khôi phục chuẩn? | Yêu cầu dùng CLOSED cho đóng lượt; API hiện dùng COMPLETED. Cần xác nhận mapping thay vì tự đổi enum; transaction/precheck/single-use evidence không thay event idempotency, locking hoặc DB-file compensation |

Những bản mô tả cũ có bước cấp thẻ/gói đặt trong luồng cư dân vào/ra thay cho xác minh/đóng lượt. Không dùng chúng để suy bỏ kiểm tra hoặc bỏ closure: cấp thẻ nằm ở NV01/NV02, sự khác nhau thuộc P08. Một render cũ mô tả sáu quy trình và identity hằng ngày không quét CCCD, khác bộ tám NV/ba phép so trong bản chi tiết; P04/P08 cần xác nhận. Hai nhánh sạc được mô tả tại [NV06](../requirements/nv06-vehicle-classification.md).
