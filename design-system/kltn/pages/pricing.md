# Bảng giá — pre-build direction (2026-11-01)

Kế thừa [MASTER](../MASTER.md), dành cho PRICING_MANAGE hoặc ROLE_MANAGEMENT; không đổi guard hoặc nguồn giá. Quyết định trước sửa UI dựa trên NV02, `AdminController.pricing/savePrice`, inventory và UX search `administrative price editing confirmation errors`: không dùng bulk-edit hay giả một nguồn giá được phê duyệt.

- Bề mặt dashboard shell, mở đầu bằng context “bảng giá hiện có”, sau đó từng nhóm xe là một thẻ chỉnh độc lập; label/đơn vị `đ`, thời gian theo field thật, không cứng hóa “4 giờ” khi cấu hình khác. Ô tô giữ `nightPrice` readonly và cả ba thông số giờ/block; xe khác giữ hidden zeros theo form hiện tại.
- Giá là snapshot server, không có `effectiveAt`/history: nói rõ trang không cung cấp mốc hiệu lực hoặc lịch sử, không giả ngày; không hứa mọi giao dịch hiện hành bị/không bị ảnh hưởng.
- Cập nhật yêu cầu xem lại nhóm và giá trước khi submit; native validation, CSRF và field names giữ nguyên. Chỉ flash server mới là thành công; khi không xác định kết quả thì không tự gửi lại, hướng dẫn tải lại và kiểm tra. Không giả stale detection hay loading.
- Bảng/form responsive một cột trên mobile, labels liên kết, điều hướng keyboard, focus rõ; kiểm chứng desktop/mobile qua fixture tổng hợp, không dùng giá thực.

## Quyết định refinement round 1 — trước Build (2026-11-01)

- UX search `currency review amount formatting` (Content / Number Formatting, low severity) khuyến nghị phân tách hàng nghìn thay vì số dài liền mạch. Kế thừa MASTER về tabular numbers và đơn vị tiền: review định dạng giá bằng nhóm chữ số tiếng Việt và `đ`, còn input số giữ nguyên giá trị/kiểu native để không đổi POST contract.
- Review hiển thị **giá trị đang tải → giá trị đề xuất** từ snapshot ban đầu trong chính form; chỉ các trường đổi mới được đánh dấu thay đổi. Không suy ra ngày hiệu lực, stale state hay ảnh hưởng đến giao dịch; `nightPrice` readonly vẫn được nhận diện là giữ nguyên.
- Giữ native `dialog`, focus ring hiện có, Escape/hủy và focus restoration về nút mở. Pending phải có trạng thái nhìn thấy và `aria-busy` trên vùng liên quan; thành công vẫn chỉ theo flash server, unknown vẫn khóa gửi lại và yêu cầu kiểm tra thủ công.
- UX search modal trả kết quả focus indicator chung, không chứng minh riêng Escape/focus restoration; căn cứ cho lifecycle dialog là MASTER §8/§11. Search loading feedback khuyến nghị trạng thái loading→success/error và khóa submit khi đang chờ.

## Quyết định refinement round 2 — trước sửa (2026-11-01)

- Codex tái hiện nhập `2000.0` cho giá trị ban đầu `2000`, khiến review hiển thị hai số đã format giống nhau như một thay đổi. So sánh số tương đương theo giá trị số để đánh dấu “Không đổi”, nhưng không chuẩn hóa hoặc ghi lại input; giá trị gốc người dùng vẫn được gửi theo form hiện tại. Native validation và các field/contract không đổi.
- Làm rõ theo kiểm chứng precision round 2: không chuyển decimal input qua JavaScript `Number` hoặc giới hạn số lẻ khi so sánh/format review. So sánh decimal dạng chuỗi chính xác (kể cả exponent thông dụng), hiển thị đầy đủ phần lẻ đã nhập; chỉ biểu diễn locale, không làm tròn, sửa input/POST, native constraint hay suy luận khả năng chấp nhận của server/DB.
- Bổ sung theo browser Tab review: giữ focus tuần tự bên trong dialog modal ở cả hai biên Tab/Shift+Tab; Escape vẫn đóng dialog và focus trở về trigger. Đây là hỗ trợ vòng focus cho control hiện có, không đổi cấu trúc/contract form.

## Hậu kiểm Build ban đầu — lịch sử (2026-11-01)

Ghi nhận ban đầu: H2 synthetic desktop/mobile, dialog xem lại và hủy không gửi POST; focused model 1/1 và asset build thành công. Tại lượt này chưa có browser matrix response ghi/loading/error/no-permission; các trạng thái đó được kiểm ở final review bên dưới. Ảnh ban đầu tại `.artifacts/test-runs/20261101T142505Z-pricing-browser-0945bf16/`; không đại diện cho trạng thái review hiện tại.

## Hậu kiểm Browser final round 3 (2026-11-01)

Focused Node pricing/payments **6/6** và asset build trước browser review: `.artifacts/test-runs/20261101T154837Z-pricing-payments-r2-last-focused-aa2cfcd5/`. Independent Codex final review tại `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/codex-final/checks.json` báo **17 nhóm PASS**, sáu served asset hashes khớp, 0 page errors/0 fresh-load console errors; 10 HTTP fixture POSTs và 2 route-aborted requests, không forward business mutation. Final readable screenshots xác nhận desktop 1366/1440 và mobile 390/long-note 320; các fixture kiểm pending/flash, forbidden/session/unknown, no-replay, decimal-equivalent display và dialog keyboard/focus. Spring loopback/H2 synthetic + mocked ANPR; không phải live E2E hoặc TEST/VERIFY tổng hợp. Root xác nhận `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/reports/maven-exit.txt` là **0** và `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/reports/TEST-vn.edu.parking.LoginBrowserHarnessTest.xml` có **tests=1, failures=0, errors=0, skipped=0**. Human UI/UX acceptance vẫn pending; các lượt fail trước được giữ lịch sử trong TODO.


## Đối chiếu sau tích hợp main — 2026-11-03

Contract hiện hành có thêm `dailyPassPrice` bắt buộc cho từng `vehicleType`, hiển thị đ/ngày và nằm trong review trước POST cùng CSRF. Các trường giá khác được giữ. Đây là cập nhật tương thích nghiệp vụ upstream đã được người dùng duyệt, không phải đổi hệ thống thiết kế hoặc tự đặt biểu phí. Browser 1440/390 đã kiểm tra field, nhãn review và payload local; không forward cập nhật giá. [Tổng duyệt](../../../docs/review/web-final-review-integration.md) ghi kết quả và giới hạn.
