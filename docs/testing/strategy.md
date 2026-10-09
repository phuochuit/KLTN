# Chiến lược kiểm thử và trạng thái bằng chứng

[Yêu cầu khóa luận](../thesis/requirements.md) · [Phạm vi](../requirements/README.md) · [Traceability](../requirements/traceability.md) · [Kiến trúc](../architecture/system.md) · [Contracts](../api/contracts.md) · [Security](../security/overview.md)

## Nguyên tắc và kết quả thực thi

**R07 documentation authoring trước đó không chạy tests/apps/generators.** Initial Build security ngày 2026-10-09 đã chạy Spring Maven suite 102 tests, 0 failed; lần chạy full suite được ghi nhận ngày 2026-10-09 là 108/108. Sau remediation, full Spring suite được chạy lại ngày 2026-10-10 và tiếp tục PASS 108/108, gồm `ParkingFlowIntegrationTest` 54/54 theo Surefire, `WebRevenueSecurityIntegrationTest` 3, `CccdQrControllerTest` 1, `AnprServiceClientTest` 8 và các lớp unit/config/storage/migration. Desktop Release build sạch và tests 12/12 theo Build verification output ngày 2026-10-09; ANPR 10/10 là kết quả lượt chạy trước đã ghi nhận ngày 2026-10-09, không chạy lại trong remediation vì code/dependencies không đổi. Mỗi lần chạy được tách khỏi test-present và acceptance; H2/fake service không chứng minh runtime/deployment. PDF/images QA kiểm nội dung/layout đề cương, không kiểm phần mềm. Generator test requirements là cam kết đề xuất, không kết quả.

| Nhãn | Ý nghĩa |
| --- | --- |
| Existing / assertions inspected | Có test source và assertions đã đọc; không mặc nhiên PASS. |
| Indexed / not verified | Có path trong ledger, chưa đủ assertion/coverage/runtime evidence. |
| Executed | Cần command, date, environment, result/exit và output thực tế; lần chạy được ghi trong mục evidence bên dưới. |
| Proposed / needed | Scenario cần viết/chạy trong nhiệm vụ được duyệt riêng; chưa kết luận code pass/fail. |

## Các lớp kiểm thử cho topology thực tế

| Lớp | Coverage đề xuất và ranh giới |
| --- | --- |
| Unit | Spring normalize/fee/pass/rights/state/validation; ANPR OCR ordering/type selection; face thresholds/quality; Desktop request/state/error parsing. Dùng clock cố định, fake model/camera và dữ liệu synthetic; không network/DB thật. |
| Integration | Spring service–repository–media bằng isolated DB/upload root; kiểm DB/file side effects riêng. FastAPI handlers bằng fake recognizer/face/camera and internal-auth tests; Desktop HttpClient với HTTP doubles. H2 không đại diện production DB. |
| API/security | MockMvc/FastAPI request-level contracts: payload/status/validation/error/upload limit, anonymous/login/role/CSRF/media/H2 access, IDOR và flag tampering. Test actual controls và desired policy riêng; không assume public API đã có RBAC. |
| UI | Browser forms: validation, redirects, lookup, role scopes/report filters; WinForms: member selection, correction, busy/error, preview/confirm/manual, slots và barrier simulation. Form HTTP 200 không chứng minh UI events/usability; có thể dùng scripted manual acceptance khi chưa có harness. |
| End-to-end | Static source now routes Desktop image/video/face requests through Spring to FastAPI; completed tests use H2/fake ANPR, not a live end-to-end service. With isolated DB/media, synthetic people/images and fake barrier, verify same data, actor/state/evidence. Camera/reader/barrier hardware is a separate pass if approved; standalone demo does not replace gate E2E. |
| Non-functional | Load/concurrent lanes, recovery/restore, latency, privacy/accessibility/security review và representative AI evaluation; cần targets/dataset/profile đã duyệt. Không invent SLA hoặc accuracy PASS. |

## Tests hiện diện: không phải tests đã chạy

