# Thẻ & gói gửi xe — G4B-CARD-FE

Áp dụng `/cards`; kế thừa [MASTER](../MASTER.md), không tạo palette/component system riêng và không thay nghiệp vụ NV02.

## G4B-CARD-FE — pre-build basis (2026-11-01)

Đây là quyết định thiết kế được ghi nhận sau research và baseline tổng hợp, trước khi sửa template/CSS/JavaScript của `/cards`. Nó không phải kết quả Build hoặc nghiệm thu UI/UX.

### Bằng chứng và lựa chọn

- Baseline Playwright trên harness H2 riêng tại `.artifacts/test-runs/20261101T092005Z-card-contract-precheck-24bc458f/`: desktop 1440×1000 và mobile 390×844. Trang cũ dùng header/app.css legacy, gom toàn bộ field vào một form không có preview tiền/kỳ, rồi mới tới hai bảng. Ở mobile, select phương tiện rộng 541px và bảng rộng 540px trong viewport390px; full-page capture rộng582px. Date input gốc cho giá trị ISO `2026-11-01` nhưng Edge hiển thị `11/01/2026` theo locale máy chủ, khó đọc thống nhất trên trang tiếng Việt. Fixture gồm seed DemoDataConfig và marker UXQA của browser harness; không dùng database/media vận hành.
- Local UI UX Pro Max design-system search và retry thiên về Funnel/hero/marketing, đề xuất palette, font và CTA không phù hợp MASTER; loại bỏ kết quả này, không persist output. Search UX phù hợp ở mức tổng quát: phản hồi submit, nhãn/error liên kết và vùng cuộn bảng có nhãn. Search về amount/payment/retry không tìm được recipe đặc thù đáng tin cậy; các quyết định thanh toán bên dưới dựa trên backend contract, MASTER và NV02, không gán cho generator.
- Research log: `.artifacts/tool-runs/20261101T092034Z-g4b-card-fe-research-c5843bf3/`. Contract source và runtime error-path observation được ghi tại Group 4B TODO và `docs/api/contracts.md`/`docs/testing/strategy.md`.

### Hướng thiết kế

- **Thẻ như hồ sơ vận hành, không phải thẻ RFID/marketing funnel.** Dùng dashboard shell hiện hữu, nền xanh–lavender Light Glassmorphism 2.0, Be Vietnam Pro, Lucide local; tạo dấu ấn bằng một “operations desk” có hierarchy xanh–tím tiết chế và mã biển số dễ nhận biết. Không vẽ mã QR/RFID, không giả số liệu/KPI và không thêm thao tác chưa có.
- **Directory trước, thao tác theo ngữ cảnh sau.** Danh sách thẻ là bảng full-width opaque, có cột mã nhận diện/biển số/chủ xe/loại gói/kỳ hiệu lực/giá trạng thái và một link GET rõ “Chọn xe” giữ `vehicleId`. Giữ mọi cột trong vùng cuộn ngang có nhãn trên màn hình nhỏ; không fake search/sort/paging. Bảng chỉ phản ánh cards đã tải, không tự tuyên bố inventory đầy đủ hơn response.
- **Tách rõ ba sự thật khác nhau:** trạng thái `ACTIVE/BLOCKED/EXPIRED`, loại `MONTHLY/PER_VISIT`, và ngày hiệu lực inclusive. Nhãn mã nhận diện nói rõ đó là biển số chuẩn hóa, không phải card vật lý. Tóm tắt phương tiện theo selector hiện có; không gửi quyền/side effects mới.
- **Form theo loại gói.** Giữ route, field names, enum values, CSRF, selected `vehicleId`, default30 và range1–366. Khi chọn `MONTHLY`, hiển thị ngày bắt đầu, số ngày, giá server `monthlyPrices` theo loại xe, ngày kết thúc `start + days - 1`, phương thức thu và reference chỉ bắt buộc cho `BANK_TRANSFER/POS`. Khi chọn `PER_VISIT`, ẩn kỳ/giá/phương thức gói vì backend không tạo SubscriptionPayment/PaymentTransaction cho nhánh này; no-JS fallback vẫn nộp form với contract gốc.
- **Ngày tiếng Việt rõ ràng.** Dùng hiển thị `dd/MM/yyyy` đã kiểm tra ngày lịch thật và serialize vào cùng field `startDate` ISO `yyyy-MM-dd`; giữ native `type=date` làm fallback khi không có JavaScript. Không thêm mốc ngày/policy mới. Ngày kết thúc trình bày dd/MM/yyyy và nêu “tính cả ngày cuối”.
- **Review trước khi ghi.** Trên bề mặt opaque, luôn cho thấy phương tiện, loại thao tác, trạng thái sẽ lưu, kỳ/ngày, giá từ server, phương thức/reference và kết quả dự kiến. Nút mở review không gửi request; bước xác nhận cuối mới gửi một POST. MONTHLY copy nói “ghi nhận khoản thu/biên nhận”, không claim ngân hàng/POS đã settlement. PER_VISIT nói rõ không thu gói tháng. Kết quả thành công chỉ dùng flash sau server redirect/receipt; không tạo success từ click hoặc client preview.
- **Recovery an toàn.** Known `400/409/413` chỉ cho phép sửa và gửi lại khi response route xác nhận retry-safe; `503`, network, session/redirect hoặc response không xác định khóa gửi lại trên document hiện tại và không tự retry. Hiển thị lỗi bằng message thực từ server; không hứa có status lookup/draft API. Không tự gửi POST khi reload/history. Shared handler `/cards` mapping được chủ dự án duyệt riêng sau khi H2/Playwright pre-check tái hiện `400`-candidate trả500; follow-up chỉ được thêm route mapping và focused error-contract tests.
- **Responsive/a11y.** Form một cột ở narrow widths, hai cột chỉ khi mỗi field còn tối thiểu240px; controls touch-friendly, label/required/error liên kết, keyboard-visible focus. Dialog confirmation dùng native semantics, đóng bằng Escape/Cancel và trả focus; không sticky footer che field. Motion nhẹ và tắt khi `prefers-reduced-motion`; glass ở chrome/outer panel, data table/input gần opaque.

