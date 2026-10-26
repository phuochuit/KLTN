# Tài liệu hệ thống quản lý bãi đỗ xe ANPR

Đây là điểm vào chính cho người đọc và AI. Bộ tài liệu giải thích nghiệp vụ và kỹ thuật độc lập bằng Markdown; không cần mở PDF/DOCX/ảnh render để hiểu dự án. [ADR-001](decisions/0001-docs-as-project-reference.md) quy định cách tổ chức này. Tài liệu không chứng nhận hệ thống đã hoàn thiện hoặc sẵn sàng production.

**Phạm vi implementation:** các trang kỹ thuật và mục implementation main đối chiếu tĩnh `abd6183`; không sửa source nghiệp vụ để làm khớp tài liệu hoặc hạ yêu cầu để khớp code. Các bảng được ghi rõ “lịch sử Security + WIP” bảo toàn snapshot cũ, không phải contract hoặc kết quả test của main. [Trạng thái](implementation-status.md) và [testing](testing/strategy.md) phân biệt source hiện hành, kết quả lịch sử và kiểm chứng chưa chạy. Chưa có application/live E2E PASS của revision main trong đợt này.

## Đọc theo nhu cầu

- **Người mới:** [Tổng quan](overview.md) → [Phạm vi nghiệp vụ](requirements/README.md) → [Quyết định](decisions/README.md) → [Trạng thái hiện tại](implementation-status.md).
- **AI/developer:** đọc NV liên quan → [traceability](requirements/traceability.md) → kiến trúc/database/API/security → đối chiếu source và tests được dẫn. Không tự suy code là policy đã được duyệt.
- **Vận hành:** [Setup/troubleshooting](operations/setup-and-troubleshooting.md) → [User guide](operations/user-guide.md). Không khởi động/đổi DB hoặc migrations khi chưa xác nhận môi trường.
- **Khóa luận:** [Yêu cầu và sản phẩm bàn giao](thesis/requirements.md) → [nội dung tư liệu đã tổng hợp](thesis/material-summary.md) → NV/traceability/testing. Không coi kế hoạch hoặc render đề cương là kết quả thực hiện.

## Nghiệp vụ

| Trang sở hữu | Nội dung |
| --- | --- |
| [Phạm vi](requirements/README.md) | Actors, miền dữ liệu, quan hệ tám NV và supporting features |
| [NV01](requirements/nv01-registration.md) | Đăng ký hộ/người/xe, identity/consent/approval và quyền theo xe |
| [NV02](requirements/nv02-cards-passes-pricing.md) | Thẻ, gói, giá, gia hạn, payment/debt/history |
| [Mức phí tham khảo](requirements/pricing-reference.md) | Các mức phí và ví dụ, trạng thái chưa được công nhận khảo sát/biểu phí |
| [NV03](requirements/nv03-resident-entry.md) | Cư dân vào: checks, OPEN và evidence |
| [NV04](requirements/nv04-resident-exit.md) | Cư dân ra: quyền người lấy, fee và closure |
| [NV05](requirements/nv05-visitor-parking.md) | Khách, ownership/capture, payment-before-close |
| [NV06](requirements/nv06-vehicle-classification.md) | Phân loại/correction, nhánh sạc chưa chốt |
| [NV07](requirements/nv07-exceptions-recovery.md) | Quality/manual/outage/retry/recovery/privacy |
| [NV08](requirements/nv08-reporting.md) | History/dashboard/revenue/reconciliation |
| [Sổ quyết định](decisions/README.md) | Quyết định đã duyệt, ràng buộc hiện hành và câu hỏi còn mở |

## Kỹ thuật và kiểm chứng

| Trang sở hữu | Nội dung |
| --- | --- |
| [Kiến trúc](architecture/system.md) | Spring–Desktop–ANPR, media/camera và failure boundaries |
| [Workspace và artifact](architecture/workspace-layout.md) | Vị trí source/test/tools, cache, đầu ra từng lượt chạy và chính sách lưu giữ |
| [Database](architecture/database.md) | Mappings, transaction/lifecycle, migrations và integrity gaps |
| [ERD](architecture/erd.md) | Tổng quan, nghiệp vụ và security/evidence; không production DB introspection |
| [Data dictionary](architecture/data-dictionary.md) | Toàn bộ entity fields, types/null/defaults, FK/index/constraints |
| [API contracts](api/contracts.md) | Gate/AI/slots/Web/QR/media requests, responses và errors |
| [Auth/accounts/audit](api/authentication.md) | Web session/CSRF, Desktop JWT, password, account lifecycle và audit |
| [Endpoint inventory](api/endpoints.md) | Method/path theo source, phân biệt framework và app routes |
| [Security](security/overview.md) | Trust boundaries, quyền, secrets/privacy và controls/gaps |
| [Traceability](requirements/traceability.md) | NV/quyết định → source/API/data/UI → tests/gaps |
| [Testing](testing/strategy.md) | Cách chạy, coverage, kết quả lịch sử và scenarios chưa kiểm chứng |
| [Implementation status](implementation-status.md) | Phần đã có code, subset đã kiểm thử và giới hạn còn lại |

## Quy tắc tránh hiểu sai

1. **Yêu cầu** là điều cần đạt; **đã duyệt** cần authority; **có code** chỉ là implementation; **đã kiểm chứng** cần command/date/environment/result. Không gộp bốn nhãn này.
2. Nội dung nghiệp vụ nằm trong docs; source links chỉ dùng đối chiếu implementation. Ưu tiên file + symbol/route, không dựa số dòng dễ lệch.
3. GateEvidence/evidence IDs là dữ liệu nhận dạng của ứng dụng, không kho tư liệu đề cương. Không xóa/đổi API hoặc bảng vì thay cách lưu tài liệu.
4. Chỉ cập nhật trang sở hữu khi thay đổi hệ thống; trang khác dẫn link thay vì chép lại. Cuối nhiệm vụ báo docs đã sửa hoặc lý do không cần, checks đã/chưa chạy.
5. P01–P09 còn mở không được tự giải quyết bằng cách chọn tên bản “final” hoặc giảm yêu cầu cho khớp code. Không đưa secrets/PII vào docs.
