# Trạng thái implementation và giới hạn bằng chứng

[Mục lục](README.md) · [Tổng quan](overview.md) · [Traceability](requirements/traceability.md) · [Kiểm thử](testing/strategy.md)

## Cách đọc trạng thái

Đây là tổng hợp evidence đã thu thập, không full repository audit hay application readiness certificate. Acceptance tài liệu không xác nhận code đã đáp ứng nghiệp vụ. Verification ngày 2026-10-09 ghi nhận Spring 108/108, Desktop 12/12 và Release build sạch theo Build verification output; ANPR 10/10 là lượt chạy trước. Full Spring suite được chạy lại sau remediation ngày 2026-10-10: 108/108 PASS, trong đó Surefire ghi `ParkingFlowIntegrationTest` 54/54, Web revenue 3/3, `CccdQrControllerTest` 1/1 và `AnprServiceClientTest` 8/8. Desktop và ANPR không chạy lại trong remediation vì source/dependencies không đổi. Các kết quả H2/fake không xác nhận MySQL thật, TLS, Spring–FastAPI/camera thực hoặc backup/restore; các mục này vẫn PENDING. Mức inspection trong tài liệu cũ là snapshot; [testing](testing/strategy.md) ghi test IDs/assertions và executions, còn [architecture](architecture/system.md) và [operations](operations/setup-and-troubleshooting.md) ghi actual boundaries.

| Nhãn | Ý nghĩa |
| --- | --- |
| **Documented** | Có yêu cầu nguồn/đặc tả; authority hoặc approval rule vẫn có thể unresolved |
| **Code observed** | Symbol/body/call path được đọc tĩnh; không runtime result |
| **Partially implemented** | Có code cho một subset cụ thể, không completion toàn NV |
| **Not verified** | Chưa đủ assertions/runtime/source authority; không tự kết luận không có implementation |
| **Known discrepancy** | Nguồn/claim khác actual observation hoặc variants xung đột; cần quyết định/kiểm chứng |

## NV01–NV08

Các location viết tắt bên dưới: [ParkingService](../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) (S), [ParkingApiController](../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) (P), [AdminController](../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) (A), [MainForm](../desktop-winform/MainForm.cs) (D), [Spring integration tests](../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) (T). Test IDs/assertions/executions được phân biệt tại strategy; kết quả H2 không phải runtime/deployment evidence.

