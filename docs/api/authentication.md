# Hợp đồng xác thực, tài khoản và audit

[API chung](contracts.md) · [Danh mục endpoint](endpoints.md) · [Security](../security/overview.md)

**Provenance:** đối chiếu source main `abd6183f9307472f604905dd9378c02deb563e2b` ngày 2026-10-24; không dùng `.artifacts/`/backup làm implementation authority. Đây là contract tĩnh, không test/live deployment evidence. Kết quả Security + WIP kế thừa ở [testing](../testing/strategy.md) là lịch sử, không chứng nhận revision này.

## Hai cơ chế độc lập

- **Web:** HttpSession + form login; CSRF cho forms/account/QR/payment, nhưng `SecurityConfig` bỏ qua `/api/parking/**` và `/h2-console/**`. Revenue dùng Web session, không Desktop JWT. Forced-change principal chỉ có ROLE_PASSWORD_CHANGE_REQUIRED; `/account/password` nhận authenticated caller.
- **Desktop:** `/api/auth/**`, `/api/ai/**`, `/api/parking/**` trừ revenue dùng stateless bearer chain khi `parking.security.jwt.enabled=true`. [application.properties](../../spring-web/src/main/resources/application.properties) map `PARKING_SECURITY_JWT_ENABLED`, **default true**. Tắt false chọn compatibility chain permit-all parking/AI, deny auth và không tạo DesktopAuthController; không fail-closed. Service evidence/override có thể vẫn đòi JWT.
- [AccountPrincipal](../../spring-web/src/main/java/vn/edu/parking/service/AccountPrincipal.java) cấp `ROLE_<roleCode>` cùng permissions DB khi có SecurityRole; nếu null, **fallback legacy AccountRole** (Management all, Gate Staff DASHBOARD_VIEW/GATE_OPERATE/AI_RESULT_CORRECT). Nó không kiểm role.enabled để fail-closed chung. Web matchers có ROLE_MANAGEMENT fallback. [DesktopJwtAuthenticationConverter](../../spring-web/src/main/java/vn/edu/parking/service/DesktopJwtAuthenticationConverter.java) tải account enabled/not forced-change và kiểm session/securityVersion mỗi request; không chỉ tin JWT claims. Đó không đồng nghĩa role-null/disabled denial như feature.

### Cấu hình JWT hiện hành

Nguồn [DesktopJwtConfiguration](../../spring-web/src/main/java/vn/edu/parking/config/DesktopJwtConfiguration.java): RS256, RSA≥2048bit, private PKCS#8/public X.509 PEM và key-pair match; hỗ trợ inline `parking.security.jwt.private-key-pem`/`public-key-pem` hoặc key locations (location ưu tiên). Tracked properties map `PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION`, `PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION` (default trống), issuer `PARKING_SECURITY_JWT_ISSUER` default https://parking.local, audience `PARKING_SECURITY_JWT_AUDIENCE` default parking-desktop và kid `PARKING_SECURITY_JWT_KID` default key-2026-10. Đây không phải credentials được provisioned.

`parking.security.jwt.verification-keys-json` optional: trống dùng active public key; có JSON thì phải chứa active kid/key khớp signing key; retiring keys cần verifyUntil, optional notBefore, active key không retirement window. Không có required `PARKING_JWT_VERIFICATION_KEYS_JSON` mapping như tài liệu feature. Decoder kiểm issuer/audience/time/RS256/kid, required sub/jti/sid/ver/iat/exp, iat không quá60s tương lai và lifetime≤15phút. Chưa kiểm key provisioning/rotation thực tế.

## Desktop login/refresh/logout

Nguồn [DesktopAuthController](../../spring-web/src/main/java/vn/edu/parking/web/DesktopAuthController.java), [DesktopAuthenticationService](../../spring-web/src/main/java/vn/edu/parking/service/DesktopAuthenticationService.java), [DesktopRefreshTokenService](../../spring-web/src/main/java/vn/edu/parking/service/DesktopRefreshTokenService.java).

