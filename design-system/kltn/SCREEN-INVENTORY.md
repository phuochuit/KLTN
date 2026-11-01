# Screen inventory — source thực tế

[MASTER](MASTER.md) · [Audit](UI-AUDIT.md) · [Handoff](PHASE-2-HANDOFF.md)

Snapshot tĩnh 2026-10-25, revision `fe182fc9839de07d56bb0b3c54fefc5cdf034aed`, branch `feature/web-ui-redesign`. Không app/browser/DB run. Source hiện tại là evidence implementation, không acceptance nghiệp vụ. Toàn bộ19 HTML files và2 shared assets đã đọc; không suy screen từ tài liệu.

## Checkpoint Build local 2026-11-01 — không thay snapshot bên dưới

| 15 destination | Trạng thái có bằng chứng / giới hạn |
| --- | --- |
| `/login` | UI responsive đã được người dùng duyệt thị giác. |
| `/` | Dashboard đã Build; visual acceptance còn chờ theo override. |
| `/registrations` | FE đã Build, UI/UX được chấp thuận; REG-V pending. |
| `/cards` | FE đã Build và targeted synthetic review; UI/UX acceptance và CARD-V pending. |
| `/households`, `/vehicles`, `/security-audit`, `/settings`, `/account/password` | Group 4A đã Build và có focused review tổng hợp/screen-specific; tài liệu Group 4A còn các checkpoint và visual approval chưa hoàn tất, không suy tất cả đã nghiệm thu. |
| `/slots`, `/sessions` | Group 3 đã Build/refine, focused synthetic review; không suy human acceptance từ test. |
| `/account-management` | FE1 chờ UI/UX acceptance; FE2 được chấp thuận UI/UX; ACC-V pending. |
| `/roles` | FE đã Build; UI/UX acceptance và ROLE-V pending. |
| `/pricing` | Build và desktop/mobile review một phần; browser response ghi chưa kiểm; chờ UI/UX acceptance. |
| `/payments` | Build và desktop/mobile empty review; populated ledger/response ghi chưa browser review; chờ UI/UX acceptance. |

Trạng thái này không chứng nhận nghiệp vụ, live E2E hoặc nghiệm thu chức năng; chi tiết evidence trong override và Plan/TODO tương ứng.

## Source map

- **A** = [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java).
- **P** = [PaymentController](../../spring-web/src/main/java/vn/edu/parking/web/PaymentController.java).
- **M** = [AccountManagementPageController](../../spring-web/src/main/java/vn/edu/parking/web/AccountManagementPageController.java).
- **R** = [SecurityRolePageController](../../spring-web/src/main/java/vn/edu/parking/web/SecurityRolePageController.java).
- **L** = [LoginController](../../spring-web/src/main/java/vn/edu/parking/web/LoginController.java); **W** = [AccountController](../../spring-web/src/main/java/vn/edu/parking/web/AccountController.java).
- [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java), [shared model advice](../../spring-web/src/main/java/vn/edu/parking/web/CurrentAccountModelAdvice.java), [upload handler](../../spring-web/src/main/java/vn/edu/parking/web/UploadExceptionHandler.java), [QR controller](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java).

## 1. Full-page GET destinations (15)

Đối tượng mọi trang quản trị: ban quản lý hoặc nhân sự được cấp grant tương ứng, không cư dân tự phục vụ. “Grant” dưới là source matcher hiện tại; **mọi business grant đều có fallback ROLE_MANAGEMENT**. Không tự diễn giải một chức danh thành có mọi quyền.