| Documented / owner | Code observed / subset partially implemented | Actual verification level | Gaps / known discrepancy |
| --- | --- | --- | --- |
| [NV01](requirements/nv01-registration.md): household/person/vehicle, consent, ba ảnh, approval, quyền theo xe | P:lookup, entry face evidence; S:active authorized-member checks; Web registration/QR/face upload | Static call paths/templates; Spring full suite 108/108 PASS on 2026-10-10, including resident face evidence, registration/QR parsing, content-free QR scan audit, generic internal scan errors and resident-entry member rejection | Full approval/consent/dedup/privacy chưa verified; same household không mọi xe; three approved gate override exceptions do not replace registration/identity controls; QR route still returns parsed CCCD data; no three-way CCCD/registration/realtime comparison |
| [NV02](requirements/nv02-cards-passes-pricing.md): card/pass/30-day/payment/debt/giá/history | A:635–675 monthly/payment record; S:426–467 fee/pass computation; cards/pricing forms | Static bodies; T monthly zero preview/default prices và fee subset | Record không paid/settled proof; renewal/replacement/refund/debt/price snapshot chưa verified; entry-date eligibility vs source validity-at-exit; payment-only activation vs debt approval unresolved |
| [NV03](requirements/nv03-resident-entry.md): rights/evidence/one OPEN/barrier | S:entry/precheck/member validation; P:authenticated image/video OCR and server-bound recognition/face evidence; D gate flow | Static bodies; Spring full suite 108/108 PASS on 2026-10-10, including resident/guest evidence binding, all three approved reasoned override classes and missing/inactive/unrelated member rejection | **Warning thiếu/sai card/hết pass vẫn có thể tạo OPEN**; UI barrier không mở không là rollback. Quality/liveness/type mismatch, MySQL race/idempotency chưa verified; source Bước9 variants khác |
| [NV04](requirements/nv04-resident-exit.md): OPEN/evidence/authorized collector/fee/closure | S:preview/confirm and OPEN lookup; P:server-bound exit recognition and optional face proof; D preview/confirm | Static bodies; Spring H2 test covers resident face proof binding through confirm | **Resident confirm không bắt buộc face verification**; alternate authorized collector policy not established; payment/waiver/replay/MySQL races chưa verified. **Source CLOSED vs code COMPLETED** |
| [NV05](requirements/nv05-visitor-parking.md): khách evidence/ownership/payment-before-close | Optional Spring camera capture at entry; private guest evidence; same-session exit face verification when entry capture exists | Static body; Spring H2 test covers capture→entry bind→exit verification/consume; client-supplied flags rejected | Capture remains optional; guest sessions without stored entry-face image do not trigger this exit check. Payment/lost-card/ownership/receipt/audit and liveness efficacy remain unverified |
| [NV06](requirements/nv06-vehicle-classification.md): verified taxonomy/correction; A/B variants | S:482–491 registry type/guest fallback; [recognizer](../anpr-service/app/recognizer.py):58–139 YOLO/OCR/heuristics | Static processing; ANPR unit assertions về normalize/type/format/token ordering | Không six-class/EV accuracy hoặc correction reason-history proof. **A classification-only vs B EV charging unresolved**; slots không ChargingSession/connector/rights proof |
| [NV07](requirements/nv07-exceptions-recovery.md): quality/manual/outage/recovery/privacy | D errors/manual/barrier; ANPR error branches; [ApiResponseReader](../desktop-winform/ApiResponseReader.cs):8–74 messages | Static source; missing-image, backend-linked `AI_REVIEW`, and backend-recorded service-outage overrides are covered by reason/evidence/audit integration tests; no operational outage/recovery test | Unlisted manual cases remain fail-closed; native restart không recovery guarantee; offline queue/resync, DB-file compensation, idempotency/locking và privacy chưa verified |
| [NV08](requirements/nv08-reporting.md): filters/totals/reconciliation/permissions | A:725–858 reports; dashboard filter/chart/detail, sessions history; COMPLETED fees + subscription amounts; revenue endpoints use Web session + MANAGEMENT-only chain | Static query/UI observations; actual Web-session integration verifies Management 200/Gate 403/anonymous 401, không known-total test | Revenue không visit settlement proof; Payment–Session–PriceRule lineage, cutoff/refund/waiver/export/charging dashboard còn unresolved |

## Supporting components: không thêm NV chính thức

| Area / evidence owner | Observed và verification | Gaps |
| --- | --- | --- |
| [Slots/lookup/health](requirements/traceability.md) | P:104–204/S:129–380 assignment, borrow/cancel, status/release/dispatch, unassigned/batch; D map và slot-dialog actions. Static; no runtime allocation result | Race uniqueness, dispatch preconditions, borrow-expiry refresh/physical occupancy, real site layout và recovery chưa verified; health metadata/readiness không integration PASS |
| [Admin/QR/media](operations/user-guide.md) | Observed navigation/forms; resident media defaults `var/private-media`; `/uploads/**` MANAGEMENT-only. Gate evidence uses separate private storage, session/operation/kind/ID-bound reads, owner/device-session + OPEN-session scope for gate staff, audited access, 30-day retention/cleanup, and finite Management holds; H2 verifies selected access/binding flows | Deployment storage root, encryption, backup/restore, broad privacy review and browser/device acceptance remain unverified; resident CCCD/profile media remains a separate Management-only permission |
| [ANPR](architecture/system.md) / [contracts](api/contracts.md) | Desktop image/video/face processing calls go through JWT-protected Spring routes; FastAPI processing routes require external internal Bearer. Spring stores image/video/face evidence and binds recognition to entry/exit; caller-supplied face URL fetch was removed | Spring ANPR client tests passed 8/8 against a controlled fake HTTP service; FastAPI suite passed 10/10 using a fake recognizer; H2 exercises resident and guest face flow. Live service, camera/model accuracy/liveness, production credentials and deployment remain unverified |
| [Standalone face demo](architecture/system.md) | Three-image API riêng, models fallback và decision unit tests present | Không gate caller/integrated enrollment; demo livenessChecked=false; launcher interpreter path lệch repo ANPR venv |
| [Persistence/startup](operations/setup-and-troubleshooting.md) | JPA/entities/repositories, additive Flyway V1–V5 and source configuration; MySQL runtime driver/H2 test scope; V1–V5 rehearsed only on isolated H2 MySQL mode | Actual datasource/profile/DDL/credentials mapping chưa verified; README H2/MySQL auto-creation claims, Laragon probe/cloud-label mismatch không deployment guarantee |
| [Desktop](operations/user-guide.md) | WinForms .NET8, API URLs, image/video, entry/exit/preview/lookup/face/manual/map; label/timer barrier | No UI runtime/global SDK update/hardware verification; error guidance không transaction recovery; no automatic safe replay claim |
| [Security](security/overview.md) | [SecurityConfig](../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) separates Web session and JWT Desktop chains; DB-backed accounts/RBAC/session invalidation, operation-scoped gate evidence, retention/audit, Spring AI proxies, revenue session RBAC and three approved override classes have isolated test evidence | S6/S8 remain partial for MySQL concurrency, TLS, key provisioning, backup/restore and deployed behavior. No end-to-end production-hardening PASS |
| [Academic/testing](thesis/requirements.md) | Dated regulation extract, proposal commitments and tests-present/executed/needed are distinguished; 2026-10-09 local suites are recorded in testing strategy | Track/applicability/DOCX/QA/survey source authority chưa chốt; no thesis acceptance or algorithmic contribution certificate |

