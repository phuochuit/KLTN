# LumiPark Web — Design System MASTER

**Status:** Login responsive đã được người dùng duyệt thị giác. Dashboard & Overview triển khai theo [Dashboard override](pages/dashboard.md), được bộ bảy skill phối hợp quyết định thiết kế và chờ duyệt thị giác riêng. `/roles` và `/account-management` đã có post-implementation visual review tại [roles override](pages/security-roles.md) và [account override](pages/account-management.md), hiện chờ người dùng nghiệm thu UI/UX; review không phải human acceptance. UI/UX `/registrations` đã được người dùng chấp thuận theo [registration override](pages/registration.md); `REG-V` vẫn pending. Không tự áp redesign lên các trang khác; Dark Mode ngoài phạm vi.

Đây là **nguồn chuẩn duy nhất về thiết kế Web** trong phạm vi redesign, không thay requirement/security/API. Đọc MASTER trước, sau đó chỉ áp dụng khác biệt trong `pages/<group>.md`; override không được hạ contrast, quyền hoặc thay contract. Không có override thì dùng MASTER. Không tạo một system khác trong source hoặc internal context.

Điểm vào: [Screen inventory](SCREEN-INVENTORY.md) · [UI Audit](UI-AUDIT.md) · [Research và provenance](RESEARCH.md) · [Phase 2 handoff](PHASE-2-HANDOFF.md) · [Tài liệu hệ thống](../../docs/README.md).

## 1. Philosophy và direction

- **Tên sản phẩm:** LumiPark — **Smart Parking Operations**. Login dùng gateway P cyan–blue–violet (trụ/mái cổng và làn vào), không dùng Lucide làm logo; palette/logo chưa khóa cho toàn Web. Canonical assets tại `spring-web/src/main/resources/static/images/brand/`; [Login override](pages/login.md) ghi assets đang dùng và phạm vi foundation.
- Palette phụ, typography, spacing, radius, material và motion phía dưới là baseline có thể điều chỉnh có lý do; không phải mọi con số đều đã được người dùng khóa. Không tạo Design System độc lập cho Login.
- **Login visual rebuild được duyệt:** giữ cảnh bãi xe bên trái + một form kính bên phải, kết hợp nền mở có chiều sâu của B; không khung lớn bao màn hình, không nested frame ôm form, không footer/caption/feature notes dư. Nền cyan–blue–lavender, text navy và CTA xanh–tím; Be Vietnam Pro self-hosted. Điều chỉnh theo chiều cao viewport trước khi thu nhỏ nội dung; không giấu overflow toàn trang. Chi tiết, token và giới hạn art tại [override](pages/login.md); không tự áp lên trang cũ.

- Sản phẩm: Web quản trị chung cư cho ban quản lý/nhân sự được cấp quyền, dùng lâu với dữ liệu dày; không phải landing page, cổng cư dân hay màn hình gate Desktop.
- **Glassmorphism 2.0 + Bento Grid + Light mặc định**, Tailwind CSS v4, Lucide; motion tiết chế. “2.0” ở đây là tên direction của dự án: kính có phân tầng nhưng nội dung quản trị gần opaque, không một standard/thư viện mới.
- Nền pastel blue/lavender, sidebar sáng nổi nhẹ, topbar thanh, panel frosted lớn bo tròn; hierarchy đến từ spacing, typography và elevation, không từ hiệu ứng liên tục.
- Bento dùng cho overview/KPI và cụm tác vụ; bảng và biểu mẫu vẫn là khối rộng, có thứ tự đọc tuyến tính. Không ép bảng vào tile nhỏ hoặc chia field không cùng nghĩa.
- **Reference:** ba ảnh Login người dùng cung cấp đã xem trực tiếp trong hội thoại cho lần dựng mới: lấy ảnh 1 làm nền tảng bố cục, ảnh 3 tham khảo cảnh bãi xe, ảnh 2 tham khảo màu/kính. Không có file ảnh nguồn được cấp để cắt ghép, không claim pixel-match hoặc sao chép chức năng/số liệu trên ảnh. Dashboard và các màn hình khác vẫn cần duyệt riêng.
- Các giá trị dưới đây là proposal đã kiểm số học sơ bộ, không phải chứng nhận WCAG/runtime. Generator là input nghiên cứu, không có quyền thay lựa chọn người dùng; quyết định loại bỏ dark/landing/luxury defaults tại RESEARCH.