| Trang / actor được cấp quyền | GET → controller → template | UI chính / tương tác phải giữ | UX cần giải quyết |
| --- | --- | --- | --- |
| Tổng quan — DASHBOARD_VIEW | `/` → A.dashboard → [dashboard.html](../../spring-web/src/main/resources/templates/dashboard.html) | KPI hộ/xe/OPEN, kỳ tháng/năm/range, 12 tháng, click tháng mở bảng chi tiết, lượt gần đây | kỳ và snapshot lẫn nhãn; click/hover chart thiếu keyboard; API lỗi không visible |
| Đăng ký liên thông — VEHICLE_MANAGE | `/registrations` → A.registrations → [registrations.html](../../spring-web/src/main/resources/templates/registrations.html) | multipart hộ/người/xe, QR upload/browser camera, face upload/capture, giấy xe; thành công sang cards chọn xe | form dài4 cột, thiếu error summary, QR CSRF, quyền QR/cards khác quyền entry |
| Hộ và cư dân — RESIDENT_MANAGE | `/households` → A.households → [households.html](../../spring-web/src/main/resources/templates/households.html) | hộ/quota override; member picker/edit/reset; hai bảng, QR/face, GET member data | form trước list rất dài; sensitive table; face ảnh cần grant riêng; capture cũ/reset cần QA |
| Phương tiện/quyền theo xe — VEHICLE_MANAGE | `/vehicles` → A.vehicles → [vehicles.html](../../spring-web/src/main/resources/templates/vehicles.html) | vehicle picker/edit/reset; household/owner; authorized checkboxes; giấy xe; table | nhiều checkbox mọi hộ, khó chọn, thiếu context/loading/dirty warning; form/table dày |
| Thẻ & gói — CARD_MANAGE | `/cards?vehicleId=…` → A.cards → [cards.html](../../spring-web/src/main/resources/templates/cards.html) | chọn đúng xe sau đăng ký; pass/status/start/duration, payment method/reference, cards + lịch sử thu gói | thao tác có tiền chưa có confirmation summary; “30 ngày” nhưng1–366; không retry POST mù |
| Sơ đồ điều phối — SLOT_MANAGE | `/slots?floor=…&zone=…` → A.slots → [slots.html](../../spring-web/src/main/resources/templates/slots.html) | totals/filter/legend, ô→detail modal4 tabs, tạo ô/batch, assign/borrow/cancel/dispatch/block/release | div click-only, modal semantics/focus thiếu; pulse; nhãn realtime không polling; lỗi flash một số action chưa render |
| Lịch sử — HISTORY_VIEW | `/sessions` → A.sessions → [sessions.html](../../spring-web/src/main/resources/templates/sessions.html) | table9 cột theo entry desc, biển vào/ra/type/thẻ/time/state/fee | không filter/paging/detail UI; thiếu empty/scroll wrapper; không invent evidence timeline |
| Giao dịch/đối soát — PAYMENT_RECONCILE | `/payments` → P.page → [payments.html](../../spring-web/src/main/resources/templates/payments.html) | PAID count/amount, ledger table8 cột, required note + POST reconcile chỉ PAID | table rộng, label input note chưa liên kết; stats classes ở CSS dashboard không shared; cần confirm đúng biên nhận |
| Bảng giá — PRICING_MANAGE | `/pricing` → A.pricing → [pricing.html](../../spring-web/src/main/resources/templates/pricing.html) | nhiều form theo vehicleType, car block fields/readonly nightPrice; giá>=0 | labels tách input thiếu for/id; cần đơn vị/kỳ rõ; không quảng cáo giá khảo sát đã duyệt |
| Hạn mức — SLOT_MANAGE | `/settings` → A.settings → [settings.html](../../spring-web/src/main/resources/templates/settings.html) | policy hai nhóm,0 nghĩa không cho đăng ký; household blank override dùng default | cần phân biệt default vs riêng, form ngắn không cần Bento |
| Tài khoản/phiên/ca — ACCOUNT_MANAGE **hoặc** SHIFT_MANAGE **hoặc** SECURITY_AUDIT_VIEW | `/account-management` → M.accounts → [account-management.html](../../spring-web/src/main/resources/templates/account-management.html) | create/profile dialog, roleId/PII, lock/reset one-time password, Web/Desktop revoke, shift create/delete, refresh3s | read/write grants khác nhau, page dài, refresh làm mất focus, stale không báo; nhiều CSS inline |
| Vai trò & quyền — ROLE_MANAGE | `/roles` → R.page → [security-roles.html](../../spring-web/src/main/resources/templates/security-roles.html) | create/update, permission set, employeePrefix/code, enabled/systemRole | checkbox wall, grid min340/260 gây hẹp; cần nhóm quyền, không thay unchecked binding |
| Nhật ký bảo mật — SECURITY_AUDIT_VIEW | `/security-audit?page=…` → M.audit → [security-audit.html](../../spring-web/src/main/resources/templates/security-audit.html) | events.content,25/page, previous/next, actor/action/target/outcome/reason | mã/raw text dày, cần đọc dễ hơn và không fake filter/export |
| Đăng nhập — public | `/login?error/logout/passwordChanged/throttled` → L.login → [login.html](../../spring-web/src/main/resources/templates/login.html) | username/password, show/hide aria-pressed, csrf, alert/status | độc lập theme; login default `/` có thể403 cho custom role; không tự đổi redirect backend |
| Đổi mật khẩu — authenticated | `/account/password` → W.passwordForm/changePassword → [account-password.html](../../spring-web/src/main/resources/templates/account-password.html) | current/new/confirm, show passwords, errors, invalidate session→login | wording byte/minlength khác service; forced-change flow cần giữ, không thêm dark toggle |