| File / IDs thực tế | Assertions và giới hạn |
| --- | --- |
| [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) | H2 memory/create-drop, upload root under `target`; Surefire records 54/54 passed in the 2026-10-10 full Spring rerun. Includes video/image proxy, guest/resident face evidence binding/consumption, session/operation/kind-scoped Management gate reads, gate-staff ownership checks, retention/holds, unassigned-list PII minimization, override allow/deny cases, CCCD QR auditing, and auth/RBAC cases. No real camera/model/Desktop network/production DB evidence. |
| [AnprServiceClientTest](../../spring-web/src/test/java/vn/edu/parking/service/AnprServiceClientTest.java) | Eight tests against a controlled local HTTP fake: internal bearer/multipart forwarding across processing routes, missing credential, malformed response and timeout/error handling. Does not call FastAPI or provision a real service secret. |
| [FastAPI internal-auth tests](../../anpr-service/tests/test_internal_auth.py) | Six tests cover missing/weak config, missing/invalid bearer, protected processing routes, health, and non-ASCII token guard; fake recognizer only. Does not load models/camera or test live Spring integration. |
| [WebRevenueSecurityIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/WebRevenueSecurityIntegrationTest.java) | Three tests go through actual filter chains and DB-backed Web login sessions: Management allowed, GATE_STAFF denied, anonymous JSON gets 401, unsafe methods retain CSRF, and untrusted CCCD preflight has no CORS allow-origin. Covers the four revenue routes and checks adjacent `/account-admin`, `/admin-data`, and CCCD route protection/audit; no browser/deployment evidence. |
| [CccdQrControllerTest](../../spring-web/src/test/java/vn/edu/parking/web/CccdQrControllerTest.java) | A synthetic internal scan exception returns a fixed generic message and still produces a failure audit event; preserves validation-message behavior covered by integration tests. |
| `completesEntryPreviewAndExitFlow` | Normalize plate, OPEN, PREVIEW, COMPLETED và phí trong tập hai giá trị; không deterministic time-boundary/concurrency/payment proof. |
| `monthlyPassKeepsOwnerAndMakesVisitFeeZero` | Active monthly fixture, owner và zero preview; không monthly confirmation/expiry-at-exit proof. |
| `rendersAllAdminPages` | Mock ADMIN và page status 200; không authorization matrix hoặc browser interaction proof. |
| `guestFaceCaptureAndExitVerificationStayBehindSpringAndBindToOneParkingSession` | Synthetic camera response; authenticated Spring capture is bound/consumed at entry and exit proof is bound/consumed at confirmation; does not establish real biometric accuracy/liveness or service-to-service runtime. |
| `residentFaceVerificationUsesServerRegistrationImageAndBindsEntryAndExitEvidence` | Confirms server reads the resident registration image, binds entry/exit face evidence and consumes exit evidence on confirm; mocked ANPR result, not camera/model/identity accuracy. |
| `clientFaceAndOverrideAssertionsCannotBypassGuestExitVerification` | Rejects client-supplied face/override assertions and exercises captured guest proof; synthetic evidence, not liveness. |
| `combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep` | Registration redirect và persisted household/owner/face path; không consent/full approval/three-face evidence. |
| `scansVietnameseCitizenCardQrOnServerWithoutBrowserBarcodeDetector` | QR fixture parsed citizen ID/name/date; không CCCD-photo face comparison. |
| `failedCitizenCardQrScanIsAuditedWithoutImageOrDecodedPii` | Invalid synthetic image returns 400 and writes a content-free failure audit (`INVALID_INPUT`) without file/decoded values. |
| `surveyPricesAreTheDefaults` | Sáu giá monthly configured; tên method không chứng minh survey data/provenance. |
| `familyMembersCanBeAuthorizedForTheSameVehicle` | Lưu hai authorized members; không face verification/alternate exit-driver enforcement. |
| `gateStaffOverrideIsAllowedOnlyForAuthorizedMemberWithoutRegistrationImageAndIsAudited` | Narrow B1-A positive case and rejection of duplicate OPEN, inactive card, unrelated/missing member and existing registration image. |
| `recognitionOutageOverrideRequiresBackendFaceEvidenceAndIsAudited` | Backend outage needs session-bound Spring face evidence and a specific reason; without evidence the entry is rejected without creating OPEN. |
| `onlyBackendReviewCanBeOverriddenAndBackendRejectCannot`, `backendReviewExitRequiresReasonedOverrideAndAuditsTheOpenSession` | Linked backend `AI_REVIEW` may use the reasoned override; AI `REJECT`, unrelated/forged evidence and missing reason do not. Covers review at entry and exit. |
| `imageRecognitionPersistsPrivateEvidenceAndScopesDownloadsToOwningDesktopSession` | Management read requires matching session/operation/kind/evidence ID and is audited with actor/time/action; wrong context, expired evidence, anonymous access and generic ID-only path are denied. |
| `unassignedParkingListsOmitResidentNameAndApartment` | Unassigned operation DTOs omit resident name/apartment while retaining gate-operational fields. |
| `managementCanReadManualOverrideReviewQueue` | Management can read the override review list; no broader audit immutability/retention proof. |
| `residentMediaIsAvailableToManagementButNotGateStaff` | Synthetic resident-registration image is readable to Management; Gate and anonymous callers are denied. This is separate from Gate Evidence permissions and its session/operation-scoped read API. |
| [ResidentImageStorageTest](../../spring-web/src/test/java/vn/edu/parking/service/ResidentImageStorageTest.java) | Four tests cover round-trip bytes, resident-reference allowlist, missing files and read-size cap; no symlink race or deployed-filesystem behavior proof. |
| [PlateNormalizerTest](../../spring-web/src/test/java/vn/edu/parking/service/PlateNormalizerTest.java):6–10 | `normalizesVietnamesePlateFormatting`: một format case; malformed/collision/property coverage chưa established. |
| [ANPR test_recognizer](../../anpr-service/tests/test_recognizer.py):4–27 | `test_normalize_plate`, `test_vehicle_type_prefers_highest_confidence`, `test_vietnamese_plate_format_prefers_plausible_result`, `test_two_line_ocr_tokens_are_ordered_by_row_then_column`. Heuristic/unit coverage, không detector/OCR accuracy/image-video API/camera/six-class metrics. |
| [Demo test_decision](../../face-verification-demo/tests/test_decision.py):4–22 | `test_classify_three_bands`, `test_pass_when_all_pairs_match`, `test_reject_when_live_does_not_match_registration`, `test_review_when_quality_warning_exists`. Demo decision functions, không gate integration hoặc actual liveness. |
| [Desktop HTTP tests](../../desktop-winform.tests/ParkingApiClientAuthenticationTests.cs) | Mocked-handler tests include authenticated Spring-origin image/video/face requests, no bearer forwarding to ANPR, origin scoping, and mutation non-replay; no live Spring/ANPR or WinForms visual/runtime evidence. |