## 2. Token architecture và Light palette

Ba tầng: primitive (`--primitive-blue-700`) → semantic (`--ui-primary`) → component (`--button-primary-bg: var(--ui-primary)`). Screen không giữ hex hoặc opacity riêng. Tailwind bridge chỉ ánh xạ semantic; không dùng semantic này như primitive của semantic khác.

| Semantic token | Light value | Vai trò / giới hạn |
| --- | --- | --- |
| `--ui-canvas` | `#F5F7FF` | nền fallback |
| `--ui-pastel-blue` | `#E8F0FF` | đầu gradient, không text |
| `--ui-pastel-lavender` | `#EEE9FF` | cuối gradient, không trạng thái |
| `--ui-surface` | `#FFFFFF` | bảng, input, nội dung quan trọng |
| `--ui-surface-soft` | `#F1F5FF` | header bảng, secondary button |
| `--ui-text` | `#17213B` | body/headings |
| `--ui-text-muted` | `#526078` | help/metadata/placeholder, không hạ opacity |
| `--ui-primary` | `#335CDA` | CTA/link/focus |
| `--ui-primary-hover` | `#294BB8` | hover |
| `--ui-primary-active` | `#213D96` | pressed |
| `--ui-on-primary` | `#FFFFFF` | chữ CTA |
| `--ui-selected` | `#E8EEFF` | nav/tab/row selected |
| `--ui-accent` | `#6D4BC3` | series thứ hai, accent không thay primary |
| `--ui-border` | `#D8E0EF` | divider trang trí, không đủ cho control boundary |
| `--ui-control-border` | `#7A879F` | boundary input/select, icon có nghĩa |
| `--ui-focus` | `#335CDA` | outline 3px, offset trắng 2px |
| `--ui-success` / `--ui-success-soft` | `#166534` / `#ECFDF3` | thành công/ACTIVE |
| `--ui-warning` / `--ui-warning-soft` | `#854D0E` / `#FFF7E6` | cần chú ý/OPEN/đỗ nhờ |
| `--ui-danger` / `--ui-danger-soft` | `#B42318` / `#FFF1F0` | lỗi/destructive/quá hạn |
| `--ui-info` / `--ui-info-soft` | `#1E40AF` / `#EFF6FF` | thông tin/được gán |
| `--ui-disabled-bg` / `--ui-disabled-text` | `#E8ECF5` / `#526078` | native disabled, lý do ở text kế bên |
| `--ui-scrim` | `rgb(23 33 59 / 40%)` | modal, không dùng để giảm opacity nội dung |

Canvas: `linear-gradient(135deg, var(--ui-pastel-blue), var(--ui-canvas) 52%, var(--ui-pastel-lavender))`. Không ảnh/high-contrast blob chạy phía sau nội dung; không animated gradient. Gradient trang trí không mang dữ liệu.

Neutral state mapping dùng **foreground `--ui-text-muted` + background `--ui-surface-soft`**, border `--ui-control-border` khi cần boundary mang nghĩa; không tạo màu “neutral” riêng mỗi page. “Đang đỗ hợp lệ” và “Khóa/bảo trì” phân biệt bằng text/icon, không chỉ neutral tint.

**Contrast:** bình thường ≥4.5:1; chữ lớn ≥3:1; focus/control boundary/icon mang nghĩa ≥3:1. `--ui-border` chỉ chia vùng trang trí; input bắt buộc dùng control-border. Dùng solid white dưới text trong trường hợp không chứng minh được backdrop. Bảng đo đại diện và phương pháp ở RESEARCH; vẫn cần đo composed screenshot thực tế.

### Theme readiness (chưa làm Dark)

