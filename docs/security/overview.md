# Bảo mật, quyền truy cập và ranh giới tin cậy

[Hợp đồng API](../api/contracts.md) · [Kiến trúc hệ thống](../architecture/system.md) · [Database](../architecture/database.md) · [Traceability NV01–NV08](../requirements/traceability.md)

## Phạm vi và mức bằng chứng

Trang này phân biệt security policy trong requirements với controls thấy trong source. Đây là static source review; không chạy ứng dụng, kiểm tra HTTP headers/cookies/TLS hoặc thử khai thác. Có Spring Security dependency/config không đồng nghĩa endpoint đã được phân quyền đúng.

| Nhãn | Nghĩa |
| --- | --- |
| **Requirement** | Yêu cầu/source nghiệp vụ hoặc bảo mật; chưa phải bằng chứng triển khai |
| **Implemented in source** | Control/behavior hiện diện trong code, chưa xác nhận runtime |
| **Gap / unverified** | Chưa có evidence hoặc chưa đủ để kết luận control |
| **Recommendation** | Hành động cần xem xét riêng; chưa được deployed và ngoài R06 implementation scope |

## Xác thực và authorization quan sát được

[SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java):11–33 cấu hình một `SecurityFilterChain`:

| Matcher / control | Static behavior |
| --- | --- |
| `/css/**`, `/uploads/**`, `/api/**`, `/h2-console/**` | `.permitAll()` — không cần authenticate theo matcher này |
| Các request khác | `.anyRequest().authenticated()` |
| Login/logout | Form login; successful login đưa về `/`; logout đưa về `/login?logout` |
| User store | Một user in-memory tên `admin`, role `ADMIN`; credential dùng `{noop}` literal trong source. Giá trị password không được lặp lại ở đây. Không có external identity/DB-backed user source quan sát tại class này |
| Method/role authorization | Không thấy `@PreAuthorize`, `@RolesAllowed`, `@Secured`, role-specific request matcher hay per-resource ownership check tại controller/API boundary trong focused source search |

Vì `/api/**` permitAll, JSON revenue routes nằm trong AdminController cũng public theo matcher; controller name không tăng quyền. Gate entry/exit/slots, CCCD QR và API raw entities/member data không yêu cầu login tại Spring filter. `ADMIN` role được gán user nhưng không được dùng làm authorization rule tại các routes đã kiểm. Public `/uploads/**` cho phép truy cập đường dẫn ảnh nếu biết được path; không có authorization per-file.

Đây là **observed configuration**, không statement về production reverse proxy/firewall hoặc deployed credential rotation. Không có bearer/JWT/API key/client certificate contract trong controllers/clients đã rà.

## CSRF, CORS, session và HTTP transport

- **CSRF — source observed:** bỏ qua matcher `/api/**` và `/h2-console/**`; các request còn lại giữ Spring default CSRF behavior. Test form POST dùng explicit `.with(csrf())`, API MockMvc POST không thêm CSRF token. Không chạy request runtime.
- **CORS — not verified/config absent in inspected Java source:** focused search không tìm thấy `@CrossOrigin`, `CorsConfiguration` hoặc `cors(...)` config; không khẳng định framework/proxy behavior ngoài source scope.
- **Headers — source observed:** `frameOptions(...sameOrigin())`; không thấy CSP/HSTS/header policy tùy biến tại SecurityConfig. HSTS/TLS terminator may be external, not verified.
- **Session/cookies — unverified:** không explicit session fixation/timeout/cookie flags/session store policy trong SecurityConfig. Không suy luận effective cookie attributes từ source class này.
- **Transport — gap:** Desktop defaults tới `http://localhost:8080` và `http://localhost:8001`; ANPR camera fetch dùng HTTP localhost. Không thấy TLS configuration trong inspected callers/config. “localhost” không bảo vệ khi bind/proxy/deploy khác.

## Trust boundaries và dữ liệu nhạy cảm

