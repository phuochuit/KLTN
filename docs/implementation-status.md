# Trạng thái implementation và giới hạn bằng chứng

[Mục lục](README.md) · [Tổng quan](overview.md) · [Traceability](requirements/traceability.md) · [Kiểm thử](testing/strategy.md)

## Cách đọc trạng thái

Đây là tổng hợp evidence đã thu thập, không full repository audit hay application readiness certificate. Acceptance tài liệu không xác nhận code đã đáp ứng nghiệp vụ. Verification lịch sử ngày 2026-10-09/10 ghi nhận Spring 108/108, Desktop 12/12 và Release build sạch; ANPR 10/10 là lượt chạy trước. Lượt Spring mới nhất ngày 2026-10-24 trên nhánh RBAC đạt 128/128, gồm permission/role regressions và H2 V1–V6 rehearsal (`20261024T020410Z-spring-04215e1a`); Desktop/ANPR không chạy lại trong lượt này. Các kết quả H2/fake không xác nhận MySQL thật, TLS, Spring–FastAPI/camera thực hoặc backup/restore; các mục này vẫn PENDING. Mức inspection trong tài liệu cũ là snapshot; [testing](testing/strategy.md) ghi test IDs/assertions và executions, còn [architecture](architecture/system.md) và [operations](operations/setup-and-troubleshooting.md) ghi actual boundaries.

| Nhãn | Ý nghĩa |
| --- | --- |
| **Documented** | Có yêu cầu nguồn/đặc tả; authority hoặc approval rule vẫn có thể unresolved |
| **Code observed** | Symbol/body/call path được đọc tĩnh; không runtime result |
| **Partially implemented** | Có code cho một subset cụ thể, không completion toàn NV |
| **Not verified** | Chưa đủ assertions/runtime/source authority; không tự kết luận không có implementation |
| **Known discrepancy** | Nguồn/claim khác actual observation hoặc variants xung đột; cần quyết định/kiểm chứng |

## Cập nhật 2026-10-26 — main `eb471d7`

Mục này bổ sung cho bảng `abd6183` bên dưới, không thay thế bản ghi lịch sử. Đo lại trên `main` ngày 2026-10-26:

| Hạng mục | Kết quả đo | Ghi chú |
| --- | --- | --- |
| Spring test | 152 tests, 0 failures, 0 errors (BUILD SUCCESS) | thay cho 108/128 của các nhánh trước |
| ANPR test | 38 passed | |
| OCR `plate-960.jpg` | 2,4s → 1,19s | `denoised → enlarged → straightened → equalized`, early-stop `conf ≥ 0,80` |
| OCR ảnh camera | 4,4s → 0,73s | |
| Song song biển + khuôn mặt | 1,224s / 1,193s / 1,200s (3 lượt) | mỗi tác vụ và tổng ≤5s |

Các thay đổi so với `abd6183` làm thay đổi nội dung NV03/NV07 bên dưới:

- **Liveness tách khỏi face-match**: `LivenessStatus` (`SPOOF`/`REVIEW`/`NOT_EVALUATED`); `SPOOF` không mở barrier; `REVIEW` cần xác nhận qua `ManualOverrideDialog`; ảnh tĩnh trả `NOT_EVALUATED`. Luồng có UI: `MainForm`, `ManualOverrideDialog`, `EvidenceComparisonDialog`, `exceptions.html`.
- **Presence Desktop**: heartbeat 2s, polling 2s, timeout 6s — Desktop chết tự rời danh sách trạm.
- **OCR**: bỏ `clahe`, thêm early-stop, bỏ log profiling `AI_PROFILE`.
- **Bàn giao máy mới**: `setup-new-machine.ps1` kiểm tra 13 mục, tự sinh token nội bộ và khoá JWT, tự tải 2 model ONNX.

Giới hạn vẫn giữ nguyên: chưa có anti-replay video/deepfake, chưa cân ngưỡng trên camera thật, chưa nghiệm thu TLS/KMS/thiết bị thật. Flyway vẫn **không có** trong `main`: schema do `ddl-auto=update` cộng 11 script SQL thủ công trong `scripts/database/`; các dòng "additive Flyway V1–V6" bên dưới thuộc snapshot nhánh cũ, không áp cho `main`.

## NV01–NV08

## Trạng thái hiện hành — main `abd6183`