## Tests actually executed

- **2026-10-10, post-remediation full Spring rerun:** from `spring-web`, `..\tools\apache-maven-3.9.11\bin\mvn.cmd test`; Surefire reports in `spring-web/target/surefire-reports` record **108 tests, 0 failures/errors/skips**; `ParkingFlowIntegrationTest` **54/54**, `WebRevenueSecurityIntegrationTest` **3/3**, `CccdQrControllerTest` **1/1**, `AnprServiceClientTest` **8/8**, plus unit/config/storage and `SystemAccountMigrationTest`. Uses H2 `create-drop`, synthetic media, controlled local HTTP fake for ANPR, and minimal legacy-table fixtures for isolated V1–V5 H2 MySQL-mode migration rehearsal; not production DB/runtime evidence. MySQL thật, TLS, live Spring–FastAPI/camera và backup/restore remain PENDING. The suite includes member-selection, H2 account-mutation concurrency and signed-role slot-contact minimization regressions.
- The media-access regression first failed as expected before the policy change (Management got 403), then passed after the route became Management-only; the full suite includes this check. The new QR audit assertions failed before controller instrumentation, then passed for successful and invalid-input scans.
- The QR generic-error regression failed before sanitization (`private decoder path` was returned), then passed after unexpected scan failures were mapped to a fixed message; the full Spring suite passed afterward.
- **2026-10-09, Desktop:** Build verification record reports `dotnet build desktop-winform\ParkingGateDesktop.csproj --configuration Release` with **0 warnings/errors** and `dotnet test desktop-winform.tests\ParkingGateDesktop.Tests.csproj --configuration Release --no-restore` with **12/12 passed**. This count is from the Build verification output, not a persisted `.trx` report; the tests use mocked HTTP, with no live Spring/ANPR or interactive WinForms run.
- **2026-10-09, prior ANPR result (not rerun in remediation):** project-local Python 3.12.10 `.venv` with `requirements.txt` plus test requirements; `PYTHONDONTWRITEBYTECODE=1 python -m pytest -p no:cacheprovider tests/`: **10 passed**. This includes recognizer helper tests and FastAPI auth tests with a fake recognizer; no model, camera, real internal credential or E2E behavior. No global Python environment was changed.

H2 declaration does not prove production schema, MySQL concurrency, media deployment configuration or startup behavior.

## NV01–NV08: coverage cần bổ sung

Mọi scenario dưới là **proposed**, trừ subset existing đã ghi phía trên. Policy đang tranh chấp phải được quyết định trước khi biến thành expected result; characterization tests của code hiện tại phải tách khỏi acceptance theo nguồn.