`:root` khai báo Light semantic và `color-scheme: light`. Sau này `[data-theme="dark"]` chỉ remap semantic colors/materials/shadows, giữ component API/spacing. Không tự bật theo OS, không hiện toggle chưa hoạt động. Không đảo màu bằng filter, không tái dùng Light alpha/contrast cho Dark. Dark cần palette, chart, ảnh, native controls, contrast QA và phê duyệt riêng. JS/chart đọc semantic bằng computed styles khi thực sự cần.

## 3. Typography

Khuyến nghị **Source Sans 3** cho cả heading/body để đọc bảng tiếng Việt; dùng weights 400/600/700. Phương án mặc định không tải font: `"Segoe UI", system-ui, sans-serif`. Lexend có thể thử cho heading sau đối chiếu ảnh, không thêm hai font nếu không có lợi ích rõ. Không Cinzel/Josefin hoặc mono heading mặc định. Trước tự host cần xác nhận license, Vietnamese glyphs/subset và `font-display: swap`; Phase 1 không tải font.

| Role | Size / line-height | Weight |
| --- | --- | --- |
| Page heading | 28/36px desktop; 24/32px nhỏ | 700 |
| Section heading | 20/28px | 600 |
| Card heading | 16/24px | 600 |
| Body/input | 16/24px | 400 |
| Table/nav/button | 14/20px | 400 hoặc 600 |
| Caption/badge | 12/18px | 600; không dùng cho dữ liệu chính |
| KPI | 32/40px; 28/36px nhỏ | 700 |

Implementation dùng rem (base 16px), không khóa browser font size. Tiền/số lượng dùng tabular-nums, căn phải; biển số/receipt không letter-spacing quá mức. Không ALL CAPS paragraph, không ellipsis thông tin quan trọng mà không cách mở đầy đủ. Đơn vị `đ`, kỳ và ý nghĩa chỉ số luôn có nhãn; không animate counter.

## 4. Spacing / layout / container

- Base 4px: `--space-1..8` = 4/8/12/16/20/24/28/32px; `--space-10/12` = 40/48px. Icon-text 8px, label-field 8px, field-hint 4px, fields 16–24px, sections 24–32px.
- Shell desktop từ 1024px: sidebar 248px (15.5rem), topbar min-height 72px (4.5rem), content `minmax(0,1fr)`; page gutter 24px, 32px từ 1440px. Content max 1440px, centered trong phần còn lại; table/slots có thể dùng hết vùng này.
- 768–1023px: nav drawer thay sidebar cố định; gutter 24px, không icon rail khó hiểu. Dưới 768px: gutter 16px, topbar wrap/min-height 64px, một cột, không trang bị overflow ngang trừ bảng/biểu đồ được gắn nhãn.
- Breakpoints mobile-first: sm 640px, md 768px, lg 1024px, xl 1280px, 2xl 1536px (rem tương ứng 40/48/64/80/96). Đổi theo available content nếu sidebar khiến field quá chật; không chỉ dựa viewport.
- Form max-width 960px, 1 cột nhỏ; 2 cột khi mỗi field còn ≥240px. Các địa chỉ/quyền/upload full row. Form ngắn settings tối đa 640px. Không form-grid 4 cột mặc định.
- `min-width:0` cho grid child, wrapping text, max-width 100% cho media; sticky topbar/toolbar không che focus, dùng scroll-margin. Tránh hai thanh cuộn dọc cho page thường.
- Radius: `--radius-control` 12px, `--radius-card` 24px, `--radius-dialog` 24px, `--radius-inner` 16px, `--radius-pill` 999px. Nested small cells không cùng 24px; input và button cùng radius.

## 5. Glass materials / elevation

Opacity là **background alpha**, không `opacity` trên panel cha. Nội dung, text, icons luôn opaque.

