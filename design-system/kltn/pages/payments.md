# Giao dịch & đối soát — pre-build direction (2026-11-01)

Kế thừa [MASTER](../MASTER.md). Quyết định trước code dựa trên `PaymentController`, `PaymentTransactionService.recent/reconcile`, NV02 và UX search `transaction reconciliation table confirmation status`; không thêm chính sách, grant, API hay settlement.

- Bố cục ledger-first: chỉ số PAID tính trên tối đa 200 giao dịch vừa tải, ghi rõ phạm vi và không là tổng doanh thu. Bảng opaque có các cột receipt/reference, loại, phương thức, số tiền, trạng thái, thời gian, người thu và thao tác; vùng cuộn ngang có nhãn trên màn hình nhỏ.
- Chỉ `PAID` có form ghi chú bắt buộc (tối đa 500) và xác nhận đúng biên nhận trước POST. `RECONCILED` hiển thị ghi chú thực; không nói provider đã settlement. Success chỉ lấy flash server; mọi response không xác định khóa gửi lại và hướng dẫn kiểm tra danh sách.
- Cho phép tìm/lọc **trong danh sách đã tải**, không giả tìm kiếm DB, pagination hoặc tổng sau lọc. Không fake freshness/loading; khi cần kiểm tra trạng thái mới dùng tải lại thủ công.
- Mobile giữ bảng cuộn có thể focus, form labels và dialog native; desktop/mobile cần Playwright quan sát. Không thay RBAC/CSRF hay nghiệp vụ đối soát.

## Quyết định refinement round 1 — trước Build (2026-11-01)

- UX search `currency review amount formatting` khuyến nghị nhóm chữ số dài để dễ đọc; review đối soát tiếp tục chỉ nêu số tiền đã lưu với đơn vị `đ`, không diễn giải settlement. UX search `loading aria-busy form submission` khuyến nghị phản hồi loading→success/error và khóa submit trong lúc chờ.
- Giữ native `dialog`, focus ring hiện hữu, Escape/hủy và focus restoration về form/nút đã mở; MASTER §8/§11 là căn cứ cho semantics/lifecycle vì focused search modal chỉ trả hướng dẫn chung về focus indicator, không phải bằng chứng riêng cho Escape.
- Khi POST đang chờ, trạng thái pending hiển thị rõ và công bố `aria-busy`; chỉ flash server xác nhận thành công. Unknown/network/blocked tiếp tục khóa reconcile POST để không tự phát lại. Dữ liệu review và browser scenarios dùng receipt tổng hợp, không tạo hoặc forward mutation tới Spring.
- Không đổi cách tính summary, filters trên tập đã tải, giới hạn 200 rows, POST fields, CSRF, permission hay semantics PAID/RECONCILED.
- Round 2 theo browser finding: giữ nguyên `required` và `maxlength=500`, đồng thời coi note chỉ có whitespace là thiếu như `PaymentTransactionService.reconcile` hiện đã làm; báo lỗi cạnh input, liên kết bằng `aria-describedby`, đặt custom validity native và giữ nguyên giá trị để sửa. Đây là phản ánh contract server hiện có, không thêm rule.
- Bảng ledger fit đủ cột và action ở màn hình desktop ≥1280px bằng page-scoped column sizing và cho phép note/action co lại; không cắt/ẩn cột. Dưới breakpoint tiếp tục giữ toàn bảng `min-width` trong vùng scroll ngang đã gắn nhãn, có thể focus và dùng bàn phím. Căn cứ UX search: Table Handling khuyến nghị scroll hoặc card thay vì tràn; Overflow Hidden cảnh báo không clip nội dung quan trọng.
- Bổ sung theo browser Tab review: giữ focus tuần tự bên trong dialog reconcile modal ở cả hai biên Tab/Shift+Tab; Escape vẫn đóng dialog và focus trở về trigger. Không đổi form, field, hay POST contract.

## Hậu kiểm Build ban đầu — lịch sử (2026-11-01)

Ghi nhận ban đầu dùng H2 synthetic không có transaction: empty desktop/mobile, focused model 2/2 và CSS build; thông báo empty được chuyển ra ngoài vùng cuộn. Khi đó chưa có populated ledger/dialog/filter/response matrix; các trạng thái này được kiểm ở final review bên dưới. Ảnh ban đầu tại `.artifacts/test-runs/20261101T142505Z-pricing-browser-0945bf16/`; không đại diện cho trạng thái review hiện tại.

## Hậu kiểm Browser final round 3 (2026-11-01)

Independent Codex final review tại `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/codex-final/checks.json` báo **17 nhóm PASS**, sáu served asset hashes khớp, 0 page errors/0 fresh-load console errors; 10 HTTP fixture POSTs và 2 route-aborted requests, zero business mutation forwards. Final readable screenshots xác nhận desktop 1366/1440 và mobile 390/long-note 320; nội dung non-money wrap không collision/clipping, action vừa vùng, amount căn phải và mọi cột còn truy cập được trong vùng cuộn ngang. CSS build/copy-only report: `.artifacts/test-runs/20261101T160218Z-payments-readability-css-final-2e821c98/`. Root xác nhận `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/reports/maven-exit.txt` là **0** và `.artifacts/test-runs/20261101T155314Z-pricing-payments-codex-final-91ad694d/reports/TEST-vn.edu.parking.LoginBrowserHarnessTest.xml` có **tests=1, failures=0, errors=0, skipped=0**. Đây là synthetic H2/mock-ANPR Build Review, không phải live E2E hay TEST/VERIFY tổng hợp; human UI/UX acceptance vẫn pending.
