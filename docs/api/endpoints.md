# Danh mục endpoint theo source

[Contracts](contracts.md) · [Auth/accounts/audit](authentication.md) · [Security](../security/overview.md)

Danh mục method/path đối chiếu source main **`abd6183f9307472f604905dd9378c02deb563e2b`**, ngày 2026-10-24, không `.artifacts/`/backup/feature copies. Giữ API/Web/QR/media/demo coverage, không thêm routes dự kiến trong yêu cầu. Có mapping không chứng minh runtime, permissions provisioned hoặc test PASS; đợt này chỉ kiểm tĩnh. Bằng chứng suite Security + WIP kế thừa là lịch sử tại [testing](../testing/strategy.md).

## Spring — gate, recognition và slots

Nguồn [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java), [AiProxyController](../../spring-web/src/main/java/vn/edu/parking/web/AiProxyController.java), [DesktopApiSecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/DesktopApiSecurityConfig.java); payload/errors/evidence ở [contracts](contracts.md). JWT mặc định bật: gate/read/AI/dispatch cần GATE_OPERATE, vehicles-unassigned cần VEHICLE_MANAGE, slot mutations còn lại SLOT_MANAGE; chỉ parking health public. Tắt JWT chọn compatibility permit-all parking/AI, không fail-closed. Revenue thuộc Web; admin evidence routes nằm ở nhóm Web bên dưới.

| Method | Path |
| --- | --- |
| POST | `/api/parking/entry` |
| POST | `/api/parking/exit-preview` |
| POST | `/api/parking/exit-confirm` |
| GET | `/api/ai/health` |
| POST | `/api/ai/recognize/image` |
| POST | `/api/ai/recognize/video` |
| POST | `/api/ai/recognize/camera` |
| POST | `/api/ai/face/verify-camera` |
| POST | `/api/ai/face/verify-image` |
| POST | `/api/ai/face/verify-video` |
| POST | `/api/ai/face/guest/verify-camera` |
| POST | `/api/ai/face/guest/verify-image` |
| POST | `/api/ai/face/guest/verify-video` |
| POST | `/api/ai/face/capture-camera` |
| GET | `/api/parking/open` |
| GET | `/api/parking/lookup` |
| GET | `/api/parking/slots` |
| POST | `/api/parking/slots/{id}/assign` |
| POST | `/api/parking/slots/{id}/borrow` |
| POST | `/api/parking/slots/{id}/cancel-borrow` |
| POST | `/api/parking/slots/{id}/status` |
| POST | `/api/parking/slots/{id}/release` |
| POST | `/api/parking/slots/{id}/dispatch-session` |
| GET | `/api/parking/recent-unassigned` |
| GET | `/api/parking/vehicles-unassigned` |
| POST | `/api/parking/slots/batch-generate` |
| GET | `/api/parking/health` |

Main không khai feature `/api/parking/anpr/**`, session/operation image hay preservation/release routes. Không chuyển historical contracts thành API main.

## Spring — Desktop auth, Web account và audit

Chi tiết [authentication](authentication.md). DesktopAuthController chỉ tạo khi JWT bật; session/summary/logout cần bearer, login/refresh public. Account-admin cần ACCOUNT_MANAGE hoặc ROLE_MANAGEMENT, audit SECURITY_AUDIT_VIEW hoặc ROLE_MANAGEMENT, role forms ROLE_MANAGE hoặc ROLE_MANAGEMENT; main không union create-account/role-assignment guard của feature.

| Method | Path |
| --- | --- |
| POST | `/api/auth/login` |
| POST | `/api/auth/refresh` |
| POST | `/api/auth/logout` |
| GET | `/api/auth/session` |
| GET | `/api/auth/shift-summary` |
| GET | `/account/password` |
| POST | `/account/password` |
| GET | `/account-admin/accounts` |
| GET | `/account-admin/accounts/{username}` |
| POST | `/account-admin/accounts` |
| POST | `/account-admin/accounts/{username}/disable` |
| POST | `/account-admin/accounts/{username}/enable` |
| PUT | `/account-admin/accounts/{username}/profile` |
| POST | `/account-admin/accounts/{username}/password-reset` |
| GET | `/account-admin/audit` |
| GET | `/account-admin/audit/overrides` |
| GET | `/account-management` |
| GET | `/account-management/live` |
| POST | `/account-management/create` |
| POST | `/account-management/profile` |
| POST | `/account-management/state` |
| POST | `/account-management/reset-password` |
| POST | `/account-management/shifts` |
| POST | `/account-management/shifts/delete` |
| POST | `/account-management/sessions/desktop/revoke` |
| POST | `/account-management/sessions/web/revoke` |
| GET | `/security-audit` |
| GET | `/roles` |
| POST | `/roles/create` |
| POST | `/roles/update` |