### Giới hạn contract phải giữ

- Preview là tham khảo, không lock quote: server tiếp tục quyết định amount. Không prorate, nối kỳ, overlap, auto-renewal, activation/debt/refund, giá hoặc chính sách mới.
- Transaction CARD-BE giữ nguyên. Có thể disable nút khi POST đang chờ, nhưng đây không phải idempotency. Unknown outcome không auto-submit lại; không hứa exactly-once hoặc provider settlement.
- Canonical runtime review ban đầu: isolated H2 POST bank-transfer thiếu reference trả HTTP500 với trang chung “Vui lòng thử lại…”; source shared handler không nhận `/cards`. Chủ dự án duyệt follow-up hẹp tại handler hiện có; status/error semantics vẫn phải được test lại sau khi sửa.

## Hậu kiểm Build — chờ nghiệm thu UI/UX

- Giữ hierarchy directory → form → preview, bảng cuộn ngang có nhãn và dialog native. Review mobile đã chỉnh focus vào tiêu đề, giữ actions trong viewport và hiển thị reference/kỳ; form đứng trước preview ở màn hình hẹp. Review desktop 1440×1000 và mobile 390×844 sau refinement: `.artifacts/test-runs/20261101T125558Z-cards-flash-browser-e3c1b0b1/desktop.png` và `mobile-review.png`; đây là quan sát AI với fixture tổng hợp, không phải human acceptance hay chứng nhận accessibility.
- Sau một POST synthetic 302, flash không còn trên GET thứ hai. JavaScript hiện đọc thông báo Spring từ response redirect thay vì điều hướng GET thêm lần nữa; khóa form và yêu cầu tải lại danh sách thủ công. Thiếu flash hoặc kết quả không rõ vẫn khóa và không tự gửi lại. Nhánh thành công sau sửa chưa được kiểm chứng browser bằng response giả lập; unit test chỉ kiểm trích xuất flash. Chi tiết giới hạn: [testing strategy](../../../docs/testing/strategy.md).

## Targeted UX fix — hậu kiểm (2026-11-01)

- Bảng từng rộng tối thiểu 69rem trong vùng desktop hẹp hơn, còn gợi ý cuộn bị ẩn ở desktop. Giảm minimum về 64rem, cho phép nội dung kỳ/giá xuống dòng, giữ cột thao tác sticky bên phải và gợi ý cuộn ở mọi kích thước. Ở 1440×1000 các cột trạng thái và thao tác đều thấy trọn; 390×844 có thể cuộn vùng bảng bằng bàn phím, nút chọn xe vẫn nhìn thấy và GET chọn xe hoạt động. Quan sát screenshot desktop/mobile tại `.artifacts/test-runs/20261101T131313Z-cards-targeted-harness-cc45c221/`.
- Review mới thay thế giới hạn browser của đoạn trên: proxy loopback trả response giả lập, chặn toàn bộ POST `/cards` trước Spring. Quan sát success có flash, 400, 409, 503, redirect thiếu flash và cancel. Đây là tương tác giả lập, không chứng minh commit thực tế hay provider settlement; không có human acceptance hoặc `CARD-V`.
