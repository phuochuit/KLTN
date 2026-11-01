# Bảng giá — pre-build direction (2026-11-01)

Kế thừa [MASTER](../MASTER.md), dành cho PRICING_MANAGE hoặc ROLE_MANAGEMENT; không đổi guard hoặc nguồn giá. Quyết định trước sửa UI dựa trên NV02, `AdminController.pricing/savePrice`, inventory và UX search `administrative price editing confirmation errors`: không dùng bulk-edit hay giả một nguồn giá được phê duyệt.

- Bề mặt dashboard shell, mở đầu bằng context “bảng giá hiện có”, sau đó từng nhóm xe là một thẻ chỉnh độc lập; label/đơn vị `đ`, thời gian theo field thật, không cứng hóa “4 giờ” khi cấu hình khác. Ô tô giữ `nightPrice` readonly và cả ba thông số giờ/block; xe khác giữ hidden zeros theo form hiện tại.
- Giá là snapshot server, không có `effectiveAt`/history: nói rõ trang không cung cấp mốc hiệu lực hoặc lịch sử, không giả ngày; không hứa mọi giao dịch hiện hành bị/không bị ảnh hưởng.
- Cập nhật yêu cầu xem lại nhóm và giá trước khi submit; native validation, CSRF và field names giữ nguyên. Chỉ flash server mới là thành công; khi không xác định kết quả thì không tự gửi lại, hướng dẫn tải lại và kiểm tra. Không giả stale detection hay loading.
- Bảng/form responsive một cột trên mobile, labels liên kết, điều hướng keyboard, focus rõ; kiểm chứng desktop/mobile qua fixture tổng hợp, không dùng giá thực.

## Hậu kiểm Build (2026-11-01)

Đã quan sát H2 synthetic desktop/mobile, dialog xem lại và hủy không gửi POST. Desktop ban đầu kéo cao card ngắn; đã căn đầu grid và quan sát lại. Focused model 1/1 và asset build thành công. Chưa quan sát response ghi/loading/error/no-permission trong browser; chưa có nghiệm thu người dùng hay Verify tổng hợp. Ảnh tại `.artifacts/test-runs/20261101T142505Z-pricing-browser-0945bf16/`.