| Boundary / asset | Source-observed path | Security implication / evidence gap |
| --- | --- | --- |
| Browser/user → Spring MVC | Registration/member/vehicle/card/pricing/slot forms; authenticated by `anyRequest`, not ADMIN role | Ordinary authenticated user role model chưa có; form CSRF still default-enabled, nhưng business authorization not demonstrated |
| External caller/Desktop → Spring `/api/**` | Public entry/exit/lookup/slots/revenue/QR; request DTOs accept values from caller | Any network-reachable client can supply plate/card/member/face/manual flags; deployment network restrictions unverified |
| Browser → resident media | `ResidentImageStorage` writes under upload root; WebConfig serves `/uploads/**`; SecurityConfig permits all | CCCD/registration/face images may be publicly retrievable by URL; no signed URL/auth/retention control evidenced |
| User/file → OCR QR | `/api/cccd/scan-qr` multipart file; returns raw QR and citizen identity/address/date fields | Public PII processing endpoint; service caps at10MiB/decodes an image but no caller authorization/rate-limit evidence |
| Desktop → ANPR multipart | images/video pass directly to FastAPI with 5-minute client timeout | No Spring auth token/TLS/signature between clients and ANPR evidenced; upload caps exist for recognition; service availability/rate limiting not established |
| Desktop → ANPR camera face → Spring media | Client sends `registeredImageUrl`; ANPR checks string prefix then `urlopen`; returns decision/similarity/base64 image | Unauthenticated face-comparison service trusts an HTTP URL influenced by caller; prefix is not an observed strict origin/path allowlist. No signed result bound to plate/session/user |
| Client → session flags | `EntryRequest`/`ExitRequest` carry `manualOverride`, `faceVerified`, `faceSimilarity`, `familyMemberId` | Spring service checks member authorization on entry but accepts verification/override booleans as client assertions. No operator identity/reason/audit record in DTO |
| Operator/device → barrier | WinForms opens a simulated barrier/timer after API result | Not a physical access-control protocol or authenticated actuator command; deployment distinction in [system architecture](../architecture/system.md) |

Stored or returned sensitive categories include citizen IDs/old IDs/raw CCCD QR, full names, addresses, dates, phone/email, registration/face images, plates, entry/exit timestamps and session/payment history. `GET /api/parking/open` serializes `ParkingSession` entities rather than a redacted DTO; Admin member endpoint returns raw CCCD QR. No comprehensive response allowlist/privacy review was performed.

Upload observations: ResidentImageStorage accepts multipart or base64, caps 10MiB, requires ImageIO-decodable content and writes original bytes with UUID `.jpg`; extension does not prove re-encoding. ANPR recognition caps image25MiB/video250MiB and checks suffix; multipart upload handling beyond these source checks/runtime memory limits not verified. No encrypted-at-rest, retention/TTL, delete workflow, download audit, consent record or orphan cleanup observed in reviewed flow. Do not infer a legal retention period from NV files.

## Face verification, manual override and replay/payment risks

- Entry service checks active authorized member per vehicle. If member has a registered face path, it accepts `faceVerified=true` or `manualOverride=true` supplied by the public caller; no server-to-server signed AI assertion. If no member ID and active authorization data includes face profiles, entry is rejected unless manual override; legacy/no-face data may pass.
- Desktop performs direct ANPR call and sets the boolean based on returned decision; this is a UI caller behavior, not server authenticity. Exit form can reset face flags, and confirm path does not require resident face verification/member identity. Guest verification is conditional on saved entry image and can be skipped by `manualOverride`.
- ANPR computes a motion score for camera frames, but current route does not return/use it to block successful match. The separate 3-image demo explicitly does not prove liveness gate integration. Spoof/replay resistance is **not established**.
- No idempotency key, nonce, unique request event or expected-state version in entry/exit contracts. OPEN precheck is application check before save; concurrent duplicate and response-timeout replay outcomes are not proven safe. Confirm re-finds an OPEN session; delayed/repeated confirm behavior may be “no open session” after first success, but no stable replay response contract exists.
- Fee calculation and session completion are not payment settlement: no visitor payment confirmation/receipt transaction or payment authorization gate in reviewed exit service. SubscriptionPayment is a separate record created with monthly pass; not per-session settlement evidence. Revenue JSON is public under current `/api/**` matcher.
- `manualOverride` has no operator ID, reason, authorization claim or immutable audit event in request/session mapping. NV07 manual handling/audit requirements are not equivalent to a code-enforced privileged workflow.

