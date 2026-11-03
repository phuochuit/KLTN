# LumiPark Desktop — UX & Engineering Design Proposal

**Task 1 · 03/11/2026 · HISTORICAL WINFORMS RESEARCH.** Hướng công nghệ WinForms trong tài liệu này đã được thay bằng quyết định WPF + .NET 8 + XAML của chủ dự án; WPF prototype/direction được duyệt và công việc full implementation đang tiếp tục. Các observations/targets dưới đây là bằng chứng và đề xuất tại Task1, không phải kết luận WPF đã đạt parity.

Đề xuất dành cho nhân viên trạm gác, trên **C# .NET 8 WinForms** hiện hành. Chưa triển khai redesign, chưa thay API/nghiệp vụ/quyền/AI/thanh toán/barrier, chưa commit hoặc xuất bản. [Wireframes để duyệt](desktop-wireframes.html) là minh họa thiết kế, không phải ảnh ứng dụng đã triển khai. [Phụ lục khảo sát](desktop-current-state-audit.md) chứa inventory, findings và căn cứ source.

## A. Current-state audit

### Nền khảo sát và giới hạn

- Nhánh mới: `feature/desktop-ui-ux-redesign`, từ `feature/web-ui-redesign` tại `a4e175ec5d397591b6b684ce1d8035f86f12d3bd`. Kiểm tra remote và PR #6 ngày 03/11/2026: PR **OPEN**, chưa merge; `main` vẫn tại `0bed157`. Không dùng main làm nền tương đương vì main chưa chứa toàn bộ Web. [PR #6](https://github.com/phuochuit/KLTN/pull/6).
- Hai thay đổi local ở `application.properties` và `application-laragon.properties` được giữ nguyên, không chép nội dung vào nghiên cứu. Source Desktop và tests giữ nguyên.
- Đã đọc README, docs index, canonical Web MASTER, source toàn bộ các Form/Dialog/control Desktop, API client, queue, settings, simulator và hai test classes; đối chiếu contracts/NV07/security/architecture. `artifact.md` root không có; dùng [quy ước workspace](../architecture/workspace-layout.md) cho vị trí tài liệu và output. Không tự phục hồi file thiếu.
- Thực tế có login; main với hai tab vào/ra; sơ đồ chính + cửa sổ sơ đồ; slot detail; đối chiếu evidence + zoom; correction; thu phí; cấu hình; tổng kết ca; file picker và MessageBox. Không có màn hình tự mở/kết ca, quản trị cư dân hoặc mua gói như Web. Shift đến từ operator profile.
- Slot detail chỉ gắn tab **Điều phối xe vừa vào**. Các builder gán cư dân/đỗ nhờ/khóa/giải phóng còn trong source nhưng không được gắn vào UI; không tự đưa chúng trở lại trong redesign.

### Findings quyết định thứ tự triển khai

Không xác nhận P0. Có năm nhóm P1 cần xử lý hoặc chốt dependency trước nghiệm thu vận hành:

| ID | Mức | Điều quan sát được từ source | Hướng xử lý |
| --- | --- | --- | --- |
| D01 | P1 | Kết quả AI sau await chọn đích bằng tab hiện tại | Gắn kết quả với operation context bất biến, generation và evidence |
| D02 | P1 | Submit vào/ra/điều phối chưa có single-flight guard rõ | Khóa submit đồng bộ trước await; không tạo operation ID mới khi kết quả cũ chưa rõ |
| D03 | P1 | Clear sau 2 giây có thể xóa dữ liệu lượt mới/shared media | Chuyển sang **Lượt tiếp theo**, không xóa tự động vô điều kiện |
| D04 | P1 | Lỗi ghi có thể bị hiển thị là lỗi nhận dạng; queue tự replay chưa partition actor/origin | Tách trạng thái transaction/AI/queue; chưa xác nhận phải giữ và chuyển người có thẩm quyền đối soát |
| D05 | P1 | Chuẩn bị ảnh/video đồng bộ trước HTTP trên đường gọi UI | Tách media preparation khỏi UI thread, đo lại trước/sau |