Đối chiếu static source, không dựa bản sao artifact. Không chạy application suite/build, SQL, startup hay camera; không có current-main runtime PASS. Kết quả108/128 Spring, Desktop12 và ANPR10 ở đoạn mở đầu là lịch sử nhánh Security + WIP, không áp cho main. Current assertions và đúng giới hạn tại [testing](testing/strategy.md).

| Phạm vi | Code observed main | Giới hạn chưa được kiểm chứng |
| --- | --- | --- |
| NV01 hồ sơ/quyền | Web registration, QR autofill, private resident media, per-vehicle members; entry có legacy no-face-profile/null-member branch | Consent/approval/three-way identity/liveness; mandatory resident exit rights/exitMember |
| NV02 gói/giá/thu | Plate-as-card; PricingRule; SubscriptionPayment + PaymentTransaction receipt, method/reference/collector/shift | External settlement, debt/refund, versioned prices, card–payment atomicity toàn controller |
| NV03 vào | `/api/ai` Spring evidence → entry; required mặc định, member/OPEN/slot checks, ManualOverrideService correction | Warning OPEN, legacy flags khi required=false/no-ID, distinct-key concurrent OPEN; không hardware proof |
| NV04 ra | Preview không bind/consume; confirm evidence row locks, COMPLETED/fee, receipt phí dương, clear slot | Resident face optional, exitMember chưa điền; payment waived/refund/provider verification chưa đầy đủ |
| NV05 khách | Optional entry face asset, guest verification dùng consumed entry image; positive-fee payment method/receipt | Guest face result chưa cross-link đầy đủ đến OPEN, capture/ownership/liveness và external settlement chưa proven |
| NV06 loại xe | Registry/YOLO/OCR/heuristic; original/corrected plate/type và reason/actor audit | Six-class/EV accuracy; FACE_FAILED FAIL khác upstream REJECT, blank plate không phát vehicle evidence; charging chưa có acceptance |
| NV07 phục hồi | Desktop durable outbox + operationId/hash/server cached response; IBarrierController simulator | Không account/origin-partitioned queue, evidence expiry/ownership, crash/DB-file/barrier recovery; không offline admission guarantee |
| NV08 báo cáo | Dashboard sums, PaymentController receipts/reconciliation, shift summaries | Các cơ sở tổng hợp khác nhau; chưa known-total/timezone/cutoff/refund hoặc settlement PASS |
| Accounts/security | JWT mặc định bật, Web session/CSRF, grants + legacy role compatibility, active account/session/shift | Explicit disabled JWT permit-all compatibility; deployed TLS/keys/account/privacy/abuse chưa nghiệm thu |
| Media/startup | Private root residents/evidence, TTL/retention, scheduled cleanup mặc định tắt; properties MySQL/Hibernate update, SQL thủ công, no Flyway | Không finite holds của feature; runtime roots/encryption/backup/restore chưa verified. Health identity fields khớp launcher nhưng UP không full readiness |

Chi tiết field/constraints tại [dictionary](architecture/data-dictionary.md), controls tại [security](security/overview.md), API tại [contracts](api/contracts.md), các nhánh và failure boundaries tại [system](architecture/system.md).

## Snapshot NV và supporting components lịch sử — Security + WIP

Các bảng kế tiếp bảo toàn quan sát và kết quả của nhánh trước, không phải characterization main. Nhận xét cũ về ba override, absence payment/outbox, V1–V6, storage và launcher mismatch không thay thế bảng hiện hành phía trên. Test lịch sử không được dùng như coverage/execution của main.

Các location viết tắt bên dưới: [ParkingService](../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) (S), [ParkingApiController](../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) (P), [AdminController](../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) (A), [MainForm](../desktop-winform/MainForm.cs) (D), [Spring integration tests](../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) (T). Test IDs/assertions/executions được phân biệt tại strategy; kết quả H2 không phải runtime/deployment evidence.

