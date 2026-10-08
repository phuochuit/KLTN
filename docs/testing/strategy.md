# Chiến lược kiểm thử và trạng thái bằng chứng

[Yêu cầu khóa luận](../thesis/requirements.md) · [Phạm vi](../requirements/README.md) · [Traceability](../requirements/traceability.md) · [Kiến trúc](../architecture/system.md) · [Contracts](../api/contracts.md) · [Security](../security/overview.md)

## Nguyên tắc và kết quả thực thi

**Không chạy tests/apps/generators trong R07.** Test hiện diện/assertions đã đọc, test thực thi và acceptance là trạng thái riêng. Những slice tài liệu trước cũng không cung cấp test-executed results; điều này không khẳng định toàn bộ lịch sử project chưa từng chạy test. PDF/images QA kiểm nội dung/layout đề cương, không kiểm phần mềm. Generator test requirements là cam kết đề xuất, không kết quả.

| Nhãn | Ý nghĩa |
| --- | --- |
| Existing / assertions inspected | Có test source và assertions đã đọc; không mặc nhiên PASS. |
| Indexed / not verified | Có path trong ledger, chưa đủ assertion/coverage/runtime evidence. |
| Executed | Cần command, date, environment, result/exit và output thực tế; **không có execution mới trong trang này**. |
| Proposed / needed | Scenario cần viết/chạy trong nhiệm vụ được duyệt riêng; chưa kết luận code pass/fail. |

## Các lớp kiểm thử cho topology thực tế

| Lớp | Coverage đề xuất và ranh giới |
| --- | --- |
| Unit | Spring normalize/fee/pass/rights/state/validation; ANPR OCR ordering/type selection; face thresholds/quality; Desktop request/state/error parsing. Dùng clock cố định, fake model/camera và dữ liệu synthetic; không network/DB thật. |
| Integration | Spring service–repository–media bằng isolated DB/upload root; kiểm DB/file side effects riêng. FastAPI handlers bằng fake recognizer/face/camera/URL fetch; Desktop HttpClient với HTTP doubles. H2 không đại diện production DB. |
| API/security | MockMvc/FastAPI request-level contracts: payload/status/validation/error/upload limit, anonymous/login/role/CSRF/media/H2 access, IDOR và flag tampering. Test actual controls và desired policy riêng; không assume public API đã có RBAC. |
| UI | Browser forms: validation, redirects, lookup, role scopes/report filters; WinForms: member selection, correction, busy/error, preview/confirm/manual, slots và barrier simulation. Form HTTP 200 không chứng minh UI events/usability; có thể dùng scripted manual acceptance khi chưa có harness. |
| End-to-end | Web/Desktop → Spring và Desktop → ANPR với isolated DB/media, synthetic người/ảnh, fake barrier; kiểm cùng dữ liệu, đúng actor/state/evidence. Camera/reader/barrier vật lý là pass riêng nếu được duyệt; standalone demo không thay gate E2E. |
| Non-functional | Load/concurrent lanes, recovery/restore, latency, privacy/accessibility/security review và representative AI evaluation; cần targets/dataset/profile đã duyệt. Không invent SLA hoặc accuracy PASS. |

## Tests hiện diện: không phải tests đã chạy

| File / IDs thực tế | Assertions và giới hạn |
| --- | --- |
| [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java):38–218 | H2 memory/create-drop, upload root `target/test-uploads`; tám test methods dưới đây. Không real camera/model/Desktop/network/production DB evidence. |
| `completesEntryPreviewAndExitFlow` | Normalize plate, OPEN, PREVIEW, COMPLETED và phí trong tập hai giá trị; không deterministic time-boundary/concurrency/payment proof. |
| `monthlyPassKeepsOwnerAndMakesVisitFeeZero` | Active monthly fixture, owner và zero preview; không monthly confirmation/expiry-at-exit proof. |
| `rendersAllAdminPages` | Mock ADMIN và page status 200; không authorization matrix hoặc browser interaction proof. |
| `guestFaceIsStoredAtEntryAndRequiredAtExit` | One-pixel image, thiếu flag preview400, true flag confirm; không biometric/liveness/ownership/settlement test. |
| `combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep` | Registration redirect và persisted household/owner/face path; không consent/full approval/three-face evidence. |
| `scansVietnameseCitizenCardQrOnServerWithoutBrowserBarcodeDetector` | QR fixture parsed citizen ID/name/date; không CCCD-photo face comparison. |
| `surveyPricesAreTheDefaults` | Sáu giá monthly configured; tên method không chứng minh survey data/provenance. |
| `familyMembersCanBeAuthorizedForTheSameVehicle` | Lưu hai authorized members; không face verification/alternate exit-driver enforcement. |
| [PlateNormalizerTest](../../spring-web/src/test/java/vn/edu/parking/service/PlateNormalizerTest.java):6–10 | `normalizesVietnamesePlateFormatting`: một format case; malformed/collision/property coverage chưa established. |
| [ANPR test_recognizer](../../anpr-service/tests/test_recognizer.py):4–27 | `test_normalize_plate`, `test_vehicle_type_prefers_highest_confidence`, `test_vietnamese_plate_format_prefers_plausible_result`, `test_two_line_ocr_tokens_are_ordered_by_row_then_column`. Heuristic/unit coverage, không detector/OCR accuracy/image-video API/camera/six-class metrics. |
| [Demo test_decision](../../face-verification-demo/tests/test_decision.py):4–22 | `test_classify_three_bands`, `test_pass_when_all_pairs_match`, `test_reject_when_live_does_not_match_registration`, `test_review_when_quality_warning_exists`. Demo decision functions, không gate integration hoặc actual liveness. |
| Desktop | Evidence ledger chưa thiết lập automated suite/assertions. Không kết luận toàn repo không có; cần focused tests hoặc manual protocol. |