| Material | White alpha / blur | Border / shadow | Dùng cho |
| --- | --- | --- | --- |
| G0 solid | 100% / 0 | 1px ui-border; không hoặc shadow-1 | input, table body, detail/form content |
| G1 reading glass | 92% / 12px | 1px white 85%; shadow-1 | card overview, outer form panel |
| G2 chrome glass | 86% / 16px | 1px white 80%; shadow-2 | sidebar/topbar, không bảng nhiều hàng |
| G3 overlay | 98% / 0 | 1px ui-border; shadow-3 + scrim | dialog/dropdown chứa thao tác |

Shadow-1 `0 4px 16px rgb(40 56 100 / 6%)`; shadow-2 `0 12px 32px rgb(40 56 100 / 9%)`; shadow-3 `0 24px 64px rgb(23 33 59 / 18%)`. Elevation 0 canvas, 1 panel, 2 chrome, 3 overlay. Highlight border không tính là accessible control boundary.

Tối đa một backdrop-filter trong mỗi nhánh chồng lớp; không blur từng row/cell/slot, không full-screen blur ở scrim. Blur không transition/animate. `@supports` fallback solid white, bỏ backdrop-filter; reduced transparency/forced colors dùng opaque và native borders. Khi thiết bị yếu hoặc scrolling jank, ưu tiên G0. Không coi dataset “cost low” là benchmark.

## 6. Bento Grid rules

12 columns desktop, 8 tablet, 1 mobile; gap 24px desktop/16px nhỏ. Child `min-width:0`; auto row theo nội dung, không masonry/dense packing đổi thứ tự DOM. Không fixed-height tile làm cắt dữ liệu, không card-scale on hover.

- KPI 3–4 cột mỗi tile khi đủ chỗ, 2 tile/hàng tablet, 1 tile/hàng mobile. Row KPI nội bộ có thể 4 cột → 2 → 1 độc lập 12-col grid.
- Overview chart 8 + summary 4; tablet cả hai full width nếu plot không đủ; recent table luôn full width. Phân biệt operational counts và period revenue, không gộp thành một số “live”.
- Management pages: toolbar → bảng full width → form/detail theo tác vụ. Bento chỉ cho summary phù hợp, không thay table rows bằng card wall desktop.
- Trình tự DOM = trình tự visual = keyboard. Không placeholder KPI/alerts/health dựa API chưa có. [Dashboard override](pages/dashboard.md) xác định bố cục cho dữ liệu hiện hữu.

## 7. Sidebar / navigation / topbar

Giữ grouping có nguồn: Tổng quan; Hồ sơ (Đăng ký, Hộ/cư dân, Phương tiện, Thẻ & gói); Vận hành (Sơ đồ bãi, Lịch sử, Giao dịch & đối soát); Cấu hình (Giá, Hạn mức); Quản trị (Tài khoản/phiên/ca, Vai trò, Nhật ký). Không thêm màn hình chỉ vì requirement nhắc.

Sidebar link min-height 44px, 12px horizontal padding, icon 20px, label 14/20px; selected dùng selected-bg + primary text + marker, `aria-current="page"`. Group button có `aria-expanded`, click/keyboard được, không hover-only. Giữ authority condition trên từng link; menu ẩn không thay server authorization. Không nhóm rỗng, không tự đổi role matrix.

Topbar: breadcrumb/title ngắn và account menu (tên, đổi mật khẩu, POST logout). Không global search/notification bell vô dụng khi chưa có contract. Mobile drawer có accessible name, Escape, focus containment/return và background inert; khi đóng không còn control tabbable. Skip link tới main, một main/h1, nav có label. Account/password dùng destination đã có, không invent profile endpoint.

**Navigation scroll decision:** reveal the selected route inside navigation's own scroll container while preserving main-document position; retain native drawer keyboard/Escape/focus behavior and honor reduced motion.

**Drawer usability clarification:** keep the native drawer close control visible at the top while only the navigation list scrolls; the close action must not cover the revealed active route. Close the open drawer when crossing the existing 64rem desktop breakpoint.

## 8. Component specifications

Tất cả spec kế thừa tokens, keyboard và state matrix §9. Không mặc định panel có pointer khi nó không tương tác.

