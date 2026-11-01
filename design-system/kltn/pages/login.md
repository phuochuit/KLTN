# LumiPark — Login override

[MASTER](../MASTER.md) là nguồn thiết kế chung; override này chỉ áp dụng Login, không thay authentication/backend hoặc những trang khác.

**Trạng thái: người dùng đã duyệt thị giác bản Login responsive hiện tại trong hội thoại.** Giữ nguyên khi triển khai Dashboard. Phê duyệt Login không đồng nghĩa nghiệm thu toàn Web, kiểm chứng live E2E hoặc WCAG certification.

## Identity và tài sản hình ảnh

- LumiPark / Smart Parking Operations. Logo active là **gateway P** tự dựng SVG: trụ đứng, mái/vòng P và nét làn vào trong khoảng âm; cyan–blue–violet, nét góc rõ hơn ribbon cũ. Header/favicon đồng bộ; không sao chép nguyên logo mẫu, chưa coi là bộ identity đầy đủ đã nghiệm thu.
- Login dùng `static/images/brand/lumipark-icon.svg` + live wordmark Be Vietnam Pro ở header; favicon đồng bộ tại `favicon.svg`. Clear-space tối thiểu 8/64 kích thước mark, không stretch/filter/crop. Không lặp logo trong form.
- `lumipark-full.svg`, `lumipark-lockup.svg`, `lumipark-reversed.svg` và `parking-scene.svg` là assets phương án cũ, giữ để không xóa work trước; **không dùng cho direction mới**, không coi là bộ logo mới đã đồng bộ. Chưa nghiệm thu bộ identity đầy đủ hoặc Dark.
- Hero dùng `parking-environment.svg`: cảnh vector gốc với SUV góc sau-ba-phần-tư (cabin, cửa, gương, đèn hậu, lốp/hốc bánh riêng), tiến về barrier và mũi tên đường cùng hướng; cây có tán phân nhánh, thân neo vào bồn, chân công trình/xe có bóng tiếp xúc. Không dữ liệu vận hành/external resource/logo bên thứ ba. **Không phải ảnh photoreal 3D hoặc ảnh AI-generated**; mức chất lượng art vẫn cần người dùng duyệt. Các art cũ giữ nguyên, không dùng trên Login mới.

## Composition / typography / materials

- Desktop hai cột trên **nền mở toàn viewport**, không border/background/shadow bao toàn trang. Grid theo tỷ lệ1.15fr/1fr, form chiếm chiều rộng cột phải với giới hạn544px, không giữ width480/440px theo breakpoint như trước. Padding24–40px và heading24–32px co giãn có giới hạn. Nội dung quyết định chiều cao, không stretch theo viewport. Không Bento, KPI/dashboard giả, footer/caption/feature notes dư.
- Nền cyan sáng phía trái/trên, blue nối hai vùng, lavender phía dưới/phải; mảng màu nhìn xuyên qua form để nhận ra kính. Một lớp trang trí lệch nhẹ phía sau form, không nested frame ôm sát hoặc blur nhiều lớp.
- Layout dựa `min-height` viewport trừ inset; desktop main căn lên theo nội dung với top-padding thích ứng, gap hai cột32–48px. Breakpoint chiều cao≤800px giảm padding/gap và art trước, không giảm font input. Dưới960px chuyển một cột có chủ đích, không hero, **không căn giữa dọc**; mobile header→panel24–40px theo chiều cao, padding20–28px, input/CTA52px. Giữ cuộn khi viewport ngắn, bàn phím ảo hoặc text/zoom làm nội dung dài; không `overflow:hidden` trên body để che lỗi. Giới hạn đọc544px là chủ đích, không yêu cầu form tăng vô hạn theo màn hình.
- Be Vietnam Pro 400/500/600/700 self-hosted Latin/Vietnamese, `font-display: swap`; source/version/OFL tại `static/css/fonts/be-vietnam-pro/README.md`. Heading navy, hero accent blue–violet; hero 32–52px/700, form title 24–32px/700, labels13px, input16px, secondary10–13px. Không thêm font/CDN mới.
- Primary `#3042AA`, text `#171E42`, muted `#42506B`, control border `#7B89AB`; CTA gradient `#2864CE` → `#7541C9` với text trắng. Gradient/logo/art không đóng vai trò màu trạng thái. Primary/muted làm đậm để bù vật liệu trong hơn; kiểm contrast trên glass composition, không chỉ white.
- Form white alpha32–60%/blur20px; reflection nhẹ hơn, lớp sau lộ ít và lệch2° trên desktop. Dưới960px **bỏ lớp kính nghiêng**; nền lavender tập trung sau form rồi chuyển neutral sáng phía dưới, không thêm footer để lấp khoảng trống. Minh họa tăng độ rõ cục bộ ở kính/biển P/chân công trình, không tăng saturation toàn ảnh. Input white78%, radius14px; panel32px. Chỉ panel có backdrop-filter, lớp trang trí không blur. Solid fallback, reduced-transparency và forced-colors; forced-colors bỏ gradient text. Transition180ms cho focus/hover, tắt reduced-motion; không animate nền/blur, không motion library.

## Visual acceptance

Không lấy test xanh thay cho chất lượng thị giác. So ảnh trước/sau cùng1440×900 và375×812: đánh giá khoảng trống trên/giữa hai cột, phân tầng/reflection kính, xe/cây/mặt đường và thời điểm form xuất hiện trên mobile. Người dùng quyết định nghiệm thu; không tuyên bố visual PASS chỉ từ pixel-diff hoặc test counts.

## Native form invariants

GET/POST `/login`, username80/password1024, required, autocomplete, CSRF và model errorMessage/successMessage giữ nguyên. Không thêm minlength, remember-me/reset/signup/Google/Microsoft/SSO hoặc language switch giả. Không AJAX login; submit guard chỉ disable CTA, giữ credential/CSRF enabled. pageshow phục hồi CTA và mask password, JS-off vẫn submit được, toggle ẩn khi JS không có. Bỏ autofocus khi mở trang để không tạo focus ring trước tương tác hoặc tự mở bàn phím mobile; keyboard vẫn đi vào username trước.

Sai credentials/throttle là lỗi form chung; không đánh dấu username là sai khi server không phân biệt. Focus error sau redirect khi JS có; notice success không tự mất. Không persist username/password vào localStorage/sessionStorage; không đổi controller để giữ username. Paste/password manager và native validation giữ nguyên.

## Foundation / verification boundary

CSS input `src/main/frontend/styles/web-ui.css` import Tailwind v4 và tokens. Chỉ scan Login + login.js; chỉ Login load `/css/web-ui.css`. Lucide subset build-time copy tới `/images/lucide/`, không browser library. Trang khác giữ app.css/inline style/script, header/favicon cũ không đổi. Không React migration hoặc đổi config DB/security.

Build/dev/test entry points: [setup](../../../docs/operations/setup-and-troubleshooting.md#lumipark-login-frontend-build) và [tests](../../../tests/README.md). Playwright render và kiểm form trên Spring thật + H2 synthetic/mock ANPR, không DB operational hoặc live gate E2E. Desktop/mobile screenshots là bằng chứng render, không WCAG certification hay người dùng đã chấp nhận art. Research và giới hạn tại [RESEARCH](../RESEARCH.md).