## 2. Partial/error/layout files (4)

| File / entry | Contract / purpose | Redesign implication |
| --- | --- | --- |
| [account-management-live.html](../../spring-web/src/main/resources/templates/account-management-live.html), GET `/account-management/live` → M.live | HTML view có fragments `sessions`, `shifts`; IDs `live-sessions`, `live-shifts`; same read grants account page; không page navigation riêng | fetch/DOMParser/replaceWith giữ csrf forms, focus, data-confirm; không tính là màn hình Desktop |
| [error.html](../../spring-web/src/main/resources/templates/error.html), framework `/error` | status403/404/khác; generic message, `/` và `/login` links; không custom error controller được tìm thấy | standalone shell, tránh link về forbidden home loop; không hiển thị stack trace |
| [upload-error.html](../../spring-web/src/main/resources/templates/upload-error.html), UploadExceptionHandler.handleUploadTooLarge | HTTP413 HTML, model `message`, history.back; **không GET screen riêng** | notice hiện success tint; recovery không hứa field/file được khôi phục |
| [fragments.html](../../spring-web/src/main/resources/templates/fragments.html), `header` | grouped horizontal nav bằng details/summary + greeting/logout POST; shared app-dialog; currentAuthorities/currentAccountDisplayName | chưa có sidebar/base head/main layout fragment. Phase2 thay shell, giữ conditional authority/navigation và dialog contract |

**Tổng:** 19 files =15 full-page GET +1 live HTML view +2 error templates +1 shared fragment. Live view có2 fragment nhưng vẫn1 file; không đếm thành2 page mới. Không tìm thấy controller-backed standalone member/vehicle/card detail, export, notification center hay charging page. JSON/media endpoint không tự thành screen.

## 3. CSS / JavaScript / responsive inventory

**Delta Branding & Login 2026-10-26:** riêng login.html thay bằng LumiPark SSR UI, load `/css/web-ui.css`, `/js/login.js`, `/images/brand/` và Lucide subset generated. CSS source nằm `src/main/frontend/styles/`, CLI build qua package/lock và Maven process-classes. Login không còn CSS/JS inline. Các ghi nhận asset/style bên dưới là baseline audit Phase1 cho phần chưa chuyển; không diễn giải như Login mới vẫn dùng style cũ. Không đổi route/security/model contracts.

- [app.css](../../spring-web/src/main/resources/static/css/app.css): shared tokens7 màu, Segoe UI, main1180px, form-grid4 cột, tables, buttons, slot grid/status, custom modal/tabs, native app-dialog. Một shared breakpoint760px đổi header/nav/filter/slot grid; không collapse shared form-grid/price-grid/stats grid đầy đủ. Slot mobile ép3 cột.
- [app-dialog.js](../../spring-web/src/main/resources/static/js/app-dialog.js): nav pointer/focus/Escape; `window.ParkingUi.alert/confirm` trả Promise; override `window.alert` thành async; submit delegation `form[data-confirm]`, `requestSubmit(event.submitter)` tránh loop qua data-confirmed. Không gọi async alert như native blocking alert trong code mới.
- Inline style blocks: dashboard, account-management, security-roles, security-audit; login/password/error tự style, không shared CSS. Inline style attributes rải rác media/slots. Inline JS: dashboard, registrations, households, vehicles, slots, account-management, login, account-password. Không frontend package manifest được tìm thấy; Tailwind/Lucide chưa là dependency Web hiện tại.
- Trong17 full-page/error templates,7 file có viewport: slots, account-management, roles, audit, login, password, error.10 file còn lại không có: dashboard/registrations/households/vehicles/cards/payments/pricing/settings/sessions/upload-error. Header fragment không cấp meta head.
- Sidebar chưa tồn tại. Chưa có shared table pagination/search/sort component; audit là pagination server riêng. Chưa có toast system chung; notice/alert/dialog là feedback thực tế. Slots custom div modal khác native profile/app-dialog.

## 4. Binding / validation preservation ledger

