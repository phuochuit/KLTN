# Architecture Decision Records

Thư mục này lưu các quyết định kiến trúc quan trọng của hệ thống quản lý bãi đỗ xe ANPR. ADR giải thích bối cảnh, lý do lựa chọn và hệ quả; tài liệu không thay thế mã nguồn hoặc kết quả kiểm thử.

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