P2 đáng làm: map refresh dựng lại controls, refresh chồng nhau, thiếu summary ở tab ra, payment xuất hiện khi xem trước lại, correction có ít loại xe hơn contract hiển thị, DPI/keyboard/resource ownership. P3 chỉ đồng bộ nhận diện/nội dung sau khi giải quyết nền tảng; không kéo dài nghiên cứu vì hiệu ứng trang trí.

## B. UX research & design rationale

### Áp dụng UI UX Pro Max có kiểm định

Tái sử dụng `.opencode/skills/ui-ux-pro-max/SKILL.md`, Python 3.12.10 và dữ liệu/scripts đã có; không cài package hoặc sửa config global. Đã đọc [README/cài đặt chính thức](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill#installation) và [Design System Generator](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill#design-system-command-advanced). Generator là input nghiên cứu; quyết định phải phù hợp station và canonical Web.

| Search thực hiện | Kết quả liên quan | Quyết định áp dụng/loại bỏ |
| --- | --- | --- |
| `parking operations dashboard desktop --design-system` | Minimalism & Swiss Style, low-cost rendering; đồng thời trả funnel/hero và font Fira | Nhận phân cấp thông tin/bề mặt đơn giản. **Loại funnel, hero, marketing CTA, đỏ làm CTA mặc định**, font code cho heading và breakpoint điện thoại |
| `industrial monitoring console --design-system` — một lần thu hẹp | Vẫn Swiss/minimal, nhưng pattern hero và typography generic không hợp | Không persist generator; không tiếp tục tìm vô hạn. Chọn layout từ task analysis và source, giữ palette canonical |
| `keyboard focus modal --domain ux` | Focus States, visible keyboard focus | Native focus, thứ tự Tab, trả focus cho control mở dialog; không sao chép CSS/ARIA vào WinForms |
| `loading feedback cancellation --domain ux` | Loading Indicators: phản hồi phù hợp thời gian chờ, giữ layout/focus | Pending text tại đúng vùng xử lý, không animation toàn cửa sổ, không progress % giả |
| `reduced motion animation --domain ux` | Reduced Motion, Excessive Motion | Không transition bố cục mặc định; không pulse/slide/shimmer trong luồng xe |
| `readable data dense --domain typography` | Dashboard Data: sans cho nhãn, mono cho dữ liệu | **Segoe UI** native; mono chỉ cân nhắc cho identifier khi đo cho thấy lợi ích, không thêm font tải mạng |
| `responsive UI dispatcher batching --stack wpf` | UI-thread affinity, async I/O, CPU background | Chuyển nguyên tắc sang WinForms SynchronizationContext/BeginInvoke; **không dùng WPF Dispatcher/XAML**, không migration |

### Phương án khuyến nghị: LumiPark Operations, nền sáng đặc

Một workstation tập trung vào **một lượt đang xử lý**, hai nguồn evidence và một vùng quyết định. Nhận diện kế thừa logo cổng LumiPark, navy, xanh primary và accent tím dùng rất ít. Bỏ blur/glass/bóng lớn, banner marketing và nút cùng màu/cùng độ nổi. Ảnh và thông tin biển số/phí được ưu tiên hơn sơ đồ tổng thể.

Không tạo phong cách thứ hai. Chế độ compact là cách bố trí khác của cùng design system, không phải theme mới. Chưa đề xuất dark mode: cần nghiên cứu riêng điều kiện sáng tại cổng và kiểm contrast ảnh/control.

## C. Screen-by-screen design

[Mở bộ wireframes](desktop-wireframes.html): 8 bảng minh họa có nhãn rõ; dữ liệu hoàn toàn giả lập, không chứng minh runtime.

### 1. Login và phiên

Form gọn, logo + “Trạm gác”, hai field có label, show password điều hướng được bằng bàn phím, primary “Đăng nhập” và “Thoát”. Không ảnh nền nặng. Giữ paste/password manager/native password control. Lỗi inline giữ username, không tiết lộ account tồn tại. Pending khóa submit/credentials, vẫn có thông tin đang chờ; thoát lúc đang xác thực cần lifetime guard.

Header sau login: trạm, nhân viên, ca hiện hành; từng trạng thái Spring/AI và “chưa kiểm tra” riêng. Không gọi health pass là tất cả thiết bị hoạt động. URL/config nằm ở tiện ích, không cạnh nút xác nhận. Khi session bị vô hiệu hóa: khóa thao tác ghi, bỏ evidence khỏi context sử dụng, trở lại login; không xóa queue hoặc coi pending mutation đã hủy.

### 2. Bàn vận hành vào/ra

**Wide:** header gọn → chọn Xe vào/Xe ra → vùng trái hai ảnh xe/mặt cùng source + status → vùng phải biển số, loại xe, người được phép, gói, phí/kết quả → footer hành động. Mini-map chuyển thành summary “ô trống/theo loại + mở sơ đồ”; không render toàn bộ tile thường xuyên trên màn hình chính.

**Compact:** theo chiều rộng client thực sau DPI: thanh context → biển số/vùng quyết định → hai ảnh cạnh nhau nếu đủ chỗ, nếu không xếp dọc → thông tin bổ sung cuộn. Action luôn nhìn thấy trong vùng riêng; không thu chữ để ép vừa. Mốc ban đầu 1180 logical pixels cho wide, dưới đó compact; đây là giả thuyết layout phải hiệu chỉnh qua QA, không phải support guarantee. Tại working area nhỏ hơn 900×600 logical cần kiểm chứng cuộn/khả dụng; không hứa mọi độ phân giải.

Chọn chế độ trước capture. Capture/lookup/verify giữ identity: operation kind + generation + normalized plate + selected member + evidence references. Completion cũ không được áp vào context mới. Đổi biển/người/nguồn ảnh làm invalid các kết quả phụ thuộc, hiển thị “Cần nhận dạng/đối chiếu lại”; không tự tái sử dụng verified state. Không sửa proof hoặc TTL của backend.

Luồng entry khuyến nghị: chọn nguồn → xử lý xe/mặt → kiểm biển/người/gói/evidence → xác nhận → kết quả có mã lượt và ô → **Lượt tiếp theo**. Giữ kiểm tra và confirmation nghiệp vụ. Parallel hiện hành là **chọn file xe + camera mặt**; nhãn đề xuất nói rõ điều này. Không quảng bá hai camera realtime liên tục vì không có nút exposed tương ứng.

### 3. Xe ra, phí và thu tiền

Xe ra có summary riêng, không dùng Label thuộc tab vào. Hiển thị biển, người, gói, lượt/giờ vào và trạng thái evidence. Tách rõ **Xem trước phí** (không thu tiền) → kiểm số tiền → **Xác nhận xe ra** → dialog thu phí nếu fee > 0 theo contract hiện tại. Preview lại không mở dialog thanh toán. Thay dữ liệu liên quan làm preview stale, bắt preview lại; server vẫn quyết định cuối.

Dialog: biển/mã lượt/phí nổi bật, CASH/BANK_TRANSFER/POS giữ nguyên; reference bắt buộc khi contract yêu cầu. Hai nút rõ “Hủy” và “Xác nhận thu và gửi yêu cầu xe ra”. Loại nhãn “Bỏ qua” trùng Cancel vì source hiện trả Cancel; không tạo khả năng bỏ qua phí. Không gọi operator confirmation là ngân hàng/POS đã settlement. Kết quả chỉ xác nhận sau authoritative response; receipt hiển thị khi có.

### 4. Evidence, zoom và correction

Lưới 2×2: xe vào/xe ra, mặt vào/mặt ra; có thời điểm/source/nhãn không có ảnh khi response cung cấp. Không bịa ảnh hoặc ghép cặp identity. Mở ảnh đầy đủ qua nút có tên/keyboard; zoom 40–400% theo khả năng hiện tại, không stretch méo. Ảnh hồ sơ/ảnh vào không đồng nghĩa bằng chứng liveness.

Correction giữ lý do code, biển/loại trước–sau, mô tả 10–500 ký tự và quyền hiện hành. Danh sách loại phải khớp canonical contract, giữ nguyên loại ban đầu nếu không sửa; không tự chọn UNKNOWN rồi ghi đè. Hiển thị “Chỉnh sửa để gửi kiểm tra”, không ngụ ý đã được server duyệt. Focus về trường liên quan khi lỗi; tránh modal chồng modal nếu có thể mở correction sau khi đóng comparison với context giữ nguyên.

### 5. Sơ đồ và chi tiết ô

Cửa sổ secondary: filter tầng/khu/loại, summary theo toàn bãi và theo filter có nhãn khác nhau, legend gồm text + hình dạng/màu, thời điểm cập nhật. Giữ snapshot cũ có cảnh báo stale khi đọc lỗi, không biến lỗi thành bãi trống. Một refresh hoạt động, trigger tiếp theo gộp; giữ filter/scroll/focus, cập nhật tile theo ID, dispose control bị loại.

Tab/arrow/Enter có đường thao tác thay chuột; nếu native tile khó tiếp cận, bổ sung danh sách DataGridView cùng snapshot để truy cập bàn phím, không đổi nghiệp vụ. Slot detail chỉ hiển thị thông tin assigned/occupied/borrowed và luồng điều phối session vừa vào hiện có; selection + xác nhận một lần, kết quả rõ. Không thêm gán cư dân/borrow/block/release chưa exposed.

### 6. Cấu hình, tổng kết ca, thông báo

Settings dùng label/helper/example, group camera xe/mặt và simulator. Giữ frame 1–60, auto-close 1–120 giây, driver chỉ SIMULATOR. URI có thể chứa credential: che thông tin nhạy cảm khi hiển thị/chụp báo cáo; không đưa vào log/tài liệu. Cấu hình sai có lỗi tại field, không báo “camera đã kết nối” nếu chỉ lưu cấu hình.

Tổng kết ca giữ entry/exit/doanh thu theo phương thức, occupancy/correction/reconciliation và local queue count hiện có; nhãn “Theo phản hồi hệ thống”, ghi thời điểm nếu có. Không thêm start/end shift, xuất báo cáo hoặc đối soát ngân hàng. Đăng xuất tường minh có summary theo hiện hành; không tự đổi đóng cửa sổ thành kết ca.

Thông báo có mức/info + hành động hợp lệ; lỗi giao dịch không ghi đè thành “AI thất bại”. Barrier riêng với nhãn **MÔ PHỎNG**, position/error; thao tác mở/đóng không làm transaction chuyển success. Không xác nhận thiết bị vật lý hoặc thêm quyền điều khiển.

### State và recovery contract của UI đề xuất

| State | Hiển thị và thao tác | Ranh giới an toàn |
| --- | --- | --- |
| Idle | Chọn nguồn/nhập dữ liệu, hướng dẫn bước tiếp | Chưa có evidence không hiển thị verified |
| Processing | Pending cục bộ, lý do disabled, elapsed nếu hữu ích | Một submit tại một context; context switch không nhận completion sai |
| Success | Response, mã lượt/receipt nếu có, “Lượt tiếp theo” | Không suy success từ queue/AI PASS/simulator mở |
| Error | Phân loại validation/auth/service/AI và field liên quan | Chỉ cho retry khi xác định thao tác chưa ghi hoặc retry read an toàn |
| Cancelled | Bỏ lựa chọn file/dialog; bỏ kết quả read không dùng | Dừng chờ client không chứng minh server dừng |
| Unknown outcome | “Chưa xác định kết quả — không gửi lại”, mã tham chiếu an toàn nếu có; giữ thông tin | Không cấp operation ID mới, không mở barrier dựa trên unknown; chuyển người có thẩm quyền đối soát |
| Queued / Needs review | Số pending + lần thử/lỗi đã có, tách khỏi success | Không discard/reorder/replay thủ công hoặc đổi actor/origin tùy ý |

**Dependency bắt buộc:** source hiện có auto-replay. Chỉ đổi text/disable nút không giải quyết được replay sau timeout, restart hoặc đổi tài khoản. Chưa xác nhận reconciliation API an toàn để đóng Unknown. UI chỉ đề xuất **giữ lượt chưa rõ, không xác nhận xe/thu tiền lần nữa, chuyển quản lý kiểm tra trạng thái qua quy trình được duyệt**; không tự đánh dấu resolved. Trước Build slice recovery cần chủ dự án duyệt policy actor/origin/session/evidence expiry và cách xác nhận kết quả. Nếu chưa duyệt, phần recovery giữ **BLOCKED**; không báo toàn bộ vận hành complete. Single-flight chỉ bảo vệ một context trong một process, không chứng minh chống trùng đa trạm.

## D. Desktop design system proposal

Kế thừa [MASTER Web](../../design-system/kltn/MASTER.md) về brand và semantic colors; thông số native dưới đây là **desktop proposal**, chưa ghi đè MASTER Web.

| Nhóm | Đề xuất native |
| --- | --- |
| Surface | Canvas #F5F7FF, surface #FFFFFF, soft #F1F5FF; opaque, border 1 logical px; không backdrop blur |
| Text | #17213B; muted #526078; primary #335CDA, hover #294BB8, selected #E8EEFF; white on primary |
| States | success #166534/#ECFDF3; warning #854D0E/#FFF7E6; error #B42318/#FFF1F0; info #1E40AF/#EFF6FF; luôn kèm text/icon |
| Boundaries | #7A879F cho input/focus có nghĩa; #D8E0EF chỉ divider. Focus native rõ, không owner-draw làm mất focus; High Contrast dùng SystemColors |
| Typography | Segoe UI 10–11pt body, 9pt metadata khi đọc được, 14–16pt section, 22–26pt biển/phí; 400/600/700; kiểm Vietnamese glyph/long labels |
| Spacing | 4/8/12/16/24 logical px; group 16–24; control ≥32 logical px cao, thao tác chính 40; không áp nguyên mobile 44pt |
| Density | Một primary tại mỗi bước; labels sentence case; biển số có tương phản/hierarchy, không paragraph ALL CAPS |
| Controls | Native Button/TextBox/ComboBox/TabControl/DataGridView/StatusStrip ưu tiên; TableLayoutPanel + Dock/Anchor/AutoSize/scroll body; không dựng lại toàn widget framework |
| Shapes/icons | Radius nhẹ 4–8 chỉ ở panel custom thật cần; native controls giữ rendering Windows. Một family line icon, raster đa kích thước hoặc vector render có sẵn phù hợp WinForms; không cài thư viện chỉ để bo nút |
| Images | Letterbox/Zoom giữ aspect; thumbnail có bounded size; zoom dùng owned copy hoặc borrowed lifetime rõ; không cache ảnh sinh trắc lâu hoặc xuyên phiên |
| Motion | Mặc định cập nhật tức thời, không layout animation/fade cảnh báo; spinner native chỉ khi pending thật, không pulse toàn màn hình |

Keyboard: Tab order theo thứ tự công việc; Ctrl+Tab đổi tab chỉ khi context an toàn; mnemonic Alt cho lệnh không phá hủy; không global Enter/F-key mở barrier hoặc xác nhận thu tiền. Dialog có Accept/Cancel theo mức rủi ro, không mặc định Enter gây commit khi người dùng đang sửa dữ liệu. Tile/image có control thao tác tương đương accessible; AccessibleName/Description/Role và selected/disabled state; focus quay về vị trí mở. Screen reader và High Contrast phải QA native, không coi WCAG Web là chứng chỉ WinForms.

DPI: giữ bootstrap hiện có, đánh giá SystemAware so với PerMonitorV2 trên .NET 8; chỉ chọn PerMonitorV2 sau spike đa màn hình. Chọn một chiến lược autoscale nhất quán, không nhân DPI thủ công lần hai. Dialog footer không chồng body, text wrap/scroll thay vì cắt. Microsoft phân biệt [autoscaling và project DPI mode](https://learn.microsoft.com/en-us/dotnet/desktop/winforms/forms/autoscale); không dùng cấu hình app.config của .NET Framework cho .NET 8.

## E. Performance & interaction plan

### Baseline đã có

| Quan sát 03/11/2026, Windows tại workspace | Kết quả | Ý nghĩa/giới hạn |
| --- | --- | --- |
| `dotnet build desktop-winform/ParkingGateDesktop.csproj --configuration Release --no-restore --verbosity quiet` | PASS, 0 warning/error; build elapsed 2.74s | Chỉ xác nhận compile; không là startup/UI speed |
| Mở executable vừa build, quan sát login bằng Windows screenshot/UI Automation | Login xuất hiện; focus username; password có accessible name; native buttons được expose | Không nhập credentials, không login, không camera/backend mutation; chưa kiểm Tab/Enter thực tế |
| Một sample process login idle | Working set 62,160,896 bytes (~59.28 MiB); private bytes 15,785,984 (~15.05 MiB); CPU tích lũy 0.90625s; handles 370; Responding=True | Một thời điểm sau startup; CPU không phải utilization %, không có before/after, không đại diện main/AI/map |
| Startup-to-first-paint, UI latency, tab/dialog/image/map, 100/125/150% DPI, screen reader | **NOT VERIFIED** | Chưa có harness cô lập authenticated main; không dùng backend/queue operational để lấy số |
| Existing tests | Source có 4 outbox facts + 8 status-code rows | Không chạy tests trong Task 1; không gán PASS từ lịch sử |

Đã đóng process login do lượt nghiên cứu mở. Không thay DPI Windows, không mở phiên gate operational. Windows có thể chạy WinForms, nhưng runtime đại diện còn cần fixture an toàn ở Task 2.

### Bottleneck và kỹ thuật có căn cứ

1. **UI CPU/I/O:** ShowLocalImage, Base64/Bitmap, resize/JPEG/file đọc đồng bộ trên đường UI. Dùng bounded worker cho preparation/decode, await I/O; publish result đúng UI thread, bỏ stale generation, dispose bitmap bị bỏ. Giữ 960px/Q90 hiện hành đến khi có quality/size measurements. Không đưa mọi API I/O vào Task.Run, không thay threshold/quality AI để đẹp benchmark.
2. **Map:** đã có SuspendLayout; thêm gộp refresh/filter update, một snapshot render, keyed update/disposal. Chỉ virtualize hoặc custom canvas nếu số slot/thời gian đo chứng minh cần; double buffering chỉ custom paint có flicker được xác nhận, không đặt cờ reflection cho mọi control.
3. **Lifetime:** cancel/discard read khi đóng/chuyển context; mutation pending có Unknown nếu mất response. Không dùng .NET 9 `Control.InvokeAsync` trên .NET 8; dùng captured context/BeginInvoke có handle/dispose guard. [Microsoft: thread-safe calls](https://learn.microsoft.com/en-us/dotnet/desktop/winforms/controls/how-to-make-thread-safe-calls), [double buffering](https://learn.microsoft.com/en-us/dotnet/desktop/winforms/advanced/how-to-reduce-graphics-flicker-with-double-buffering-for-forms-and-controls).

### Đo lại có kiểm soát trong Task 2

Fixture HTTP/AI fake với media tổng hợp, account/queue/settings test riêng, không dùng LocalAppData operational, không Spring/ANPR thật, không barrier vật lý. Đây là **harness cần xây**, chưa có coverage đó. Đo Release cùng hardware/OS/build/DPI/power plan, tách cold/warm; ghi commit/run ID/sample count/median/p95/max, không chỉ một stopwatch.

| Kịch bản | Phép đo và điều kiện hoàn thành dự kiến |
| --- | --- |
| Startup/login/main | 5 cold + 10 warm; process start→Shown/first paint tách riêng; báo số thực, chưa đặt budget startup khi thiếu baseline |
| Feedback/AI pending | 30 lần/kịch bản: input→state paint, pending hiển thị trong khi fake delay 0.2/2/10s; mục tiêu **100ms feedback, 200ms pending**, chưa phải SLA; đo paint/message-loop, không chỉ assignment timestamp |
| Tab/dialog | 30 lần warm; focus, state, no wrong-context completion; không tăng p95 quá 10% so baseline ổn định, giải trình chênh nhiễu trước chốt budget |
| Image | Synthetic 1080p/4K/ảnh lớn hợp lệ và lỗi decode; prep/decode/network/render đo riêng; UI heartbeat 50ms và delay p95/max; quality evidence phải giữ |
| Map | Dùng slot counts thực khi fixture xác nhận + stress lớn hơn; 30 refresh và 100 vòng mở/đóng; filter/scroll giữ; GDI handles/private bytes không tăng tuyến tính sau dispose/settle |
| Reliability | Delayed/reordered responses, double click, tab/member/plate switch, form close, timeout trước/sau server commit, 401, 5xx, expired evidence; assert không false success/barrier hoặc payment retry |
| Native | 1366×768 và 1920×1080 ở 100/125/150%; working area/taskbar; monitor change nếu có; all critical controls reachable, không che focus/phí/CTA; keyboard + Narrator + High Contrast |

Độ trễ server/AI và camera/network đo riêng bằng correlation/elapsed khi có; upload preparation và render thuộc client. Không quy mọi thời gian nhận dạng thành UI chậm. Không tuyên bố tăng hiệu năng khi chưa có paired baseline/candidate. Không thay resolution Windows hay mở camera thật tự động để hoàn thành bảng.

## F. Implementation roadmap

Sau **human design acceptance**, Task 2 mới bắt đầu: Codex giao finding cụ thể → OpenCode Build và self-check → Codex đọc diff/kiểm thực độc lập → targeted fix tối đa hai vòng mặc định → đóng finding hoặc ghi blocker. Chỉ một Builder ghi source. TDD/focused tests cho logic/contract; ảnh review không thay runtime test.

| Slice | Phạm vi/dependencies | Done và Codex independent check |
| --- | --- | --- |
| S0 — fixture/baseline + recovery decision | Injectable safe HTTP, queue/settings riêng; chốt actor/origin/Unknown/expiry policy; chưa đổi contract | Chứng minh không gọi operational services/state; reproduce D01–D04 an toàn; lưu baseline. Recovery chưa chốt → BLOCKED phần đó |
| S1 — operation state safety | Main context/controller nhẹ tách view; D01–D03, preview/payment composition, canonical type | Delay/reorder/double-click/next-operation tests; preview không thu tiền; đúng evidence/member; không bỏ server RBAC |
| S2 — media/render lifecycle | API prep + image ownership; D05, generation cancellation; không đổi AI params | UI stall/quality/RAM trước–sau, late decode disposed, close safe; independent review UI thread boundary |
| S3 — shell + vào/ra + payment | Tokens/native controls, wide/compact, visible exit summary; phụ thuộc S1 | Screenshots + native keyboard/DPI + valid/error/pending states; preserve fee/receipt/evidence behavior |
| S4 — evidence/correction + dialogs | Comparison/zoom/settings/summary; không thêm hidden slot functions | Accessible focus/labels, exact existing fields/validation; repeated image dialog resource soak |
| S5 — map/dispatch | Coalesce/diff update/dispose; phụ thuộc baseline & S1 guard | One render/snapshot, stale read ignored, selection retained; read/write failure scenarios; no duplicate dispatch |
| S6 — final targeted QA/handoff | Tất cả exposed surfaces, unresolved P1 review | Retest changed regions, isolated contract tests, native DPI/a11y/perf evidence; NOT VERIFIED cho thiết bị/settlement/live tests; chỉ sau duyệt mới commit tiếng Việt/push/PR trong Task 2 |

Không hứa thời gian triển khai khi recovery policy và fixture chưa chốt. Ưu tiên nền tảng trước visual; không migration framework, dependency UI lớn hoặc tối ưu không đo được.

## G. Decision record và điểm duyệt

| Quyết định đề nghị chủ dự án duyệt | Trade-off |
| --- | --- |
| **D-01:** Native WinForms, nền sáng đặc, brand Web có chọn lọc; Segoe UI | Ít hiệu ứng hơn Web, dễ đọc và ít chi phí render |
| **D-02:** Một context vào/ra, hai evidence + decision pane; map secondary/summary | Thêm một thao tác mở map đầy đủ, dành chỗ cho thông tin quyết định |
| **D-03:** “Lượt tiếp theo” thay clear tự động sau 2 giây | Thêm một activation có chủ đích, tránh xóa nhầm và giữ receipt |
| **D-04:** Payment nằm ở confirm, re-preview không thu; “Bỏ qua” hợp nhất Cancel | Làm rõ hành vi hiện có; không bỏ qua phí, không thay settlement/RBAC |
| **D-05:** Roadmap S0→S6; recovery là dependency được duyệt riêng | Không gọi queue thành công chỉ nhờ UI; cần decision có thẩm quyền trước sửa replay |
| **D-06:** Wide/compact cùng design, DPI spike trước chọn PerMonitorV2 | Thêm QA layout; không cam kết support chỉ từ mockup |

**Các câu hỏi recovery cần chủ dự án chốt ở S0:** ai được xác minh/đóng lượt Unknown; nguồn authoritative nào được dùng; operation nào được replay sau thay actor/origin/session hoặc hết evidence; cách xử lý khi chưa có endpoint đối soát phù hợp. Tài liệu này không tự chọn policy hoặc mở rộng quyền. Có thể duyệt direction/layout và roadmap trước, nhưng chưa đủ duyệt vận hành recovery.

### Independent review và tự phản biện

Hai Codex subagents khảo sát hai nhóm source độc lập; Codex đối chiếu trực tiếp các đoạn P1 và Request/payment. Một lượt OpenCode **Plan**, model catalog `openai/gpt-6-luna`, phản biện concept không đọc lại source, không Build: chỉ ra Unknown thiếu đường đóng an toàn, single-flight không chống trùng xuyên restart/đa trạm, evidence lifetime và DPI targets chưa chứng minh. Các điểm đã được đưa thành dependency S0, state/lifetime rule và giới hạn baseline ở trên. Catalog có Terra nhưng không gọi thêm model vì không cần lặp nghiên cứu. Không sửa global/project agent config. CodeGraph có database local nhưng chưa xác nhận index Desktop đủ mới; căn cứ acceptance dùng source trực tiếp.

Review cuối: agent kiến trúc đọc lại proposal/audit không thấy vấn đề material cần sửa. HTML 8 boards được render bằng Edge có sẵn; không có page error hoặc tràn ngang ở viewport 1600 và 900px; Codex đã xem ảnh main/payment/compact. Local file links hợp lệ. Đây chỉ là kiểm tra tài liệu minh họa, không phải kiểm thử WinForms. Source Desktop/tests không có diff; hai local config overrides khớp hash đã ghi nhận. `AGENTS.md` xuất hiện thay đổi ngoài lượt chỉnh tài liệu này và được giữ nguyên.

Codex không coi “Plan không tìm thêm lỗi” là PASS runtime. Proposal đủ căn cứ để duyệt **phương án và roadmap**; triển khai/recovery/runtime acceptance vẫn ở Task 2. **Dừng tại đây, chờ human design acceptance; không tự chuyển Task 2.**