## Spring — Web pages/forms/JSON

Nguồn [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java), [CccdQrController](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java), [GateEvidenceAdminController](../../spring-web/src/main/java/vn/edu/parking/web/GateEvidenceAdminController.java), [PaymentController](../../spring-web/src/main/java/vn/edu/parking/web/PaymentController.java). Web session/permissions; CSRF cho forms/QR/payment (main bỏ qua `/api/parking/**`). Contracts/forms ở [contracts](contracts.md), thao tác ở [user guide](../operations/user-guide.md); không REST CRUD mọi entity.

| Method | Path |
| --- | --- |
| GET | `/` |
| GET | `/vehicles` |
| GET | `/registrations` |
| POST | `/registrations` |
| POST | `/vehicles` |
| GET | `/admin-data/vehicles/{id}` |
| GET | `/households` |
| POST | `/households` |
| POST | `/members` |
| GET | `/admin-data/members/{id}` |
| GET | `/admin-data/members/{id}/face-image` |
| GET | `/admin-data/gate-evidences/{evidenceId}` |
| GET | `/admin-data/gate-evidences/{evidenceId}/assets/{assetId}` |
| GET | `/settings` |
| GET | `/slots` |
| POST | `/slots` |
| POST | `/slots/assign` |
| POST | `/slots/borrow` |
| POST | `/slots/cancel-borrow` |
| POST | `/slots/status` |
| POST | `/slots/release` |
| POST | `/slots/dispatch-session` |
| POST | `/slots/batch-generate` |
| POST | `/settings` |
| GET | `/cards` |
| POST | `/cards` |
| GET | `/pricing` |
| POST | `/pricing` |
| GET | `/sessions` |
| GET | `/api/parking/revenue/monthly` |
| GET | `/api/parking/revenue/filter` |
| GET | `/api/parking/revenue/monthly/{year}/{month}` |
| GET | `/api/parking/revenue/stats/{year}/{month}` |
| POST | `/api/cccd/scan-qr` |
| GET | `/payments` |
| POST | `/payments/{id}/reconcile` |
| GET | `/payments/receipts/{id}` |
| GET | `/payments/reports/reconciliation` |

## ANPR và demo riêng

Nguồn [ANPR main.py](../../anpr-service/app/main.py), [internal_auth.py](../../anpr-service/app/internal_auth.py), [demo app.py](../../face-verification-demo/app.py); payload/errors ở [contracts](contracts.md). ANPR dùng X-Parking-Service-Token; **chỉ health/favicon public**, root/docs cũng được middleware bảo vệ. Demo riêng không phải gate dependency, không thừa hưởng ANPR auth.

| Service | Method | Path |
| --- | --- | --- |
| ANPR | GET | `/health` |
| ANPR | GET | `/` |
| ANPR | GET | `/favicon.ico` |
| ANPR | POST | `/recognize/image` |
| ANPR | POST | `/recognize/video` |
| ANPR | POST | `/recognize/camera` |
| ANPR | POST | `/face/verify` |
| ANPR | POST | `/face/verify-camera` |
| ANPR | POST | `/face/verify-image` |
| ANPR | POST | `/face/verify-video` |
| ANPR | POST | `/face/capture-camera` |
| Demo | GET | `/` |
| Demo | GET | `/api/health` |
| Demo | POST | `/api/compare` |

## Framework/resources và giới hạn

- [LoginController](../../spring-web/src/main/java/vn/edu/parking/web/LoginController.java) cung cấp GET `/login`; Spring Security xử lý POST `/login`, POST `/logout`, cần CSRF.
- Main không có WebConfig publish `/uploads/**`; private/legacy media đọc qua server services và explicit admin routes. Không generic evidence storage download hoặc preservation endpoint.
- `/css/**`, `/js/**`, `/images/**`, favicon là static; `/error` framework. Main SecurityConfig **permitAll `/h2-console/**`**, CSRF ignore và frame sameOrigin; matcher không chứng minh console được bật/phục vụ ở runtime.
- FastAPI mặc định có `/docs`, `/redoc`, `/openapi.json` nhưng middleware yêu cầu service token. Demo `/static/**` resource mount riêng. HEAD/OPTIONS tự sinh không route nghiệp vụ bổ sung.
- UploadExceptionHandler không tạo endpoint: multipart quá lớn có thể HTML413. Tracked application.properties max-file10MB/request25MB; proxy MIME/service limits không thay limit servlet. Không hứa JSON errors thống nhất.
