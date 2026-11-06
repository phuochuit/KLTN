# Vai trò & quyền hạn — G4B-ROLE-FE

**Status:** Đây là ghi nhận hiện trạng sau triển khai và visual review ngày 2026-10-29; UI/UX vẫn chờ người dùng nghiệm thu. Bản ghi này không tự tạo quyết định nghiệp vụ, không xác nhận nghiệm thu, và không thay [MASTER](../MASTER.md). Trang kế thừa LumiPark Glassmorphism 2.0 Light cùng dashboard shell hiện có.

## Cấu trúc và cách trình bày

- `/roles` dùng một vùng làm việc gồm danh sách vai trò và một contextual editor; không lặp checkbox wall dưới danh sách. Trên desktop hai vùng đứng cạnh nhau; ở tablet/mobile chuyển sang một cột. Nhóm quyền giữ cùng editor, theo năm miền: Tổng quan; Hồ sơ & tài khoản; Vận hành bãi; Tài chính & lịch sử; Bảo mật & riêng tư.
- Thẻ tổng quan và danh sách cho biết số vai trò, vai trò hệ thống, trạng thái hoạt động và số tài khoản đang gán. Nhãn mã vai trò và loại hệ thống vẫn là dữ liệu dễ nhận biết; nội dung form/control giữ nền gần opaque trên các panel kính.
- Editor có trạng thái tạo/chỉnh sửa, bản nháp chưa lưu, lỗi/thành công và tác động phiên. Chọn vai trò khác hoặc hủy khi có thay đổi sẽ hỏi giữ hay bỏ bản nháp.
- Giữ shell điều hướng hiện có; trạng thái `/roles` active được cuộn vào vùng nhìn thấy trong sidebar và drawer. CSS chỉ áp dụng dưới `.security-roles-page`.

## Invariants phải thể hiện trung thực

- Mã vai trò và tiền tố nhân viên nhập khi tạo nhưng không sửa sau đó. Trạng thái hệ thống và số tài khoản đang gán được nêu riêng; không thể tắt vai trò hệ thống hoặc vai trò còn tài khoản gán. Khi control bị khóa, trạng thái enabled hiện tại vẫn được gửi bằng hidden value.
- Tập quyền là đúng các checkbox được chọn; bỏ chọn tất cả là hợp lệ và tạo được role với tập quyền rỗng. Chú giải chung chỉ nói các quyền được chọn được lưu vào cấu hình vai trò; không gợi ý rằng mọi role có cơ chế cấp quyền đặc biệt.
- Cảnh báo chỉ hiện khi đang chỉnh `MANAGEMENT`, với nội dung thân thiện: “Quản trị viên (MANAGEMENT) có cơ chế cấp quyền quản trị riêng. Vì vậy, bỏ chọn một quyền bên dưới không nhất thiết sẽ ngăn tài khoản Quản trị viên truy cập chức năng tương ứng.” `ACCOUNT_MANAGE` và `ROLE_MANAGE` vẫn được giữ bắt buộc trong editor; cảnh báo mô tả tác động thực tế mà không đổi bootstrap/bypass.
- Mọi lần cập nhật role làm thu hồi session của các tài khoản đang được gán role, kể cả chỉ đổi tên hoặc mô tả; thông báo yêu cầu đăng nhập lại. Không chỉ cảnh báo khi đổi permissions.
- Ghi chú danh sách nhấn mạnh ẩn action ở UI không thay thế authorization phía máy chủ. Giữ nguyên POST routes, CSRF field, permission names và service/RBAC behavior.

## Tương tác và responsive

- Nút chọn role công bố lựa chọn bằng `aria-pressed`; live status báo trạng thái chọn/chỉnh sửa và draft. Checkbox permission vẫn dùng thao tác bàn phím native; Space được kiểm tra trong browser review. Focus-visible có outline, các action có scroll margin; reduced-motion, reduced-transparency và forced-colors có fallback CSS.
- Ở chiều rộng nhỏ, directory/editor, các nhóm quyền, form fields và action buttons xếp lại; tại 320 CSS px phần overview/action cũng chuyển thành một cột. Không chủ ý tạo horizontal scroll cho trang.
- Loading chỉ được ghi nhận như trạng thái trình bày trong review; request ở screenshot loading đã bị hủy trước POST. Tạo role với tập quyền rỗng và thử lỗi mã trùng được gửi tới fixture H2 cô lập.
- **History-restored draft recovery decision (2026-11-02, before implementation):** when an unresolved native `/roles/create` POST navigates away and the user returns through browser history, retain only the current tab’s minimal non-sensitive role-form values/context in session-scoped tab storage, then restore them only into the current server-rendered create/editor mode after reapplying the current server role identity, enabled/assigned state, management-permission locks, and permission list. Do not persist CSRF, credentials, or sensitive values; do not restore a role ID as authority, replay a POST, or force-enable a save control. Clear the temporary draft after a server-confirmed successful navigation or explicit discard/new-editor action. Preserve native validation and form serialization.
- **Principal-scope refinement (2026-11-02, current-state/post-implementation):** the opaque random draft-scope marker is retained in the HTTP session alongside server-only metadata for its authenticated principal. A different principal in the same session rotates the marker; only the opaque marker is rendered for the tab-storage namespace. This is UI draft isolation only and does not affect authorization. Confirmed new-editor/discard actions clear the tab draft; canceling the discard prompt keeps it. If browser storage cleanup is unavailable, it is best-effort and must not introduce an uncaught page error.

## Visual review và giới hạn bằng chứng

- Playwright/Edge review ngày 2026-10-29 ghi 14 check groups tại 1440×900, 1280×720, 390×844 và 320×740; document `scrollWidth` không vượt viewport, navigation active nằm trọn vùng nhìn, và `pageErrors`/`consoleErrors` đều bằng 0. Có kiểm tra MANAGEMENT system+assigned, draft-discard, duplicate-code error, loading presentation, tạo tập quyền rỗng, thao tác Space và keyboard focus.
- Evidence local: `.artifacts/test-runs/20261029T070642Z-roles-fe-4e9a82bf/roles-review/review.json` và screenshots cùng thư mục. `LoginBrowserHarnessTest` có Surefire report 1/1, 0 failures/errors/skips; Playwright script exit 0. Wrapper không ghi được numeric Maven exit vào `logs/harness.exit` (file rỗng), nên không ghi nhận con số exit đó ở đây.
- Môi trường là Spring/H2 tổng hợp với ANPR mock; không phải live E2E. Đây không phải ROLE-V: chưa kiểm chứng sâu authorization/session-revocation matrix, no-JS, screen reader, physical mobile/browser engines, WCAG conformance hoặc live DB. Human visual acceptance vẫn pending.

## Source trong phạm vi

- `spring-web/src/main/resources/templates/security-roles.html`
- `spring-web/src/main/resources/static/js/security-roles.js`
- `spring-web/src/main/frontend/styles/security-roles.css`
- `spring-web/src/main/resources/templates/dashboard-shell.html` (active navigation)
- `spring-web/src/test/java/vn/edu/parking/LoginBrowserHarnessTest.java` (synthetic MANAGEMENT assignment fixture)

Không đổi role service, permission names, authorization, authentication, CSRF contract hoặc hành vi các màn hình khác trong G4B-ROLE-FE.