| Component | Geometry / anatomy | Behavior và accessibility |
| --- | --- | --- |
| Button | min-height 44px; px16, gap8; label 14/20 semibold. Compact table 36px chỉ desktop fine-pointer; hit area vẫn ≥24px | primary blue/white, secondary soft/text + control border, ghost text, destructive danger/white. Một CTA chính mỗi vùng; native button, đúng type; anchor cho navigation; loading giữ width/label |
| Input | min-height44, px12/py10, solid white, 1px control-border, radius12 | label kết nối for/id, required text, helper/error id và aria-describedby; `aria-invalid` khi sai. Giữ name/value/type/min/max/readonly; readonly khác disabled và vẫn submit |
| Select | cùng geometry input, native ưu tiên. Variant kích thước opt-in: **Compact** tối đa 8rem cho tập lựa chọn ngắn; **Standard** lấp field phân loại/lọc; **Fluid/Entity** lấp vùng được cấp, `min-width:0` cho tên xe/người/lượt dài. | empty option rõ, không placeholder làm label; giữ selected và enum value, kích thước không đổi theo option đang chọn. Với `base-select`, nhãn dài được rút gọn khi đóng và đọc đầy đủ trong picker có wrap; fallback native giữ hành vi nền tảng. `base-select` dùng chevron kiểu Lucide và popup có selected/hover/focus/disabled states; scope hiện tại là `/slots`, `/sessions`, các Select Dashboard được chọn và Select trong vehicle editor trên `/vehicles`. Trên `/vehicles`, popup có giới hạn kích thước và option tối thiểu 44px; không tự áp cho trang/form khác. Fallback popup/icon do trình duyệt hoặc hệ điều hành quyết định. Không tạo searchable combobox dependency nếu chưa duyệt và QA keyboard. |
| Textarea | cùng input, min3 rows, resize-y | giữ maxlength, label/hint, không fixed height cắt địa chỉ/ghi chú |
| Card | G1 shell, padding24 (16 nhỏ), title/body/actions; G0 inset cho data | không clickable cả card khi có nested controls; chỉ link rõ tên; không hover lift mặc định |
| Table | G0 body, soft header; padding12x16; row min48px, wrap; 14/20 text | semantic table/caption/th scope, tiền phải, thời gian/ID rõ; wrapper overflow-x auto có accessible name và keyboard scroll khi cần. Không hide cột nghiệp vụ trên mobile; không fake sort/pagination. Empty/error/loading riêng; controls không gây row click |
| Modal | G3, width min(640px, viewport−32px); detail rộng tối đa800; radius24, padding24/16, max-height calc(100dvh−32px) | native dialog ưu tiên, named title/description, showModal, focus trap/return, Escape/Cancel; destructive focus Cancel. Cuộn nội dung, footer không che field; không lồng modal gây mất focus |
| Dropdown | G3, min-width200, max-width viewport−32, option min44 | navigation links dùng list/popover disclosure, không ép role menu; action menu nếu dùng role menu phải triển khai Arrow/Home/End/Escape đúng. Click ngoài đóng; align tránh tràn |
| Tab | min44 high, active underline + text, không chỉ màu | role tablist/tab/tabpanel, aria-selected/controls, roving tabindex, arrows/Home/End; chỉ dùng cho panel cùng page. Chuyển route dùng links, không tab giả |
| Badge | px8/py4, radius-pill, 12/18px; text state + optional16px icon | soft bg + dark semantic foreground; không trạng thái chỉ màu. Badge không tương tác không có tabindex; long label wrap |
| Toast / notice | opaque surface, icon20 + message + dismiss44; width≤400 | success role=status/polite, error quan trọng role=alert dùng tiết chế. Success auto-dismiss chỉ khi có notice/history thay thế; error không tự mất; pause timer hover/focus. Flash message server luôn visible sau redirect; không toast-only validation |
| Loading | spinner16/20 + text hoặc skeleton theo kích thước thật | aria-busy vùng cập nhật; announce một lần, không từng tick. Static placeholder reduced motion; không hiển thị 0 đ như dữ liệu đang tải; không khóa navigation toàn trang |
| Checkbox / radio | native 20px visual, label click area≥44px | fieldset/legend cho permissions/authorized members; focus rõ, checked + label; không bỏ unchecked semantics/các hidden values hiện tại |
| Upload / camera | G0 preview contain, caption file/size, buttons44 | tên file, limit10MB theo form source, progress/error/retry; retain file-vs-capture contract. Không tự upload/bật camera; dừng tracks khi đóng/chuyển page; QR chỉ autofill, không nhãn “đã xác minh” |
| Detail view | label-value dl, summary identity + grouped facts + actions | không route detail mới nếu chưa có controller; dùng inline region/dialog với data sẵn có. Sensitive data không nhân bản vào tooltip/telemetry; thiếu hiển thị “Chưa có”, không fake value |