| Documented / owner | Code observed / subset partially implemented | Actual verification level | Gaps / known discrepancy |
| --- | --- | --- | --- |
| [NV01](requirements/nv01-registration.md): household/person/vehicle, consent, ba ảnh, approval, quyền theo xe | P:lookup, entry face evidence; S:active authorized-member checks; Web registration/QR/face upload | Static call paths/templates; Spring full suite 108/108 PASS on 2026-10-10, including resident face evidence, registration/QR parsing, content-free QR scan audit, generic internal scan errors and resident-entry member rejection | Full approval/consent/dedup/privacy chưa verified; same household không mọi xe; three approved gate override exceptions do not replace registration/identity controls; QR route still returns parsed CCCD data; no three-way CCCD/registration/realtime comparison |
| [NV02](requirements/nv02-cards-passes-pricing.md): card/pass/30-day/payment/debt/giá/history | A:saveCard monthly/payment record; S:calculateFee/hasValidMonthlyPass; cards/pricing forms | Static bodies; T monthly zero preview/default prices và fee subset | Record không paid/settled proof; renewal/replacement/refund/debt/price snapshot chưa verified; entry-date eligibility vs source validity-at-exit; payment-only activation vs debt approval unresolved |
| [NV03](requirements/nv03-resident-entry.md): rights/evidence/one OPEN/barrier | S:entry/precheck/member validation; P:authenticated image/video OCR and server-bound recognition/face evidence; D gate flow | Static bodies; Spring full suite 108/108 PASS on 2026-10-10, including resident/guest evidence binding, all three approved reasoned override classes and missing/inactive/unrelated member rejection | **Warning thiếu/sai card/hết pass vẫn có thể tạo OPEN**; UI barrier không mở không là rollback. Quality/liveness/type mismatch, MySQL race/idempotency chưa verified; source Bước9 variants khác |
| [NV04](requirements/nv04-resident-exit.md): OPEN/evidence/authorized collector/fee/closure | S:preview/confirm and OPEN lookup; P:server-bound exit recognition and optional face proof; D preview/confirm | Static bodies; Spring H2 test covers resident face proof binding through confirm | **Resident confirm không bắt buộc face verification**; alternate authorized collector policy not established; payment/waiver/replay/MySQL races chưa verified. **Source CLOSED vs code COMPLETED** |
| [NV05](requirements/nv05-visitor-parking.md): khách evidence/ownership/payment-before-close | Optional Spring camera capture at entry; private guest evidence; same-session exit face verification when entry capture exists | Static body; Spring H2 test covers capture→entry bind→exit verification/consume; client-supplied flags rejected | Capture remains optional; guest sessions without stored entry-face image do not trigger this exit check. Payment/lost-card/ownership/receipt/audit and liveness efficacy remain unverified |
| [NV06](requirements/nv06-vehicle-classification.md): verified taxonomy/correction; A/B variants | S:resolveVehicleType registry type/guest fallback; [recognizer](../anpr-service/app/recognizer.py) YOLO/OCR/heuristics | Static processing; ANPR unit assertions về normalize/type/format/token ordering | Không six-class/EV accuracy hoặc correction reason-history proof. **A classification-only vs B EV charging unresolved**; slots không ChargingSession/connector/rights proof |
| [NV07](requirements/nv07-exceptions-recovery.md): quality/manual/outage/recovery/privacy | D errors/manual/barrier; ANPR error branches; [ApiResponseReader](../desktop-winform/ApiResponseReader.cs):8–74 messages | Static source; missing-image, backend-linked `AI_REVIEW`, and backend-recorded service-outage overrides are covered by reason/evidence/audit integration tests; no operational outage/recovery test | Unlisted manual cases remain fail-closed; native restart không recovery guarantee; offline queue/resync, DB-file compensation, idempotency/locking và privacy chưa verified |
| [NV08](requirements/nv08-reporting.md): filters/totals/reconciliation/permissions | A:dashboard/sessions/revenue methods; dashboard filter/chart/detail; COMPLETED fees + subscription amounts; dashboard requires `DASHBOARD_VIEW` + `REVENUE_VIEW`; revenue API uses Web session + `REVENUE_VIEW` | Static query/UI observations; actual Web-session integration verifies permission/anonymous boundaries, không known-total test | Revenue không visit settlement proof; Payment–Session–PriceRule lineage, cutoff/refund/waiver/export/charging dashboard còn unresolved |

## Supporting components: không thêm NV chính thức

