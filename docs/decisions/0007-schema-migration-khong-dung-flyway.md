# ADR-0007 — Không dùng Flyway; schema do Hibernate và script SQL thủ công

- Trạng thái: Accepted
- Ngày: 28/10/2026
- Liên quan: [ADR-0001](0001-spring-backend-trung-tam-va-tich-hop-ai.md), [Trạng thái triển khai](../implementation-status.md)

## Bối cảnh

Hệ thống chạy hai môi trường dữ liệu: H2 (file demo và H2 in-memory khi test) và MySQL (Laragon khi demo, Aiven khi bàn giao). Hiện `spring-web/src/main/resources/application.properties` đặt `spring.jpa.hibernate.ddl-auto=update`, nên Hibernate tự tạo và bổ sung cột/bảng theo entity mỗi lần khởi động.

Các thay đổi lớn ngoài khả năng của `ddl-auto` được ghi thành script chạy tay trong `scripts/database/` (11 file `*-mysql.sql` cho các giai đoạn bảo mật, thanh toán, offline/idempotency, chất lượng) cùng cặp `migrate-resident-images-private.*` để chuyển ảnh cư dân sang vùng private.

Đồ án chỉ có một người phát triển, dữ liệu là dữ liệu demo có thể tạo lại, và toàn bộ test chạy trên H2 in-memory nên không cần dựng schema bằng migration có version.

## Quyết định

Không thêm Flyway hoặc Liquibase trong phạm vi đồ án.

- Schema của môi trường demo/test do `ddl-auto=update` quản lý.
- Thay đổi cấu trúc trên MySQL phải viết thành script idempotent trong `scripts/database/*-mysql.sql`, chạy tay và ghi lại trong commit/ghi chú bàn giao.
- Không sửa script đã phát hành; thay đổi mới tạo file mới theo giai đoạn.

## Hệ quả

Thuận lợi:

- Không thêm dependency, không cần bảng lịch sử phiên bản, không phải quản lý baseline cho hai loại database.
- Test H2 luôn khớp entity mà không cần chạy migration trước.

Hạn chế phải chấp nhận:

- Không có thứ tự áp dụng, không rollback, không biết máy nào đã chạy script nào ngoài việc đọc log/ghi chú.
- `ddl-auto=update` không xóa cột, không đổi kiểu an toàn và có thể bỏ sót index/ràng buộc; hai máy có thể lệch schema dù cùng mã nguồn.
- Không dùng `ddl-auto=update` làm bằng chứng "đã migrate" trong báo cáo. Bằng chứng là script, log chạy script và kết quả test trên schema đó.

## Khi nào xem lại

Chuyển sang migration có version (Flyway) ngay khi xảy ra một trong các điều kiện:

- Có từ hai người cùng thay đổi schema, hoặc
- Dữ liệu thật không thể tạo lại và không thể mất, hoặc
- Cần triển khai đồng thời nhiều môi trường (dev/staging/production).

Khi đó cần baseline schema hiện tại thành `V1__baseline.sql` trước khi bật Flyway, và backup dữ liệu trước lần chạy đầu tiên.