| Method/path | Request | Thành công | Lỗi tường minh / side effects |
| --- | --- | --- | --- |
| POST `/api/auth/login` | JSON `username` không blank, tối đa80; `password` không empty, tối đa1024 ký tự; `deviceLabel` optional, server trim/cắt120 | 200 TokenResponse | 400 `{error:"invalid_request"}`; 429 `{error:"too_many_attempts"}`; 401 `{error:"invalid_credentials"}` gồm disabled/forced-change. Tạo Desktop session/token hash và audit login, không tạo account |
| POST `/api/auth/refresh` | JSON `refreshToken`, đúng43 ký tự; service kiểm base64url32bytes | 200 TokenResponse mới | Length sai→400 invalid_request; invalid/expired/reused→401 invalid_refresh_token. Rotation consume token cũ, cấp token mới; phát hiện reuse revoke session |
| POST `/api/auth/logout` | Bearer access token, không body | 204, không body | Invalid/missing bearer do security chain; revoke session của actor và audit LOGOUT. Không cần gửi refresh token |
| GET `/api/auth/session` | Bearer | 200 ProfileResponse | Non-JWT/profile runtime failure→401 không body trong handler; chain failures riêng |
| GET `/api/auth/shift-summary` | Bearer; scope default current, `previous` chọn previous summary | 200 summary hoặc204 nếu không có | Handler runtime failure→401; previous gọi `ShiftSummaryService.previous()`, không khẳng định account-scope giống current |

TokenResponse có `accessToken`, `tokenType:"Bearer"`, `expiresIn:900`, `refreshToken`, `sessionId`, `absoluteExpiresAt` Instant, **username/fullName/activeShift**. ProfileResponse có username/fullName/activeShift; shift gồm id/name/startsAt/endsAt/duty. JWT claims iss/sub/aud/iat/exp/jti/sid/ver; sub là account ID. Absolute Desktop session lifetime10giờ, idle2giờ; refresh không kéo dài absolute expiry. Tokens là secrets, không ghi vào examples/log/screenshot.

[ShiftSummaryService](../../spring-web/src/main/java/vn/edu/parking/service/ShiftSummaryService.java) trả shiftId/shiftName/operatorName/startsAt/endsAt/summarizedAt/duty, totalRevenue/cashRevenue/bankTransferRevenue/posRevenue, vehicleEntries/vehicleExits/vehiclesCurrentlyInside/occupiedSlots/availableSlots/pendingReconciliation/manualOverrides. Current chọn ca của account; previous chọn ca kết thúc gần nhất **toàn hệ thống**, không account filter. Counts entry/exit theo khoảng ca, OPEN/slots là snapshot toàn bãi; revenue theo payment gắn shift, không chứng minh settlement/reconciliation hoàn tất.

Ví dụ shape login (giá trị placeholder, không credentials được cấp):

```json
{
  "username": "<account-provisioned-by-management>",
  "password": "<secret-entered-locally>",
  "deviceLabel": "gate-demo-device"
}
```

