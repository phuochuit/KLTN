# Desktop current-state audit — Task 1

**03/11/2026 · source `a4e175e` · static evidence trừ login được ghi rõ.** [Proposal](desktop-ux-proposal.md) · [Wireframes](desktop-wireframes.html).

## Inventory thực tế và effort

Đếm command activations theo source; không tính từng ký tự, chuyển focus, chọn file hoặc chọn member như một số click cố định. Không phải time-and-motion study với nhân viên thực. P1: có thể làm sai context/trạng thái hoặc ảnh hưởng thao tác vận hành; P2: cản thao tác/khả năng phục hồi/khả dụng; P3: polish. Không P0 đã xác nhận.

| Surface/flow | Tầm quan trọng, thao tác hiện tại | States hiện có / vấn đề | Source |
| --- | --- | --- | --- |
| Login | Critical; điền 2 field + 1 submit, reveal tùy chọn | Có busy/inline auth/network/timeout, Enter/Escape; reveal TabStop=false. Chỉ màn này được quan sát runtime, chưa submit | DesktopLoginDialog.cs:7–12,83–134 |
| Shell/session/shift | Critical; service check, logout, config, map, barrier mỗi lệnh một activation | Header nhiều hành động cạnh tranh; profile cung cấp shift; heartbeat 2s, outbox 15s, clock 1s. Không start/end shift UI | MainForm.cs:20–23,117–177,851–930 |
| Media chung | Critical; ảnh/video/camera xe và mặt, parallel, click ảnh zoom | Vehicle/face busy text + cursor, chưa khóa context. “Parallel” chọn file xe + camera mặt; CaptureBothCamerasAsync có method nhưng chưa exposed button | MainForm.cs:105–115,237–305,1062–1097 |
| Xe vào | Critical; camera xe, lookup, camera/verify mặt, confirm = 4 commands + chọn member nếu cần; parallel giảm lệnh nhưng thêm file picker | Pending media có; write không single-flight. Summary owner/package chỉ ở đây. Clear sau 2s | MainForm.cs:208–220,381–443,940–968 |
| Xe ra | Critical; chọn tab + capture + lookup + verify + preview + confirm + thu phí = 7 commands nếu phí>0; 0 fee bớt 1 | Có kiểm preview plate; thiếu summary owner/package riêng. Cancel payment dừng submit. Re-preview có thể mở payment | MainForm.cs:223–234,446–500,621–644 |
| Comparison | High; mở/đóng + correction hoặc zoom tùy nhu cầu | 2×2 images, badges, missing image, nested correction. Không chứng minh match/bio thật chỉ từ UI | EvidenceComparisonDialog.cs:108–208 |
| Zoom | High; click ảnh, −/+25%, reset; 40–400% | Scroll, image resizing; cần keyboard command, resource/large image check | EvidenceComparisonDialog.cs:282–354 |
| Correction | Critical; mở + confirm, nhập reason/biển/type/details | Validation 10–500; Cancel giữ flow; type có 3 lựa chọn so với nhiều loại current contract | ManualOverrideDialog.cs:25–82 |
| Payment | Critical; CASH mặc định; bank/POS thêm reference; confirm hoặc cancel/skip | Native Accept/Cancel; reference required. Skip=Cancel, không có bypass fee. Chưa có settlement integration evidence | PaymentMethodDialog.cs:27–89 |
| Mini-map và map window | High; mở map 1, filter/reload tùy nhu cầu | Floor/zone/type, snapshot summary, tile; render toàn bộ, overlap refresh/DPI risk | MainForm.cs:724–797; ParkingMapForm.cs:22–183 |
| Slot tile/detail/dispatch | High; mở tile + select session + dispatch + dismiss success: 3 command/dismiss activations, ngoài selection | Summary assigned/occupied/borrowed, chỉ tab điều phối. Pending/duplicate guard còn thiếu | SlotControl.cs:45–120; SlotDetailDialog.cs:88–112,182–244,328–344 |
| Station settings | Medium/high; mở + save, sửa field và dismiss warning khi sai | Camera sources, frames1–60, close1–120s, SIMULATOR; validation; chưa chứng minh camera network hoạt động khi save | StationSettingsDialog.cs:5–74; GateStationSettings.cs:18–50 |
| Shift summary | High; explicit logout + acknowledge nếu summary có | Số liệu server + local outbox; previous shift tự mở khi ID đổi. Close window không cùng summary journey | ShiftSummaryDialog.cs:34–63; MainForm.cs:176–177,886–930 |
| Barrier indicator/actions | Critical theo vận hành, hiện simulator; mở/đóng mỗi lệnh1 | Không confirmation trong main, auto-open sau response hợp lệ theo current logic; simulated delay120ms, không feedback hardware | MainForm.cs:151–153,799–831; BarrierController.cs:15–34 |
| File picker/MessageBox | Supporting; chọn/hủy file, warning/error/info/confirm | Native modal; Cancel trước gửi khác timeout sau gửi. Không đổi toàn bộ thông báo thành toast biến mất | MainForm.cs:237–258,459–483,1001–1020 |

**Không exposed:** BuildResidentTab, BuildBorrowTab, BuildManagementTab/handlers tương ứng chưa được gắn trong SlotDetailDialog.BuildUi (chỉ BuildGuestTab tại110). Không đưa vào inventory màn đang dùng hoặc tự bật trong redesign. Không có live video wall/physical barrier/system settings screen mới.

## Findings và điều kiện đóng