Management table enhancement: search/sort client-side chỉ khi toàn bộ dataset hiện có và có nhãn phạm vi; lớn hơn cần backend pagination/filter được duyệt riêng. Giữ audit pagination server hiện tại. Không chọn bulk/export/delete nếu source không có.

## 9. Interaction state matrix

| State | Required treatment |
| --- | --- |
| Default | text/boundary rõ trên material, native semantics, label cụ thể |
| Hover | primary-hover; secondary soft; row subtle selected tint, không scale/lift dữ liệu; không hover là đường duy nhất |
| Active/pressed | primary-active; action button không dịch layout; toggle dùng aria-pressed, selected khác pressed |
| Focus-visible | outline3px ui-focus + offset2px white; không tắt outline nếu thiếu thay thế; không bị clipping bởi overflow |
| Disabled | native disabled + disabled tokens, không opacity cha; ghi lý do nếu không hiển nhiên; không dùng disabled field thay readonly |
| Loading | giữ text/width, spinner + aria-busy, chống submit lặp ở UI nhưng không coi là idempotency backend |
| Success | semantic icon + message; server flash sau POST/redirect; không auto mở modal |
| Warning | warning-soft + warning text + lý do/hành động; không pulse vô hạn |
| Error | danger border + inline description, linked focusable error summary sau submit nhiều lỗi; giữ input không nhạy cảm nếu contract cho phép |

Form lỗi từ server hiện chưa luôn trả field errors/giữ dữ liệu. UI chỉ render evidence thực có; handoff ghi backend gap riêng, không fabricate BindingResult hoặc hứa file/password còn sau redirect. HTTP401/session timeout khác 403, lỗi mạng khác empty; hiển thị retry chỉ cho GET an toàn, không tự gửi lại POST thu tiền/reset/revoke.

## 10. Motion

Tokens: `--duration-fast:120ms` (hover/pressed), `--duration-normal:180ms` (dropdown/tab fade), `--duration-overlay:240ms` (drawer/dialog). Easing `cubic-bezier(.2,0,0,1)` enter; `cubic-bezier(.4,0,1,1)` exit. Hiệu ứng opacity và transform tối đa translateY4px cho overlay; không animate layout/blur/table rows/counters.

`prefers-reduced-motion: reduce`: bỏ transform/animation/smooth scrolling, transition không thiết yếu 0ms, spinner thành trạng thái tĩnh kèm text. Không chờ animation mới xử lý tác vụ/focus. Không GSAP/runtime motion library chỉ để fade, không bounce/stagger hàng nghìn row, không pulsing quá hạn. CSS + vanilla JS đủ cho direction này. Live refresh vẫn cần pause/freshness, reduced motion không đồng nghĩa dừng nghiệp vụ.

## 11. Accessibility / readability acceptance

Mục tiêu WCAG2.2 AA, chưa chứng nhận. Keyboard đi được toàn bộ, không trap ngoài overlay; DOM semantics trước ARIA. Focus không bị sticky bar che; target web tối thiểu24x24 CSSpx theo AA (ưu tiên44x44 cho admin touch). 200% zoom, 400% reflow ~320CSSpx; bảng hai chiều có vùng scroll riêng, không toàn body. Vietnamese accents không bị crop; screen reader labels/invalid/expanded/selected/busy đúng.