## Mười nhóm deployment gaps được giữ nguyên

[PROJECT_GAPS.md](../evidence/archive/PROJECT_GAPS.md) là author statements, không independent runtime audit. Ánh xạ đầy đủ để không mất nội dung:

1. **Barrier/sensor:** hiện simulation; relay/PLC SDK, loop sensor và device feedback chưa verified.
2. **Face/liveness:** YuNet/SFace và thresholds không chống spoofing được chứng minh; live-site calibration/REVIEW policy còn cần.
3. **Camera:** default camera0, source DirectShow; entry/exit cameras, RTSP/resolution/ROI/config UI chưa verified.
4. **Media privacy:** disk storage không encryption/role/retention/backup/access logs được nghiệm thu.
5. **Gate API:** Desktop JWT and explicit role checks have test evidence; signed/server-bound AI evidence and TLS still not proven.
6. **Slots/site layout:** initial diagram không real-site occupancy/capacity/placement policy; existing create/dispatch UI không complete site acceptance.
7. **Physical/financial devices:** RFID, printer, POS/transfer, invoice và shift reconciliation chưa integrated evidence.
8. **Roles/accounts/audit:** login không complete staff/admin role lifecycle, DB-backed accounts/lockout/audit.
9. **Adverse scenarios:** night/backlight/rain/dirty plate/outage/power/tailgating/manual barrier tests chưa executed evidence.
10. **Windows SDK updates:** .NET8 target không substitute regression after SDK/hardware changes.

## Các quyết định và mức sẵn sàng

R01 source-authority/QA/current DOCX và academic/survey lineage vẫn PARTIAL. Không chọn A/B bằng “final”, không publish survey khi provenance/privacy chưa resolved. Full distinct-variant/diagram lineage closure chưa có bằng chứng; originals phải được giữ.

Những behavior đã quan sát chỉ chứng minh subset, không full NV fulfillment. Transaction annotations/OPEN precheck/model lock không financial/session idempotency; warning OPEN, optional guest capture with conditional exit verification, non-mandatory resident exit face proof, and payment gates remain policy/verification gaps. Không có end-to-end deployment/production datasource/security/AI accuracy/physical-device readiness result trong bộ tài liệu này.

R06 correction HTTP400 còn chờ final review; R07/R08 cùng tham gia consolidated documentation review. Aggregate R09 integrity check báo 21 protected-file fingerprints khớp; đây là bằng chứng integrity tài liệu, không phải runtime/application readiness. R08-specific standalone comparison không được thực hiện. Trạng thái review tài liệu độc lập với trạng thái phần mềm. Xem [testing strategy](testing/strategy.md) để biết actual test IDs, mức assertions và planned scenarios; không suy passing tests từ tên method hoặc page200.
