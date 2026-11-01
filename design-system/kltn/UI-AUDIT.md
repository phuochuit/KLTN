# Web UI audit — Phase 1

[MASTER](MASTER.md) · [Inventory và bindings](SCREEN-INVENTORY.md) · [Phase2](PHASE-2-HANDOFF.md)

## Method / limits
Audit source tĩnh toàn bộ19 HTML templates, app.css/app-dialog.js và controller/security/service contracts có liên quan; revision và links ở inventory. Không có browser screenshot, runtime viewport, keyboard/screen reader/performance test, không DB introspection. Ảnh reference không truy cập được. Findings dưới là evidence source + risk cần QA, không khẳng định lỗi tái hiện trong browser. Không chấm aesthetic score giả.

## Findings ưu tiên

P0 = gate về trust/security/financial correctness trước nghiệm thu luồng liên quan; P1 = cản trở tác vụ/accessibility; P2 = consistency/maintainability. Priority không cấp quyền sửa backend trong Phase2 UI-only.

| ID / ưu tiên | Evidence source | Vấn đề / ảnh hưởng | Direction và cách kiểm Phase2 |
| --- | --- | --- | --- |
| A01 P0 | registrations/households `scanQrBlob`, SecurityConfig.csrf | fetch multipart QR chỉ file, endpoint cần CSRF; khả năng403 thay vì đọc QR | UI request dùng token/header Spring thật, check session/403/HTML error; **không exempt CSRF**; synthetic QR runtime test |
| A02 P0 | SecurityConfig account read vs actions; M.accounts; account template | SHIFT_MANAGE/audit-read được page nhưng form create/reset/profile/PII vẫn render; quyền ghi/ảnh hẹp hơn page | action visibility theo grants hiện có, test direct denied POST giữ nguyên; model data minimization/RBAC redesign là backend approval gate, không hide=secure |
| A03 P0 | A.revenueFilter keys; dashboard renderStats; P.page/reports | “Hôm nay/Tháng” bị thay bằng cùng tổng kỳ; dashboard fees/subscriptions không ledger đối soát | nhãn/kỳ/lineage rõ, không báo settled hoặc known-total PASS. Boundary/timezone/aggregation fix cần backend scope riêng; test sums/selection với fixtures |
| A04 P0 | A.saveCard saves card/subscription trước ledger; không enclosing controller transaction | lỗi payment không bảo đảm rollback toàn bộ; UI retry dễ tạo lịch sử/thu trùng | không auto-replay POST; response error nói kiểm tra trạng thái trước thử lại; atomicity/idempotency cần task riêng, không hứa được UI giải quyết |
| A05 P1 |10 full/error templates thiếu viewport; shared form-grid4 cols; price-grid2; dashboard stats5 | mobile/reflow không nhất quán; columns không adapt | shared head viewport,1→2 field columns theo available width, QA320/375/768/1024/1440 + zoom; chưa đo overflow thật |
| A06 P1 | dashboard chart wrapper div mouse/click; slots `.slot-card` onclick div; custom modal/tab scripts | keyboard không có tương đương, focus không quản lý ở slots | button semantics, table fallback, named dialog, tab arrows/Escape/return; test keyboard toàn luồng |
| A07 P1 | dashboard catches console.error, fmtNum fallback0; account polling silent catch/3s replace | lỗi bị hiểu “không doanh thu”; live stale không báo; refresh có thể mất focus | loading/empty/error/stale riêng; no-zero-on-error; pause refresh/focus-safe updates, request sequencing; simulate timeout/403/offline |
| A08 P1 | form @RequestParam/manual errors; upload-error history.back; slots có flash error nhưng chỉ render message | mất context/recovery; errors không sát field; server error có thể không được hiển thị | inline/summary khi có dữ liệu thật, render flash error, safe retry; giữ non-sensitive values cần backend support riêng, không fake BindingResult |
| A09 P1 | pricing labels đứng riêng không for; payments note chỉ placeholder; tab div không ARIA | thiếu accessible name/relationship; validation khó tìm | connected labels/describedby/invalid; error summary focus khi submit; semantic controls thay ARIA vá tùy tiện |
| A10 P1 | app.css pulseRed/pulseAlert infinite; smooth scroll member/vehicle; không reduced-motion shared | distraction và vestibular risk | static overdue badge/icon, reduced-motion, không animate blur/layout; QA OS emulation |
| A11 P1 | slots page wording realtime nhưng không polling; unassignedSessions lấy latest30 OPEN; summary counts từ allSlots | nhận thức sai freshness/phạm vi filter/danh sách dispatch | ghi “khi tải trang”, summary ghi toàn bãi khác grid lọc; không bảo đảm unassigned từ tên model; backend eligibility gap bàn giao riêng |
| A12 P1 | registration VEHICLE_MANAGE→QR RESIDENT_MANAGE→cards CARD_MANAGE; login redirects `/` | custom role đứt luồng/403 sau save/login | test grants giao cắt, giải thích destination/quyền, không tự thêm quyền hoặc đổi redirect policy |
| A13 P2 | header horizontal dropdown; thiếu active-page; tables không có shared toolbar/paging; nhiều form trước list | tìm chức năng/bản ghi chậm; quá nhiều task trên page | sidebar grouping giữ source, selected state, table-first management + explicit create/edit; search/paging chỉ khi contract đủ, không claim đã có |
| A14 P2 |app.css globals + inline styles dashboard/accounts/roles/audit/login/password/error; stats-grid chỉ định nghĩa dashboard | style drift, khó thay toàn bộ; một số classes không shared | tokens + shared fragments/head/components, isolated new shell theo wave, không CSS override stack chồng |
| A15 P2 | emoji slots/nav symbols, small status text, color gradients; no shared toast | inconsistent icon/status visual, một số control màu chưa được đo | Lucide outline, semantic tint+text, opaque data surfaces, contrast kiểm numerically+runtime |
| A16 P2 | household/member/vehicle form IDs và JSON edit/reset/camera | change/reset có nguy cơ carry capture hoặc nhầm create/edit context; native fields mất data khi chuyển | rõ record identity, explicit cancel/reset, clear capture/preview, stop tracks; dirty warning nếu phù hợp, không persist PII drafts |

