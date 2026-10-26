# Phạm vi nghiệp vụ quản lý bãi đỗ xe

[Mục lục](../README.md) · [Quyết định](../decisions/README.md) · [Traceability](traceability.md) · [Trạng thái](../implementation-status.md)

## Quy ước đọc

Tám mã nghiệp vụ là **NV01–NV08**; các chức năng hỗ trợ không tự tạo NV mới. Mỗi trang ghi yêu cầu, luồng/ngoại lệ, dữ liệu, acceptance và implementation riêng. Yêu cầu chưa được đáp ứng vẫn phải giữ; code hiện tại không phải phê duyệt nghiệp vụ. “Có test” khác “đã chạy”; H2/mock khác live E2E. Những nội dung cần chủ dự án xác nhận nằm tại P01–P09 trong sổ quyết định.

## Miền dữ liệu và tác nhân

- **Hộ/căn hộ:** đơn vị đăng ký và quota, model hiện tại là Household/FamilyMember.
- **Người–xe–quyền:** chủ đăng ký và người được phép theo từng xe; cùng hộ không tự có quyền mọi xe.
- **Thẻ/gói/giá:** mã thẻ, validity, nhóm giá; payment/debt/renewal/refund/history có yêu cầu riêng.
- **Lượt gửi:** OPEN và đóng đúng lượt, evidence vào-ra và phí. API hiện dùng COMPLETED; yêu cầu cũ dùng CLOSED, P09 chưa xác nhận mapping.
- **Vị trí:** assigned/occupied/borrowed/blocked; ParkingSlot không phải charging station.
- **Bằng chứng nhận dạng:** ảnh/AI/proof được Spring giữ và ràng buộc operation/session. Không nhầm GateEvidence với kho tài liệu nguồn; xóa tư liệu không xóa dữ liệu vận hành.

| Tác nhân | Trách nhiệm nghiệp vụ |
| --- | --- |
| Ban quản lý | Quản trị hồ sơ, quyền, thẻ/gói/giá/quota, tài khoản, ngoại lệ và báo cáo |
| Nhân viên gate | Kiểm xe/người/thẻ/evidence, xác nhận vào-ra trong quyền được cấp, chuyển ngoại lệ |
| Cư dân/thành viên | Cung cấp hồ sơ/consent, chỉ sử dụng xe được cấp quyền |
| Khách | Gửi theo lượt, giữ mã lượt/thẻ và thực hiện quy trình ownership/thanh toán |
| Camera/reader/AI | Cung cấp observation và độ tin cậy, không tự cấp quyền; hardware khác mô phỏng |

## Tám nghiệp vụ

| NV | Đầu ra và quan hệ |
| --- | --- |
| [NV01 — Đăng ký](nv01-registration.md) | Hồ sơ hộ–người–xe/quyền, identity/consent/approval; chuyển NV02 cấp điều kiện, NV06 xác minh nhóm |
| [NV02 — Thẻ/gói/giá](nv02-cards-passes-pricing.md) | Validity/gia hạn/khóa/thay thẻ, giá/phí/payment/debt/history; cung cấp quyền lợi cho NV03–05 |
| [NV03 — Cư dân vào](nv03-resident-entry.md) | Kiểm plate/type/person/card/pass, một OPEN đủ evidence và quyết định cổng |
| [NV04 — Cư dân ra](nv04-resident-exit.md) | OPEN/evidence vào-ra, người lấy được quyền, preview/fee/payment theo policy và đóng lượt |
| [NV05 — Khách](nv05-visitor-parking.md) | Không enrollment cư dân; capture/ownership, phí, thanh toán hoặc reasoned waiver trước closure |
| [NV06 — Phân loại](nv06-vehicle-classification.md) | Loại chính thức/correction history và mapping giá/quota; nhánh sạc chờ P01 |
| [NV07 — Ngoại lệ/phục hồi](nv07-exceptions-recovery.md) | Quality/retry/manual/outage/privacy, recovery không mất/trùng session/payment |
| [NV08 — Báo cáo](nv08-reporting.md) | History/dashboard/revenue/reconciliation, quyền và tổng–chi tiết đúng |

Chuỗi chính: NV01 → NV02/NV06 → NV03/NV04 hoặc NV05 → NV08. NV07 bao quanh mọi bước có lỗi/ngoại lệ. [Mức phí tham khảo](pricing-reference.md) giữ số liệu/ví dụ, không biểu phí đã được duyệt.

## Chức năng hỗ trợ

| Nhóm | Phạm vi / tài liệu sở hữu |
| --- | --- |
| Account, login, password, role, session, audit | [Auth contracts](../api/authentication.md), [security](../security/overview.md); không thêm actor cư dân đăng nhập khi chưa có flow |
| CRUD hộ/member/vehicle, QR và resident media | [User guide](../operations/user-guide.md), [API](../api/contracts.md); QR autofill không identity verification |
| Lookup, image/video/face processing và private evidence | [API](../api/contracts.md), [architecture](../architecture/system.md); Desktop → Spring → FastAPI |
| Slots/borrow/cancel/status/release/dispatch/unassigned/batch | [User guide](../operations/user-guide.md), API; allocation source không sensor/concurrency proof |
| Health/startup/models/camera | [Operations](../operations/setup-and-troubleshooting.md); health không dependency readiness |
| Demo ba ảnh | App riêng, không thay integrated gate identity/liveness |

Các quyết định còn mở và ràng buộc hiện hành chỉ được sở hữu tại [decisions](../decisions/README.md); độ bao phủ code/test/gap ở [traceability](traceability.md). Không cần mở tài liệu render để hiểu phạm vi.