## Error and abuse-surface observations

- Parking API converts selected `IllegalArgumentException`/`IllegalStateException` to 400 and exposes `ex.getMessage()` in response. Other Spring errors/serialization/binding paths have no single response envelope confirmed.
- CCCD QR handler likewise returns exception message in 400 body; other failures are framework paths.
- ANPR `verify-camera` catches generic exception and includes its string in 502 detail (including failure context); it fetches caller-supplied local URL with a 10s timeout and response bytes are decoded. Prefix check only blocks nonmatching starting text; DNS/redirect/port/path constraints were not verified.
- No rate-limit, per-client quota, request audit, alerting or API key mechanism was found in focused controller/config inspection. DoS resistance at proxy/network layer is unknown.
- Spring H2 console path is public and CSRF-excluded by current matcher; whether console is enabled/reachable is deployment/config dependent and unverified.

## Requirements versus implemented status and priorities

Business requirements should be read from [NV01–NV08](../requirements/README.md), [NV07 exceptions/recovery](../requirements/nv07-exceptions-recovery.md), [NV04 resident exit](../requirements/nv04-resident-exit.md), [NV05 visitor parking](../requirements/nv05-visitor-parking.md) and [NV08 reporting](../requirements/nv08-reporting.md). Requirements for least privilege, privacy, verified face/manual handling, payment and audit remain requirements; static source observations below do not mark them closed.

| Priority | Status | Finding / recommendation (not an implemented change) |
| --- | --- | --- |
| P0 | **Implemented config: public by matcher; deployment exposure unverified** | Decide intended network boundary and protect `/api/**`, `/uploads/**`, revenue/identity/member data and H2 console with authentication/authorization before exposing beyond isolated local demo. Avoid broad public wildcard for admin JSON |
| P0 | **Gap** | Bind server-verified identity/authorization to face evidence and manual override; record operator/reason/audit. Do not trust public `faceVerified`/`manualOverride` booleans as proof |
| P0 | **Gap** | Define resident exit identity/authorization and visitor fallback/override policy; currently mandatory resident exit face end-to-end is not established |
| P1 | **Gap** | Add threat-reviewed file access, privacy classification, retention/deletion, encrypted storage/transport and upload quotas/rate controls; verify TLS and secrets/config externalization |
| P1 | **Gap** | Define idempotent/replay-safe entry/exit/payment operations and reconciliation/audit. Client timeout is not proof server did not commit |
| P1 | **Observed risk / unverified mitigation** | Restrict ANPR URL fetch to a fixed trusted Spring origin/path with robust validation and prevent SSRF; validate AI output server-side and bind to session/request |
| P2 | **Partial source control** | Review safe error envelopes, avoid exception detail disclosure, security headers, CORS policy, session-cookie policy, monitoring and testable authorization matrix |

No recommendation above is deployed or newly authorized implementation. Source authority/R01 remains PARTIAL; R02 reservation for NV06 PDF remains; NV06 classification-only vs EV charging conflict, R01 QA/DOCX lineage, liveness, payment and idempotency gaps remain open. R05 architecture/database pages are accepted and linked as evidence, not runtime security certification.

## Test and runtime evidence boundary

[ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java):51–125 contains MockMvc flow/monthly/guest-face assertions; :107–112 uses an authenticated ADMIN mock for pages; form-post tests use CSRF helpers. These are test definitions, not execution evidence and do not establish authorization correctness. No security tests for anonymous revenue/media/identity access, role matrix, CSRF exclusion consequences, spoofed flags, replay/concurrency, SSRF, retention or TLS were run in R06. Runtime production config, proxy controls and effective headers remain unverified.
