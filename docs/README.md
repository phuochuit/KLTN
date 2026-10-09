# Tài liệu hệ thống quản lý bãi đỗ xe ANPR

Đây là điểm vào canonical cho người đọc và AI agent. Hệ thống hướng tới quản lý bãi đỗ cư dân/khách: hồ sơ và quyền theo xe, thẻ/gói/giá, vào-ra, evidence nhận dạng, vị trí đỗ, ngoại lệ và báo cáo. AI hỗ trợ quyết định, không tự cấp quyền. Tài liệu không chứng nhận hệ thống đã chạy end-to-end hoặc sẵn sàng production.

## Bắt đầu theo nhu cầu

- **Người mới:** [Tổng quan](overview.md) → [Phạm vi nghiệp vụ](requirements/README.md) → [Trạng thái và giới hạn](implementation-status.md).
- **Người vận hành:** [Setup và troubleshooting](operations/setup-and-troubleshooting.md) → [Hướng dẫn Web/Desktop](operations/user-guide.md). Lệnh là hướng dẫn source-derived, không kết quả startup đã kiểm chứng.
- **Developer/AI agent:** scope map → NV liên quan → [Traceability](requirements/traceability.md) → architecture/contracts/security/testing → source được dẫn. Đừng suy implementation từ tên endpoint, test hoặc yêu cầu nguồn.
- **Người đánh giá khóa luận:** [Nghĩa vụ học thuật](thesis/requirements.md) → NV/traceability → [Chiến lược kiểm thử](testing/strategy.md). Minima học thuật và cam kết đề tài được quy nguồn riêng; authority còn mở.

## Mục lục nghiệp vụ NV01–NV08

| Owner | Nội dung |
| --- | --- |
| [Phạm vi nghiệp vụ](requirements/README.md) | Actors, entities, quan hệ tám NV, supporting features và quyết định mở |
| [NV01 — Đăng ký](requirements/nv01-registration.md) | Hộ/người/xe, identity evidence, consent và quyền dùng từng xe |
| [NV02 — Thẻ/gói/giá](requirements/nv02-cards-passes-pricing.md) | Validity, gia hạn, giá/phí và payment/debt/history |
| [NV03 — Cư dân vào](requirements/nv03-resident-entry.md) | Kiểm xe/người/card/pass và tạo lượt OPEN |
| [NV04 — Cư dân ra](requirements/nv04-resident-exit.md) | Lượt vào, người lấy có quyền, phí/evidence và đóng lượt |
| [NV05 — Khách vãng lai](requirements/nv05-visitor-parking.md) | Entry/exit khách, evidence, ownership và thanh toán |
| [NV06 — Phân loại](requirements/nv06-vehicle-classification.md) | AI đề xuất, loại chính thức/correction; classification-only và charging-inclusive chưa chốt |
| [NV07 — Ngoại lệ/phục hồi](requirements/nv07-exceptions-recovery.md) | Quality, manual, outage/retry/recovery/privacy |
| [NV08 — Báo cáo](requirements/nv08-reporting.md) | Tra cứu, dashboard, doanh thu, reconciliation và quyền |
| [Traceability](requirements/traceability.md) | Nhóm yêu cầu → nguồn/code/test/gap, kể cả supporting features |

## Mục lục kỹ thuật, vận hành và học thuật

| Owner | Thông tin cần tìm |
| --- | --- |
| [Tổng quan](overview.md) | Mục tiêu, actors, workflow và integration boundaries |
| [Trạng thái implementation](implementation-status.md) | Documented/code observed/partial/not verified/discrepancies, gaps theo từng NV |
| [Kiến trúc hệ thống](architecture/system.md) | Desktop–Spring–ANPR, demo riêng, models/media/camera và diagram source-based |
| [Database](architecture/database.md) | Entity/repository mappings, persistence/media constraints và profile uncertainty |
| [API contracts](api/contracts.md) | Method/path, request/response/errors và caller; Spring/forms/ANPR/demo phân biệt |
| [Security](security/overview.md) | Actual access/CSRF/trust boundaries khác desired controls |
| [Thesis requirements](thesis/requirements.md) | Nguồn học thuật, minima và cam kết đề tài; applicability chưa chốt |
| [Testing strategy](testing/strategy.md) | Existing test IDs/assertions, executed evidence và planned NV coverage riêng biệt |
| [Setup/troubleshooting](operations/setup-and-troubleshooting.md) | Prerequisites, CMD/startup order, configuration/DB/model/camera và launcher caveats |
| [User guide](operations/user-guide.md) | Đúng tên UI Web/Desktop, resident/visitor/slots, manual và simulation limits |

Survey publication **deferred** đến khi provenance, sampling và privacy/consent được giải quyết; không có trang survey placeholder hoặc kết quả đại diện được công nhận ở đây.

## Tìm thông tin authoritative như thế nào?

1. Trang canonical sở hữu từng chủ đề ở mục lục; NV là nguồn rule nghiệp vụ được ghi nhận, architecture/contracts mô tả observed implementation, testing ghi mức bằng chứng kiểm thử.
2. Với claim quan trọng, theo citation về đúng file/symbol/trang/variant. Tài liệu cũ có thể ghi mức inspection tại thời điểm authoring; dùng status/testing hiện tại cho cập nhật, không coi “indexed” là “passed”.
3. Nguồn đề cương/QA không được chọn theo tên “final”. DOCX/regulations/QA lineage còn unresolved; cả NV06 A/B phải giữ. Chỉ quyết định nghiệp vụ được người có thẩm quyền duyệt mới resolve conflict.
4. Root README là hướng dẫn/claims lịch sử, không ưu tiên hơn source-supported discrepancies được ghi tại operations/status. Acceptance tài liệu cũng không là application readiness.

Các giới hạn cần đọc trước thao tác: entry warning có thể đã tạo OPEN; resident exit face proof chưa bắt buộc với mọi lượt; guest exit face chỉ bắt buộc nếu có ảnh entry; payment record không settlement. Image/video/face processing hiện đi qua Spring JWT và FastAPI internal Bearer, evidence được lưu riêng và có scope/retention; đây chưa phải live deployment proof. Liveness, concurrent/idempotent operation, backup/restore, offline và production datasource chưa verified. Client face claims bị từ chối ở nghiệp vụ. Xem [status](implementation-status.md), không tự coi các luồng là an toàn vì có UI button.