| ID/mức | Căn cứ source và rủi ro | Điều kiện đóng ở Task 2 |
| --- | --- | --- |
| D01/P1 | MainForm:267–271,308–319,115,1062–1063: await dùng operation lúc bắt đầu nhưng apply dựa selected tab mới | Delayed response + switch/restart capture không viết vào context khác; snapshot evidence/member/plate kiểm được |
| D02/P1 | MainForm:218,232,381–401,459–500; SlotDetailDialog:200,328–339: không guard/disable submit | Double click chỉ tạo một pending attempt/context; test scope local, không claim đa trạm |
| D03/P1 | MainForm:395–398,494–497,940–996: delay clear shared state | Next vehicle/tab đổi không bị clear từ completion cũ; result giữ tới acknowledge |
| D04/P1 | MainForm:1005–1020 generic AI error; API:187–215,307–328; Outbox:12–30 shared store chưa actor/origin partition | Chốt policy Unknown/replay; fault injection sau commit mất response; không false success/barrier/resubmit. **Policy dependency**, chưa fix bằng text |
| D05/P1 | API:330–360,382–394,415–466; MainForm:265,320–326,1049–1053 prep/decode đồng bộ đường UI | Đo stall trước/sau synthetic media; preparation background + correct ownership, không đổi chất lượng evidence |
| D06/P2 | MainForm:404–443 cập nhật _residentInfo nhưng chỉ gắn entry:216–217 | Exit owner/package/authorized member summary có thể đọc trên đúng tab, per-context |
| D07/P2 | MainForm:451 gọi Request; Request:621–637 mở payment nếu lastPreview fee>0 | Preview/re-preview không mở collection dialog; confirm fee>0 vẫn giữ method/reference validation |
| D08/P2 | ManualOverrideDialog:35–41 chỉ MOTORBIKE/CAR/UNKNOWN; MainForm:1100–1108 hiển thị các loại khác | Canonical values source đối chiếu, type không sửa giữ nguyên, không bổ sung policy loại mới |
| D09/P2 | ParkingMapForm:53,57,63,87–162: combo events + explicit render; Controls.Clear không dispose removed controls | One render/snapshot, preserved filters/focus, GDI/private memory không tăng tuyến tính trong soak |
| D10/P2 | ParkingMapForm:26,29–38,78,87–117: multiple reload triggers, thiếu request version/lifetime guard | Single-flight/coalesce, late response không overwrite newer hoặc touch disposed form |
| D11/P2 | MainForm:86–94,184; Comparison:21–36; SlotDetail:25–29,45–85 fixed sizes/header | Native 100/125/150% ở working areas định trước không che CTA/phí/focus; chưa observed clipping |
| D12/P2 | Login:9 reveal TabStop=false; MainForm:107–114 và SlotControl:57–69 mouse-only handlers | Keyboard paths tới reveal/zoom/tile, accessible names/focus restore; Narrator QA |
| D13/P2 | SlotControl:100,106 tạo Font; Comparison:246–251,368–379 bitmap/font lifetime chưa rõ | Ownership audit + repeated open/close; không gọi measured leak trước có evidence |
| D14/P2 | MainForm:353–378 swallow queue errors; API:192–213 stops first failed item; Outbox:75–107 quarantine/error | Pending/needs-review hiện rõ; quarantine không thành sync success; không tự thêm delete/reorder/retry |
| D15/P3 | MainForm:143–161,1144–1148 branding/header/actions | Tokens/hierarchy đồng bộ proposal sau safety, không trì hoãn nghiệm thu vì shadow/radius |

## Controls và nền đã tốt cần giữ

Long-lived HttpClient/redirect disabled (ParkingApiClient:24–29), HTTPS nonloopback và authenticated-origin binding (:559–565,601–616), serialized refresh/safe-read-only retry401 (:458–512), outbox operation-ID dedup/temp-file swap/base64-face rejection (OfflineOutboxStore:33–52,110–122). Không quy `async` là mọi công việc đã ra background; không bỏ các guard backend để giảm số click.

Main image replacement đã dispose bitmap cũ (MainForm:325,614,945–948); map có SuspendLayout và unsubscribe event khi đóng. Không đưa các khẳng định “không dispose gì”, “không batch layout” hoặc “DPI không hỗ trợ” vào kết luận.

## Coverage và trạng thái kiểm chứng

Current tests: **12 xUnit cases** theo source: 4 facts OfflineOutboxStoreTest (dedup, reject base64, quarantine, failure payload) và 8 theory rows TransientServerExceptionTest (4 transient5xx/4 permanent4xx). Không chạy trong Task1. Chưa chứng minh HTTP replay, auth refresh, crash/disk-full/multiprocess, cross-login recovery, barrier order, payment correctness, UI hoặc DPI.

Runtime login screenshot/accessibility + một process sample được root quan sát; build Release hiện tại PASS. Tất cả journey authenticated, latency/render/map/device, keyboard traversal và DPI matrix **NOT VERIFIED**. Không dùng mockup hoặc build để thay bằng chứng này. Xem baseline và QA plan trong [proposal](desktop-ux-proposal.md#e-performance--interaction-plan).

Nguồn authority: [contracts](../api/contracts.md), [NV07/recovery](../requirements/nv07-exceptions-recovery.md), [security](../security/overview.md), [architecture](../architecture/system.md), [Web MASTER](../../design-system/kltn/MASTER.md). Báo cáo này mô tả source và recommendation, không đóng các policy P06/P07/P09 đang mở trong tài liệu dự án.