## Pattern grouping / quyết định

| Nhóm thực có | Pattern chung / hướng mới | Override? |
| --- | --- | --- |
| Dashboard | Bento overview + period chart drill-down + full table | [dashboard](pages/dashboard.md): kỳ/snapshot, keyboard chart |
| Registrations, household media | long multipart form, QR/camera/upload sections | [registration](pages/registration.md): capture và linked workflow |
| Households, vehicles, cards | management list + explicit form region + detail facts, labels/actions nhất quán | MASTER; không3 file page riêng; dùng media override cho household vùng liên quan |
| Slots | operational summary + semantic grid + dispatch dialog/batch | [slots](pages/slots.md): states và geometry/focus |
| Payments, sessions, audit | table full width, tiền/time/ID rõ; audit giữ server paging | MASTER; không file riêng; reconciliation action dùng shared confirm |
| Pricing, settings | grouped form; effective values, units, readonly rõ | MASTER; không biến pricing thành KPI wall |
| Accounts/live sessions/shifts | table+profile forms, live regions có pause/stale, secret warning | [account-management](pages/account-management.md): refresh/PII/secret |
| Roles | permission fieldsets, readable groups, native multi-checkbox semantics | MASTER; không quyền mới hoặc collapse che selected errors |
| Login, password, error, upload-error | standalone auth/feedback variant hoặc recovery trong shell, cùng tokens | MASTER; không shell nav cho anonymous/password-required |

## Gaps không được biến thành màn hình/capability
Requirement docs còn consent/approval, renewal/debt/refund, fee versioning, deep investigation/export, camera health/alerts và charging variant chưa được source screen này chứng minh. Không tạo nút “Duyệt hồ sơ”, “Hoàn tiền”, “Xuất Excel”, “Xác minh danh tính” hoặc charging dashboard chỉ để lấp Bento. Ledger/receipt JSON tồn tại không đồng nghĩa có screen riêng. Backend fixes, policy role matrix, period semantics/cutoff và payment recovery là approval riêng.

## UI audit exit
Đã có inventory đầy đủ file/route/action/asset và source evidence; đã đưa direction vào MASTER/4 overrides. Chưa có usability sessions với người dùng, ảnh reference comparison, rendered contrast/accessibility/responsive hoặc performance measurements. Những checks này là tiêu chuẩn Phase2, không dùng source audit để ghi PASS.