| Area / evidence owner | Observed và verification | Gaps |
| --- | --- | --- |
| [Slots/lookup/health](requirements/traceability.md) | P slot/lookup/health routes → S assignment, borrow/cancel, status/release/dispatch, unassigned/batch methods; D map và slot-dialog actions. Static; no runtime allocation result | Race uniqueness, dispatch preconditions, borrow-expiry refresh/physical occupancy, real site layout và recovery chưa verified; health status/service không dependency/API readiness; launcher expectations đang lệch contract |
| [Admin/QR/media](operations/user-guide.md) | Observed navigation/forms; resident media defaults `var/private-media`; `/uploads/**` requires `PRIVATE_EVIDENCE_VIEW`. Gate evidence uses separate private storage, session/operation/kind/ID-bound reads, owner/device-session + OPEN-session scope for `GATE_OPERATE`, audited access, 30-day retention/cleanup, and finite holds requiring `PRIVATE_EVIDENCE_VIEW`; H2 verifies selected access/binding flows | Deployment storage root, encryption, backup/restore, broad privacy review and browser/device acceptance remain unverified; resident CCCD/profile media remains a permission-wide surface |
| [ANPR](architecture/system.md) / [contracts](api/contracts.md) | Desktop image/video/face processing calls go through JWT-protected Spring routes; FastAPI processing routes require external internal Bearer. Spring stores image/video/face evidence and binds recognition to entry/exit; caller-supplied face URL fetch was removed | Spring ANPR client tests passed 8/8 against a controlled fake HTTP service; FastAPI suite passed 10/10 using a fake recognizer; H2 exercises resident and guest face flow. Live service, camera/model accuracy/liveness, production credentials and deployment remain unverified |
| [Standalone face demo](architecture/system.md) | Three-image API riêng, models fallback và decision unit tests present | Không gate caller/integrated enrollment; demo livenessChecked=false; launcher interpreter path lệch repo ANPR venv |
| [Persistence/startup](operations/setup-and-troubleshooting.md) | JPA/entities/repositories, additive Flyway V1–V6 and source configuration; MySQL runtime driver/H2 test scope; V1–V6 rehearsed only on isolated H2 MySQL mode | Actual datasource/profile/DDL/credentials mapping chưa verified; README H2/MySQL auto-creation claims, Laragon probe/cloud-label mismatch không deployment guarantee |
| [Desktop](operations/user-guide.md) | WinForms .NET8, API URLs, image/video, entry/exit/preview/lookup/face/manual/map; label/timer barrier | No UI runtime/global SDK update/hardware verification; error guidance không transaction recovery; no automatic safe replay claim |
| [Security](security/overview.md) | [SecurityConfig](../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) separates Web session and JWT Desktop chains; DB role permission grants are authoritative, account/grant changes invalidate sessions, and route-level RBAC, operation-scoped gate evidence, retention/audit, Spring AI proxies, revenue permission and three approved override classes have isolated test evidence | Main integration remains unverified; MySQL concurrency, TLS, key provisioning, backup/restore and deployed behavior remain gaps. No end-to-end production-hardening PASS |
| [Academic/testing](thesis/requirements.md) | Dated regulation extract, proposal commitments and tests-present/executed/needed are distinguished; 2026-10-09 local suites are recorded in testing strategy | Track/applicability/DOCX/QA/survey source authority chưa chốt; no thesis acceptance or algorithmic contribution certificate |

## Mười nhóm deployment gaps được giữ nguyên

### Ghi nhận mới hơn từ `main`

Nội dung dưới đây bảo toàn ghi chép `PROJECT_GAPS.md` cập nhật 21/10/2026 trên `main`, khác snapshot feature được tổng hợp trong bảng NV ở trên. Các mục “đã hoàn tất” là **ghi nhận của tài liệu nguồn**, không phải kết quả đã chạy lại hoặc audit độc lập của đợt phục hồi docs. Không dùng chúng tự đóng các quyết định P01–P09 hoặc biến kết quả test nhánh khác thành PASS cho `main`.

Ghi chép nguồn báo đã có:

- `IBarrierController`, barrier mô phỏng, tự đóng theo cấu hình và trạng thái lỗi thiết bị.
- Cấu hình trạm gác local trong `station-settings.json`, tương thích biến môi trường cũ.
- Camera USB index/RTSP/HTTP/HTTPS qua Desktop → Spring → FastAPI, kiểm URI và khóa đồng thời.
- Sổ giao dịch phí lượt/phí gói, biên nhận, đối soát ca, quyền `PAYMENT_RECONCILE` và báo cáo đối soát.
- Desktop JWT, token nội bộ, chống replay, evidence/media private, thời hạn lưu và audit trước/sau.
- Vai trò động, tiền tố mã nhân viên, hồ sơ nhân viên, khóa/mở tài khoản và nhật ký bảo mật.
- Outbox Desktop ghi atomic và retry; Spring dùng UUID + SHA-256 payload và response lưu trước để xử lý gửi trùng. Đây không tự chứng minh exactly-once hoặc phục hồi live an toàn.
- Quy tắc nhóm ô tô/xe hai bánh cho phân chỗ tự động, ô cố định, đỗ nhờ và điều phối.
- Script release, backup MySQL/private data, health check, checklist nghiệm thu/bàn giao.
- JWT bật mặc định, request nghiệp vụ không token trả 401 trong cấu hình đó; không suy ra tắt JWT vẫn fail-closed.