Không chỉ màu cho slot/status/chart. Chart có table fallback đầy đủ giá trị, label/unit/kỳ và controls tháng bằng keyboard. Một vùng status polite cho refresh, không spam mỗi3s. Không tự đóng thông báo trước khi đọc. Login cho password managers/paste, không CAPTCHA/memory test mới. Media riêng tư đi qua route Spring được bảo vệ, không external URL/CDN có CCCD/face data. Forced-colors dùng outline/border native, blur không phải điều kiện sử dụng.

## 12. Tailwind CSS v4 direction (chỉ tài liệu)

Giữ Spring MVC + Thymeleaf, không React/shadcn/Vite migration. Build-time CLI `tailwindcss` + `@tailwindcss/cli` v4 pin version/lockfile trong Phase2 sau duyệt; CSS output static được Spring phục vụ. Không Play CDN/runtime compiler. Input, build commands, lifecycle và class scanning chi tiết tại handoff.

```css
/* Ví dụ định hướng, CHƯA thêm vào ứng dụng. */
@import "tailwindcss";
:root {
  color-scheme: light;
  --ui-primary: #335CDA;
  --ui-text: #17213B;
  --ui-surface: #FFFFFF;
  --button-primary-bg: var(--ui-primary);
}
@theme inline {
  --color-primary: var(--ui-primary);
  --color-foreground: var(--ui-text);
  --color-surface: var(--ui-surface);
  --radius-panel: 1.5rem;
}
```

`@theme inline` cho aliases tham chiếu CSS variables; `:root` runtime semantics, không thay bằng v3 `@tailwind base` hoặc config JS `content` làm mặc định. Các token còn lại trong §2–5 phải được ánh xạ nhất quán khi thực hiện, snippet không phải file đầy đủ. Class Thymeleaf dùng chuỗi trọn vẹn/mapping tường minh, không nối `bg-${status}`; scan templates và JS, gồm fragments/live responses. Preflight phải QA native controls/table/dialog, không load CSS cũ toàn cục cạnh utilities mới.

## 13. Lucide Icons

Lucide outline SVG duy nhất, `currentColor`, stroke2, viewBox24. 16px inline/badge, 20px nav/control, 24px summary; hit target lớn hơn glyph. Gợi ý semantics: LayoutDashboard, Users, Car, CreditCard, MapPinned, History, Receipt, Settings, ShieldCheck, ScrollText, LogOut, Search, Plus, ChevronDown, X, LoaderCircle; kiểm tên ở version pin khi triển khai. Không emoji/FontAwesome trộn, không invent brand logo.

Ưu tiên subset SVG local render bằng Thymeleaf fragment/build-time assets với license ISC; không yêu cầu tải cả library ở browser. Nếu chọn vanilla JS cho dynamic fragment, chỉ khởi tạo trong subtree mới và giữ accessible attributes; API package phải đối chiếu version, không chép API React/v0. Decorative `aria-hidden=true`, standalone control có accessible name ở button, icon không nhận focus. Thông tin có nghĩa phải có text, không tooltip-only.

## 14. Anti-patterns / approval gate

Không kính 15–30% phía sau bảng/form, text low-contrast, transparency lồng nhau, background chuyển động, full-page blur, neon/3D chart, scale-on-hover mọi card, toolbar toàn icon khó hiểu, table cắt cột, modal dài thay page form, nút search/export giả, empty=0 khi API lỗi, live label không timestamp, fake backend approval/settlement, bỏ CSRF/RBAC, lưu PII/secrets vào localStorage/log/screenshot thật. Không tự giải quyết requirement P01–P09 bằng UI.

Branding & Login là slice Phase2 hiện được duyệt dựng và kiểm bằng browser; nghiệm thu thị giác vẫn do người dùng quyết định. Các nhóm sau vẫn chờ review riêng theo [handoff](PHASE-2-HANDOFF.md). Không tự áp assets/CSS mới vào header/sidebar cũ. Generator chỉ gợi ý; thay đổi theo yêu cầu người dùng không đồng nghĩa tự duyệt toàn bộ design-system.