Refresh ambiguous/malformed không chứng minh token cũ chưa consume. Desktop có handling xóa authentication/yêu cầu login lại, nhưng main đồng thời có durable outbox cho entry/exit-confirm khi connectivity failure và operationId hỗ trợ server replay; xem [retry limits](contracts.md#cross-service-contract-auth-and-retry-limits). Không có exactly-once hoặc queued evidence hợp lệ sau đổi session guarantee.

## Web login và đổi mật khẩu

`GET /login` do [LoginController](../../spring-web/src/main/java/vn/edu/parking/web/LoginController.java) render custom page; `POST /login` do Spring Security nhận form username/password + CSRF. Thành công redirect `/` hoặc `/account/password`; thất bại `/login?error`. `POST /logout` dùng Web session/CSRF, invalidate session, audit và redirect `/login?logout`; không thay Desktop logout.

| Method/path | Input/quyền | Output và tác động |
| --- | --- | --- |
| GET `/account/password` | Web authenticated, gồm ROLE_PASSWORD_CHANGE_REQUIRED | HTML `account-password` |
| POST `/account/password` | Form `currentPassword`, `newPassword`, `confirmPassword` + CSRF | Sai confirmation/current password/độ dài trả lại HTML kèm error; password mới12–72 UTF-8 bytes. Thành công tăng security version, revoke Desktop sessions, clear Web security/session rồi redirect `/login?passwordChanged` |

Nguồn [AccountController](../../spring-web/src/main/java/vn/edu/parking/web/AccountController.java), AccountPasswordService. Không phải Desktop JSON password endpoint. Tracked properties main không khai Secure/HttpOnly/SameSite/timeout riêng; không giữ claim YAML Secure/Lax/30phút đã kiểm của feature. Effective cookie/TLS/proxy/browser chưa kiểm runtime. Login throttle in-memory theo **username chuẩn hóa + remote address**:10 failures/15phút, tối đa10.000 keys, không distributed quota/khóa bền vững.

## Quản trị tài khoản

Các route dùng **Web session** + CSRF cho POST/PUT. Main matcher `/account-admin/**` nhận ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT, audit matcher đứng trước nhận SECURITY_AUDIT_VIEW hoặc ROLE_MANAGEMENT; không union ACCOUNT_MANAGE+ROLE_MANAGE guard của feature cho create/profile. Role management là Web forms `/roles/**` (ROLE_MANAGE hoặc ROLE_MANAGEMENT), không `/account-admin/roles` JSON CRUD. Nguồn [AccountAdministrationController](../../spring-web/src/main/java/vn/edu/parking/web/AccountAdministrationController.java), [AccountManagementPageController](../../spring-web/src/main/java/vn/edu/parking/web/AccountManagementPageController.java), [SecurityRolePageController](../../spring-web/src/main/java/vn/edu/parking/web/SecurityRolePageController.java), AccountManagementService và SecurityRoleService.

| Method/path | Input | Thành công / side effects |
| --- | --- | --- |
| GET `/account-admin/accounts` | `ACCOUNT_MANAGE`; không body | 200 AccountSummaryResponse[], không phân trang |
| GET `/account-admin/accounts/{username}` | `ACCOUNT_MANAGE`; username normalize trim/lowercase | 200 summary; account không có→404 |
| POST `/account-admin/accounts` | ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT; JSON username NotBlank/max80, roleId NotNull và employee profile bên dưới | 201 `{account,temporaryPassword}`; duplicate409 account_exists; caught IllegalArgumentException400 `{error:ex.getMessage()}`; forced-change + audit |
| POST `/account-admin/accounts/{username}/disable` | ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT; không body | 204/version/revoke/audit; last enabled role-manager guard theo ROLE_MANAGE (legacy Management fallback), không union hai grants; conflict409 last_management_account |
| POST `/account-admin/accounts/{username}/enable` | `ACCOUNT_MANAGE`; không body | 204; tăng version, revoke sessions, audit |
| PUT `/account-admin/accounts/{username}/profile` | ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT; JSON roleId và full employee profile | 204; profile update/audit; **chỉ khi role đổi** mới tăng version/revoke; last-management409 last_management_account, IllegalArgumentException400 `{error:message}` |
| POST `/account-admin/accounts/{username}/password-reset` | `ACCOUNT_MANAGE`; không body | 200 `{temporaryPassword, mustChangePassword:true}`; đổi hash, tăng version, revoke sessions, audit |
| GET `/roles` | ROLE_MANAGE hoặc ROLE_MANAGEMENT | HTML roles/account counts/permission options |
| POST `/roles/create` | Form code, displayName, employeePrefix; optional description/permissions | Service validation; flash + redirect `/roles` |
| POST `/roles/update` | Form id/displayName, optional description/permissions, enabled default false | Grant changes tăng account versions/revoke Desktop sessions + audit; system/assigned roles không được disable; MANAGEMENT phải giữ ACCOUNT_MANAGE/ROLE_MANAGE; redirect/flash |
| GET `/account-admin/audit`, `/account-admin/audit/overrides` | `SECURITY_AUDIT_VIEW`; pagination | 200 SecurityAuditPageResponse |

Create profile fields required: fullName≤160, phone≤20, email≤160, citizenId≤20, dateOfBirth LocalDate NotNull, address≤500; strings NotBlank. Profile PUT cùng fields, không username. AccountSummaryResponse gồm id/username/**employeeCode/fullName/phone/email/citizenId/dateOfBirth/address**/roleId/roleCode/roleDisplayName/enabled/mustChangePassword, không passwordHash/securityVersion. Đây vẫn là PII, không chỉ role metadata. Temporary password trả một lần: coi là secret. Username normalize trim/lowercase/nonblank/max80; không tự thêm charset regex. Account/role row-lock source không chứng minh MySQL behavior.

AccountManagementService còn kiểm phone9..15digits (optional +), email shape/lowercase và unique, citizenId9 hoặc12digits/unique, tuổi18..100 và role tồn tại/enabled. Các checks này không phải phê duyệt identity/consent ngoài hệ thống. Cập nhật profile giữ nguyên role không tự revoke sessions; role change mới cập nhật security state.

Role forms dùng entity/model + RoleView(accountCount), không RoleResponse REST contract. Service code regex `[A-Z][A-Z0-9_]{2,47}`, employeePrefix `[A-Z]{2,8}`, displayName≤120, description≤500. [SecurityRoleBootstrap](../../spring-web/src/main/java/vn/edu/parking/config/SecurityRoleBootstrap.java) tạo MANAGEMENT all permissions và **khôi phục all grants lúc startup** nếu khác; tạo GATE_STAFF thiếu với DASHBOARD_VIEW/GATE_OPERATE/AI_RESULT_CORRECT. Existing gate-role grants không bị chép lại ở nhánh đó. ManualOverrideService thực sự kiểm AI_RESULT_CORRECT/active shift; không OVERRIDE_CREATE/three-exception feature policy.

### Web employee, ca trực và quản lý phiên

`GET /account-management`, `/account-management/live` nhận bất kỳ ACCOUNT_MANAGE/SHIFT_MANAGE/SECURITY_AUDIT_VIEW/ROLE_MANAGEMENT. Các mutations khác dưới prefix cần ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT, riêng shifts cần SHIFT_MANAGE hoặc ROLE_MANAGEMENT; đừng suy read page chỉ chứa đúng loại dữ liệu theo từng grant.

| Method/path | Form input / output |
| --- | --- |
| POST `/account-management/create` | username, roleId và employee profile; temporary password trong flash, redirect |
| POST `/account-management/profile` | username, roleId, profile; redirect/flash |
| POST `/account-management/state` | username, enabled; enable/disable |
| POST `/account-management/reset-password` | username; temporary password flash |
| POST `/account-management/shifts` | username, shiftName, startsAt/endsAt dạng yyyy-MM-dd'T'HH:mm chuyển từ Asia/Ho_Chi_Minh, duty, optional notes |
| POST `/account-management/shifts/delete` | id; xóa assignment theo service |
| POST `/account-management/sessions/desktop/revoke` | sessionId; revoke/audit |
| POST `/account-management/sessions/web/revoke` | sessionId; expire registry session/audit |
| GET `/security-audit` | page default0; HTML audit, SECURITY_AUDIT_VIEW hoặc ROLE_MANAGEMENT |

Ngoài handler catches ở bảng, [AccountAdministrationExceptionHandler](../../spring-web/src/main/java/vn/edu/parking/web/AccountAdministrationExceptionHandler.java) map AccountNotFoundException→404 `{error:"account_not_found"}` và IllegalArgumentException/MethodArgumentNotValidException→400 `{error:"invalid_request"}`. Disable last management được controller map409 `{error:"last_management_account"}`. Không suy cùng envelope cho Web forms/security/binding/unhandled errors.

## Audit

| Method/path | Query | Response |
| --- | --- | --- |
| GET `/account-admin/audit` | `page=0`, `size=20`; page>=0, size1..100 | 200 SecurityAuditPageResponse, order occurredAt desc |
| GET `/account-admin/audit/overrides` | Cùng pagination | Chỉ action bắt đầu `OVERRIDE_`; ManualOverrideService main ghi `MANUAL_OVERRIDE_APPROVED/REJECTED`, nên không coi listing này bao phủ current overrides |

Page fields: `events`, `page`, `size`, `totalElements`, `totalPages`. Mỗi event: `id`, `actorAccountId`, `actorUsername`, `action`, `targetType`, `targetReference`, `outcome`, `reason`, `evidenceReference`, `occurredAt`. Invalid page bounds→400. Không có export/delete/mutation-audit endpoint được khai ở controller này. Log policy phải không chứa credentials/hashes/tokens hoặc image/decoded CCCD payloads; xem [security](../security/overview.md) cho scope audit thực tế.

## Kiểm chứng

Contracts trên đọc source, không service call/build/test/DB/dependencies/Git mutation. Các executions kế thừa cho login/RBAC/account-race/session invalidation ở [testing](../testing/strategy.md) là lịch sử nhánh Security + WIP, không main acceptance. Chưa kiểm effective key/token provisioning, cookie/TLS, real MySQL, account/role disabled cases, browser UI hoặc deployed audit.