Đây là MVC `@RequestParam`/manual validation phần lớn, không form DTO/BindingResult chung. Giữ **name, type, multipart, enum value, hidden ID, readonly, selected**, request method và destination. `th:field` không được tự thêm với model object không tồn tại.

| POST / symbol | Exact fields / key constraints |
| --- | --- |
| `/households` A.saveHousehold | `householdCode, apartmentNumber, buildingName, contactPhone, maxTwoWheelers, maxCars`; upsert theo code, blank quota→default,0 khác blank |
| `/members` A.saveMember | `memberId, householdId, fullName, citizenId, oldCitizenId, dateOfBirth, gender, relationshipToHead, phone, email, permanentAddress, cccdIssueDate, cccdIssuePlace, cccdQrRaw, registrationFaceImage, registrationFaceCapture`; stored/new face required; member citizenId rule không đồng nhất combined registration |
| `/vehicles` A.saveVehicle | `vehicleId, householdId, registeredOwnerId, plateNumber, registrationNumber, vehicleType, fuelType, brand, modelName, color, manufactureYear, chassisNumber, engineNumber, seatCount, cylinderCapacityCc, registrationImage, registrationImagePath, authorizedMemberIds`; owner/authorized cùng hộ, owner tự được thêm, quota/plate check; giữ ảnh cũ khi không thay |
| `/registrations` A.saveRegistration | household+member+vehicle fields trên **không IDs**; CCCD12 digits, face+giấy xe required, normalized plate, quota; một multipart POST → `/cards?vehicleId={id}` |
| `/cards` A.saveCard | `vehicleId, status, passType, startDate, durationDays, paymentMethod, paymentReference`; duration1–366/default30, end inclusive start+days−1; MONTHLY positive monthly price; CASH/BANK_TRANSFER/POS, noncash reference<=120; không cardId nhập tay |
| `/pricing` A.savePrice | `vehicleType, basePrice, nightPrice, overnightFee, monthlyPrice, baseHours, extraBlockHours, extraBlockPrice`; amounts>=0, car hours>=1, noncar blocks=0 |
| `/settings` A.saveSettings | `defaultMaxTwoWheelers, defaultMaxCars` >=0 |
| `/slots` A.saveSlot | `slotCode, floor, zoneName, slotType, vehicleType, assignedVehicleId`; single slotType RESIDENT_RESERVED/VISITOR_FLEXIBLE |
| `/slots/assign` A.assignSlot | `slotId, vehicleId` optional vehicle unassigns;1 xe tối đa1 ô, compatibility server |
| `/slots/borrow`, `/slots/cancel-borrow` | `slotId, borrowedPlate, hours, borrowNotes`; cancel chỉ slotId. UI hours options1/2/4/8/12/24/48, server minimum1 |
| `/slots/status`, `/slots/release` | status=`slotId,statusOverride` NORMAL/BLOCKED; release=`slotId`, không đóng parking session |
| `/slots/dispatch-session` | `slotId,sessionId`; list model “unassignedSessions” thực là latest30 OPEN, không được chứng minh đã loại assigned |
| `/slots/batch-generate` A.batchGenerateSlots | model-bound BatchSlotGenerateRequest: `floor,zoneName,prefix,startNumber,count,slotType,vehicleType`; batch slotType RESIDENT/VISITOR khác single; HTML count max50, server clamp100, không tự đổi giới hạn |
| `/payments/{id}/reconcile` P.reconcile | path id + `note` required thực tế,<=500; chỉ PAID, không external settlement |
| `/account-management/create`, `/profile` | `username,roleId,fullName,phone,email,citizenId,dateOfBirth,address`; profile giữ employee identity. Create/profile grant ACCOUNT_MANAGE, role catalog edit grant ROLE_MANAGE riêng |
| `/account-management/state`, `/reset-password` | username + enabled (state); reset username; temporaryPassword flash chỉ một lần; last-management protections giữ server |
| `/account-management/shifts`, `/shifts/delete` | `username,shiftName,startsAt,endsAt,duty,notes`; delete `id`; dates datetime-local, VN timezone theo service/controller, end>start,<=24h/no overlap; SHIFT_MANAGE |
| `/account-management/sessions/desktop/revoke`, `/sessions/web/revoke` | `sessionId`; ACCOUNT_MANAGE, current Web session không tự revoke bằng action này |
| `/roles/create`, `/roles/update` | create `code,displayName,employeePrefix,description,permissions`; update `id,displayName,description,enabled,permissions`; system role enabled hidden=true, code/prefix không editable; retain unchecked/multi-value semantics |
| `/login`, `/logout` | framework POST username/password + CSRF; logout POST; success forced password route hoặc `/` |
| `/account/password` W.changePassword | `currentPassword,newPassword,confirmPassword`; mismatch/current checks; service minimum Java String.length12, max72 bytes UTF-8, UI nói12–72bytes; logout/relogin sau success |

