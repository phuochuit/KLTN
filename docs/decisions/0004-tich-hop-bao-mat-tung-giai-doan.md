# ADR-0004: Tích hợp bảo mật theo giai đoạn thay vì merge nguyên branch

## Status

Accepted

## Date

2026-10-10

## Context

Branch `feat/backend-security-jwt-rbac` chứa tài khoản database, JWT, RBAC, Gate Evidence, Spring–FastAPI proxy, Desktop authentication, migrations, test và tài liệu. Branch này đồng thời thay đổi hàng trăm file, tổ chức lại launcher và xóa các file cấu hình datasource đang được project sử dụng.

Branch `Phuoc` hiện có các thay đổi mới về dashboard lịch sử, sơ đồ bãi, dữ liệu Aiven, backfill `created_at` và nghiệp vụ ô đỗ. Merge hoặc cherry-pick nguyên commit bảo mật có nguy cơ ghi đè các thay đổi này và làm project không khởi động do thiếu khóa JWT, service token hoặc cấu hình Flyway.

## Decision

Không merge nguyên branch và không cherry-pick nguyên commit bảo mật. Tạo branch tích hợp từ `Phuoc` và port theo các giai đoạn độc lập có test:

1. Secret, backup và migration thử nghiệm trên database clone.
2. Tài khoản database, BCrypt, Web RBAC và audit.
3. JWT, Desktop session, refresh rotation và Desktop login.
4. Spring–FastAPI proxy và internal service authentication.
5. Gate Evidence, private storage và retention.
6. Manual override có quyền, lý do và audit.
7. Kiểm thử Spring, Python, Desktop, MySQL và luồng chạy thật.

Giữ các launcher gốc, `AGENTS.md`, datasource profiles và dashboard hiện tại trừ khi một thay đổi riêng đã được đánh giá và kiểm thử. Flyway không được bật trực tiếp trên Aiven trước khi backup, baseline và thử trên bản clone.

## Alternatives Considered

### Merge nguyên branch bảo mật

- Ưu điểm: lấy đầy đủ tính năng trong một thao tác.
- Nhược điểm: conflict lớn, khó cô lập lỗi, có thể mất cấu hình Aiven/Laragon và thay đổi nghiệp vụ mới.
- Kết luận: không chọn.

### Cherry-pick nguyên commit bảo mật

- Ưu điểm: tránh các commit tổ chức tài liệu khác.
- Nhược điểm: commit vẫn chứa nhiều tầng phụ thuộc và gần mười nghìn dòng thay đổi.
- Kết luận: không chọn.

### Chỉ lấy JWT mà không lấy account/evidence/Desktop đồng bộ

- Ưu điểm: phạm vi ban đầu nhỏ.
- Nhược điểm: tạo trạng thái nửa vời, API hoặc Desktop có thể không hoạt động và audit không gắn được danh tính đầy đủ.
- Kết luận: chỉ được chia theo giai đoạn khi mỗi giai đoạn có ranh giới chạy và test rõ ràng.

## Consequences

- Tốn thêm thời gian port và chạy test nhưng giảm nguy cơ mất dữ liệu hoặc hỏng luồng demo.
- Mỗi commit tích hợp có thể review, rollback và chứng minh độc lập.
- Cần duy trì ma trận phụ thuộc giữa Spring, Desktop, FastAPI và database.
- Không được bật JWT, FastAPI internal auth hoặc Flyway trước khi cấu hình phụ thuộc tương ứng sẵn sàng.
- Kết quả test từ branch nguồn không được dùng làm bằng chứng cho branch tích hợp; toàn bộ suite liên quan phải chạy lại.