Tám nhóm việc còn thiếu được nguồn mới giữ lại:

1. Adapter thiết bị thật: relay/PLC, vòng từ, RFID, máy in, POS; cần hãng/model/protocol.
2. Liveness chống ảnh/video phát lại và hiệu chỉnh ngưỡng/REVIEW bằng camera thực tế.
3. Sơ đồ tầng/khu, mã ô và kích thước thực tế thay dữ liệu khởi tạo.
4. TLS/mTLS và chữ ký kết quả AI cho triển khai thật.
5. Mã hóa sinh trắc học bằng KMS/ổ đĩa mã hóa của môi trường triển khai.
6. Nghiệm thu hiện trường: đêm/ngược sáng/mưa/biển bẩn hoặc che/mất mạng/mất điện/bám đuôi/barrier thủ công.
7. Lịch backup/monitoring thật và diễn tập phục hồi định kỳ.
8. Regression WinForms sau cập nhật .NET SDK Windows; target .NET 8 không thay bằng chứng kiểm thử.

### Danh sách mười nhóm của snapshot tài liệu trước

Danh sách dưới bảo toàn mười nhóm rủi ro triển khai đã ghi nhận, không independent runtime audit và không phủ nhận controls đã có trong source/H2 ở các bảng trên:

1. **Barrier/sensor:** hiện simulation; relay/PLC SDK, loop sensor và device feedback chưa verified.
2. **Face/liveness:** YuNet/SFace và thresholds không chống spoofing được chứng minh; live-site calibration/REVIEW policy còn cần.
3. **Camera:** default camera0, source DirectShow; entry/exit cameras, RTSP/resolution/ROI/config UI chưa verified.
4. **Media privacy:** role/scope, retention/cleanup và access audit đã có source/H2 evidence; encryption, deployed storage, consent/privacy acceptance và backup/restore chưa được nghiệm thu.
5. **Gate API:** Desktop JWT, role checks và server-bound recognition/face evidence có isolated-test evidence; live service/TLS/key provisioning/deployment vẫn chưa proven.
6. **Slots/site layout:** initial diagram không real-site occupancy/capacity/placement policy; existing create/dispatch UI không complete site acceptance.
7. **Physical/financial devices:** RFID, printer, POS/transfer, invoice và shift reconciliation chưa integrated evidence.
8. **Roles/accounts/audit:** DB-backed accounts, password/role/disable/session lifecycle, throttling và audit đã có source/H2 evidence; full deployed staff/admin lifecycle, operational audit và abuse resistance chưa nghiệm thu.
9. **Adverse scenarios:** night/backlight/rain/dirty plate/outage/power/tailgating/manual barrier tests chưa executed evidence.
10. **Windows SDK updates:** .NET8 target không substitute regression after SDK/hardware changes.

## Các quyết định và mức sẵn sàng

[Sổ quyết định](decisions/README.md) giữ P01–P09; không chọn A/B bằng “final”, không publish survey khi provenance/privacy chưa resolved. [Tổng hợp tư liệu](thesis/material-summary.md) ghi coverage/giới hạn chuyển nội dung và việc giữ bản sao gốc ngoài repository theo yêu cầu chủ dự án; không coi xóa thư mục là bằng chứng mọi biến thể tương đương.

Những behavior đã quan sát chỉ chứng minh subset, không full NV fulfillment. Transaction annotations/OPEN precheck/model lock không financial/session idempotency; warning OPEN, optional guest capture with conditional exit verification, non-mandatory resident exit face proof, and payment gates remain policy/verification gaps. Không có end-to-end deployment/production datasource/security/AI accuracy/physical-device readiness result trong bộ tài liệu này.

Trạng thái review tài liệu độc lập với trạng thái phần mềm. Xem [testing strategy](testing/strategy.md) để biết test IDs, assertions và planned scenarios; không suy passing tests từ tên method hoặc page200. Đợt tổng hợp docs không chạy lại suite, startup, camera hay DB thật.