Image forms hiển thị mỗi ảnh10MB; [ResidentImageStorage](../../spring-web/src/main/java/vn/edu/parking/service/ResidentImageStorage.java) decode/size/private reference. Không coi accept=image/* là server validation hoặc registrationImagePath là public URL. Đọc nghiệp vụ [NV01](../../docs/requirements/nv01-registration.md), [NV02](../../docs/requirements/nv02-cards-passes-pricing.md), không biến record-save thành complete consent/approval/payment atomicity.

## 5. Ajax/media và security boundaries

| Caller → endpoint | Contract cần giữ / risk |
| --- | --- |
| Dashboard → GET `/api/parking/revenue/monthly` |12 monthly rows year/month/label/visitRevenue/subscriptionRevenue/totalRevenue; current grant **DASHBOARD_VIEW hoặc HISTORY_VIEW**, Web session, không JWT |
| Dashboard → GET `/api/parking/revenue/filter?type=month/year/range` | year/month/from/to; returns sums + householdCount/vehicleCount/openCount/sessions/payments. todayRevenue/monthRevenue cùng tổng kỳ sau filter; selected period không “hôm nay” |
| Vehicle editor → GET `/admin-data/vehicles/{id}` | VEHICLE_MANAGE; returns vehicle form fields, authorizedMemberIds và registrationImagePath |
| Member editor → GET `/admin-data/members/{id}` | RESIDENT_MANAGE; profile fields + faceImageAvailable/faceImageUrl |
| Embedded image → GET `/admin-data/members/{id}/face-image` | PRIVATE_EVIDENCE_VIEW **khác** member grant; no-store/audited; UI cần fallback403 không phá form |
| QR → POST `/api/cccd/scan-qr` | multipart file; RESIDENT_MANAGE + CSRF. Returned raw/citizenId/oldCitizenId/fullName/dateOfBirth/gender/permanentAddress/cccdIssueDate/message. Hai callers hiện FormData chỉ file, không CSRF; gap tĩnh, chưa runtime |
| Account polling → GET `/account-management/live` | HTML, credentials same-origin, X-Requested-With;3000ms, skip hidden/overlap; replace2 regions; csrf mới trong HTML phải giữ |

Additional JSON/media, **không screen đã render**: revenue monthly/{year}/{month}, stats/{year}/{month}; `/payments/receipts/{id}`, `/payments/reports/reconciliation`; `/admin-data/gate-evidences/{evidenceId}` và assets; `/account-admin/**` account JSON. Không tự thêm UI endpoint consumers này trong Phase2 nếu chưa duyệt. Reconciliation JSON lấy RECONCILED ledger khác dashboard sum COMPLETED fees/subscriptions.

CSRF Web enabled trừ `/api/parking/**`, `/h2-console/**`; QR **không exempt**. `th:action` POST forms được Spring/Thymeleaf tích hợp token; không kết luận thiếu chỉ vì không có hidden trong source. Account/roles/login/password/live có hidden _csrf explicit. QR fetch mới FormData không lấy token từ form. Không “fix” bằng exempt endpoint hoặc tắt CSRF.

Shared nav conditions dựa currentAuthorities, nhưng page body account chưa chia grant từng action. `/account-management`/live read broad, shifts POST narrower SHIFT_MANAGE, còn actions ACCOUNT_MANAGE; không coi visible button là quyền server. Registration entry VEHICLE_MANAGE, QR RESIDENT_MANAGE, redirect cards CARD_MANAGE: test limited grants, không mở rộng quyền để làm luồng đẹp.

Docs security có phần snapshot cũ; **source matcher hiện tại** dùng DASHBOARD_VIEW/HISTORY_VIEW cho revenue, không REVENUE_VIEW như một số bảng lịch sử. Redesign giữ source authorization hiện tại, mọi thay đổi policy phải được duyệt riêng. Error fetch không uniform JSON: có login redirect, HTML413 và generic error. Chưa có live auth/media/financial E2E evidence trong audit này.