Assertions được tái sử dụng từ focused source reads; **không có run result được gán cho các test trên**. H2 declaration không proof test startup thành công hoặc production schema đúng.

## NV01–NV08: coverage cần bổ sung

Mọi scenario dưới là **proposed**, trừ subset existing đã ghi phía trên. Policy đang tranh chấp phải được quyết định trước khi biến thành expected result; characterization tests của code hiện tại phải tách khỏi acceptance theo nguồn.

| NV | Unit/integration/API/UI/E2E scenarios quan trọng | Existing subset / remaining |
| --- | --- | --- |
| [NV01](../requirements/nv01-registration.md) | Duplicates/concurrent registration, consent/approval, per-vehicle rights (same household không đủ), ba face comparisons/quality/manual reason, reverse lookup, privacy và media rollback. | Registration/authorized-list/QR assertions; không full identity/liveness/consent/activation/security coverage. |
| [NV02](../requirements/nv02-cards-passes-pricing.md) | Card unknown/missing/mismatch/inactive, expiry at entry/exit; gói 30 ngày/renewal/overlap/debt/replacement; deterministic clock, rounding/day/night/overnight, price snapshot; payment/refund/waiver history và atomicity. | Monthly preview/default prices/fee subset; chưa complete boundary/settlement/refund/reconciliation. |
| [NV03](../requirements/nv03-resident-entry.md) | Normalized/invalid plate, official-vs-AI type, authorized driver/card/pass/face; warning/manual result, one OPEN/evidence/slot; double-click, concurrent/replayed request và commit-response loss. | Sequential happy path; không real AI/mandatory liveness/concurrency/idempotency/audit proof. |
| [NV04](../requirements/nv04-resident-exit.md) | OPEN ambiguity/card fallback, plate/type/evidence mismatch; alternate authorized driver và mandatory face; preview vs confirm price changes/time boundary, payment before closure, repeat/concurrent confirms. | Happy-path/zero preview; resident exit rights/face/payment chưa proven. Giữ source CLOSED khác observed COMPLETED. |
| [NV05](../requirements/nv05-visitor-parking.md) | No resident enrollment, required capture/quality/ownership, absent entry-face/card lost/mismatch, fee/payment/waiver/receipt/report, exit after settlement failure và duplicate confirm. | Guest client-flag branch; genuine face/mandatory capture/payment/ownership chưa verified. |
| [NV06](../requirements/nv06-vehicle-classification.md) | Taxonomy/price mapping; fixed OCR/type fixtures; representative confusion matrix/accuracy, low confidence/unsupported class; correction giữ prediction/verified/reason/actor/history. | ANPR four unit cases không six-class quality proof. A classification-only/B charging unresolved; rights/connector/expiry/full-position/ChargingSession/hardware tests chỉ applicable nếu B được duyệt. |
| [NV07](../requirements/nv07-exceptions-recovery.md) | API/AI/camera/model/DB/media failure injection, timeout/malformed responses, controlled retries/manual authorization+reason+audit; partial DB/file/slot/barrier commit, offline/restart/resync và replay. | Không recovery/offline/audit/race/financial-idempotency result. Error guidance không durable queue. |
| [NV08](../requirements/nv08-reporting.md) | Known totals từ sessions/payments/prices; period cutoff/timezone/filter, refund/cancel/waiver/unpaid records, reconciliation; permission/sensitive view/export audit, dashboard counts. | Page 200 không known-total/report correctness/paid proof. Charging report phụ thuộc unresolved B. |

Supporting features cần coverage theo [scope map](../requirements/README.md): slots assignment/borrowing expiry/cancel/status/release/dispatch, batch count/duplicates/unassigned, health/dependency errors, QR/media limits và admin forms. Không đồng nhất parking slots với charging stations.

## Cross-cutting protocol và thứ tự đề xuất

1. **Chốt policy/fixtures:** source authority, NV06 A/B, activation/payment/debt/pass-expiry, rights/waiver; giữ unresolved nếu chưa có người duyệt. Dùng synthetic identities/images, consent/privacy safeguards và isolated storage.
2. **Deterministic domain/API regressions:** pricing/date/rights/state, validation/error envelopes, anonymous/role/CSRF/media tests. Kiểm [contract](../api/contracts.md) cho ANPR upload/status cases, gồm verify-camera URL prefix400 riêng với ValueError422, RuntimeError503, fetch/other502.
3. **Trust/security adversarial tests:** forged face/manual flags, unauthorized member/IDOR, public uploads/revenue/H2, unsafe URL fetch/upload/path. Actual SecurityConfig permissive observations không là desired-security PASS.
4. **Idempotency/payment/fault injection:** same operation retries sau timeout/commit, concurrent same-plate entry/exit/payment, partial file/DB/slot failure, recovery/restore. Chưa có payment provider/idempotency-key contract được chứng minh; không invent integration hoặc guarantee.
5. **UI/E2E và site evaluation:** verify Web–Desktop shared data, real deployment DB/profile/media/TLS/camera/model nếu được duyệt. Benchmark representative data và physical hardware riêng; không thay bằng mock/H2/demo results.

Mỗi run sau này cần exact test ID/file, expected rule/source, command/date/tool versions, DB/profile/model/dataset, output/exit/result, skipped cases và remaining gaps. Proposed report/metrics cần phương pháp và dataset, không chỉ số lấy từ test names. Không ghi PASS khi chỉ index/read source.

Source-authority/R01, payment, authorization, liveness, resident-exit rights, idempotency/recovery và production security vẫn mở. [Thesis requirements](../thesis/requirements.md) nối nghĩa vụ học thuật tới evidence; publication của chiến lược không đóng các gaps hoặc chứng nhận nghiệm thu.