| NV | Unit/integration/API/UI/E2E scenarios quan trọng | Existing subset / remaining |
| --- | --- | --- |
| [NV01](../requirements/nv01-registration.md) | Duplicates/concurrent registration, consent/approval, per-vehicle rights (same household không đủ), ba face comparisons/quality/manual reason, reverse lookup, privacy và media rollback. | Registration/authorized-list/QR assertions; không full identity/liveness/consent/activation/security coverage. |
| [NV02](../requirements/nv02-cards-passes-pricing.md) | Card unknown/missing/mismatch/inactive, expiry at entry/exit; gói 30 ngày/renewal/overlap/debt/replacement; deterministic clock, rounding/day/night/overnight, price snapshot; payment/refund/waiver history và atomicity. | Monthly preview/default prices/fee subset; chưa complete boundary/settlement/refund/reconciliation. |
| [NV03](../requirements/nv03-resident-entry.md) | Normalized/invalid plate, official-vs-AI type, authorized driver/card/pass/face; warning/manual result, one OPEN/evidence/slot; double-click, concurrent/replayed request và commit-response loss. | Sequential happy path; không real AI/mandatory liveness/concurrency/idempotency/audit proof. |
| [NV04](../requirements/nv04-resident-exit.md) | OPEN ambiguity/card fallback, plate/type/evidence mismatch; alternate authorized driver và mandatory face; preview vs confirm price changes/time boundary, payment before closure, repeat/concurrent confirms. | Happy-path/zero preview; resident exit rights/face/payment chưa proven. Giữ source CLOSED khác observed COMPLETED. |
| [NV05](../requirements/nv05-visitor-parking.md) | No resident enrollment, required capture/quality/ownership, absent entry-face/card lost/mismatch, fee/payment/waiver/receipt/report, exit after settlement failure và duplicate confirm. | Guest client-flag branch; genuine face/mandatory capture/payment/ownership chưa verified. |
| [NV06](../requirements/nv06-vehicle-classification.md) | Taxonomy/price mapping; fixed OCR/type fixtures; representative confusion matrix/accuracy, low confidence/unsupported class; correction giữ prediction/verified/reason/actor/history. | ANPR eight unit/auth cases không six-class quality proof. A classification-only/B charging unresolved; rights/connector/expiry/full-position/ChargingSession/hardware tests chỉ applicable nếu B được duyệt. |
| [NV07](../requirements/nv07-exceptions-recovery.md) | API/AI/camera/model/DB/media failure injection, timeout/malformed responses, controlled retries/manual authorization+reason+audit; partial DB/file/slot/barrier commit, offline/restart/resync và replay. | Không recovery/offline/audit/race/financial-idempotency result. Error guidance không durable queue. |
| [NV08](../requirements/nv08-reporting.md) | Known totals từ sessions/payments/prices; period cutoff/timezone/filter, refund/cancel/waiver/unpaid records, reconciliation; permission/sensitive view/export audit, dashboard counts. | Page 200 không known-total/report correctness/paid proof. Charging report phụ thuộc unresolved B. |

Supporting features cần coverage theo [scope map](../requirements/README.md): slots assignment/borrowing expiry/cancel/status/release/dispatch, batch count/duplicates/unassigned, health/dependency errors, QR/media limits và admin forms. Không đồng nhất parking slots với charging stations.

## Cross-cutting protocol và thứ tự đề xuất

1. **Chốt policy/fixtures:** source authority, NV06 A/B, activation/payment/debt/pass-expiry, rights/waiver; giữ unresolved nếu chưa có người duyệt. Dùng synthetic identities/images, consent/privacy safeguards và isolated storage.
2. **Deterministic domain/API regressions:** pricing/date/rights/state, validation/error envelopes, anonymous/role/CSRF/media tests. Gate evidence tests cover GATE_STAFF owner/device-session scope and Management session/operation/kind/evidence-ID access; deployed storage and production DB behavior remain unverified. Client-supplied ANPR URL-fetch was removed; no live proxy test is claimed.
3. **Trust/security adversarial tests:** forged face/manual flags, unauthorized member/IDOR, management-only resident uploads/revenue/H2, upload/path validation, and rejection of the removed caller-URL fetch contract. Actual SecurityConfig behavior and fake ANPR responses are not end-to-end PASS.
4. **Idempotency/payment/fault injection:** same operation retries sau timeout/commit, concurrent same-plate entry/exit/payment, partial file/DB/slot failure, recovery/restore. Chưa có payment provider/idempotency-key contract được chứng minh; không invent integration hoặc guarantee.
5. **UI/E2E và site evaluation:** verify Web–Desktop shared data, real deployment DB/profile/media/TLS/camera/model nếu được duyệt. Benchmark representative data và physical hardware riêng; không thay bằng mock/H2/demo results.

Mỗi run sau này cần exact test ID/file, expected rule/source, command/date/tool versions, DB/profile/model/dataset, output/exit/result, skipped cases và remaining gaps. Proposed report/metrics cần phương pháp và dataset, không chỉ số lấy từ test names. Không ghi PASS khi chỉ index/read source.

Source-authority/R01, payment, authorization, liveness, resident-exit rights, idempotency/recovery và production security vẫn mở. [Thesis requirements](../thesis/requirements.md) nối nghĩa vụ học thuật tới evidence; publication của chiến lược không đóng các gaps hoặc chứng nhận nghiệm thu.
