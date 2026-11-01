# Giao dịch & đối soát — pre-build direction (2026-11-01)

Kế thừa [MASTER](../MASTER.md). Quyết định trước code dựa trên `PaymentController`, `PaymentTransactionService.recent/reconcile`, NV02 và UX search `transaction reconciliation table confirmation status`; không thêm chính sách, grant, API hay settlement.

- Bố cục ledger-first: chỉ số PAID tính trên tối đa 200 giao dịch vừa tải, ghi rõ phạm vi và không là tổng doanh thu. Bảng opaque có các cột receipt/reference, loại, phương thức, số tiền, trạng thái, thời gian, người thu và thao tác; vùng cuộn ngang có nhãn trên màn hình nhỏ.
- Chỉ `PAID` có form ghi chú bắt buộc (tối đa 500) và xác nhận đúng biên nhận trước POST. `RECONCILED` hiển thị ghi chú thực; không nói provider đã settlement. Success chỉ lấy flash server; mọi response không xác định khóa gửi lại và hướng dẫn kiểm tra danh sách.
- Cho phép tìm/lọc **trong danh sách đã tải**, không giả tìm kiếm DB, pagination hoặc tổng sau lọc. Không fake freshness/loading; khi cần kiểm tra trạng thái mới dùng tải lại thủ công.
- Mobile giữ bảng cuộn có thể focus, form labels và dialog native; desktop/mobile cần Playwright quan sát. Không thay RBAC/CSRF hay nghiệp vụ đối soát.

## Hậu kiểm Build (2026-11-01)

H2 synthetic hiện không có transaction. Đã quan sát empty desktop/mobile; thông báo empty ban đầu bị khuất trong bảng rộng, đã chuyển ra ngoài vùng cuộn và quan sát lại. Focused model 2/2 và CSS build thành công. Chưa quan sát ledger có dữ liệu, dialog, response ghi/loading/error/no-permission hoặc lọc có kết quả trong browser; chưa có nghiệm thu người dùng hay Verify tổng hợp. Ảnh tại `.artifacts/test-runs/20261101T142505Z-pricing-browser-0945bf16/`.
