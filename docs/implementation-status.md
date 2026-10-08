# Trạng thái implementation và giới hạn bằng chứng

[Mục lục](README.md) · [Tổng quan](overview.md) · [Traceability](requirements/traceability.md) · [Kiểm thử](testing/strategy.md)

## Cách đọc trạng thái

Đây là tổng hợp evidence đã thu thập, không full repository audit hay application readiness certificate. Acceptance tài liệu không xác nhận code đã đáp ứng nghiệp vụ; không tests/apps/generators được chạy trong các slice authoring này. Existing tests có assertions, không run result. Mức inspection trong tài liệu cũ là snapshot; [testing](testing/strategy.md) ghi test IDs/assertions đã đọc gần hơn, còn [architecture](architecture/system.md) và [operations](operations/setup-and-troubleshooting.md) ghi actual boundaries.

| Nhãn | Ý nghĩa |
| --- | --- |
| **Documented** | Có yêu cầu nguồn/đặc tả; authority hoặc approval rule vẫn có thể unresolved |
| **Code observed** | Symbol/body/call path được đọc tĩnh; không runtime result |
| **Partially implemented** | Có code cho một subset cụ thể, không completion toàn NV |
| **Not verified** | Chưa đủ assertions/runtime/source authority; không tự kết luận không có implementation |
| **Known discrepancy** | Nguồn/claim khác actual observation hoặc variants xung đột; cần quyết định/kiểm chứng |

## NV01–NV08

Các location viết tắt bên dưới: [ParkingService](../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) (S), [ParkingApiController](../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) (P), [AdminController](../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) (A), [MainForm](../desktop-winform/MainForm.cs) (D), [Spring integration tests](../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) (T). Các test methods được liệt kê đầy đủ tại strategy; không kết quả thực thi.

| Documented / owner | Code observed / subset partially implemented | Actual verification level | Gaps / known discrepancy |
| --- | --- | --- | --- |
| [NV01](requirements/nv01-registration.md): household/person/vehicle, consent, ba ảnh, approval, quyền theo xe | P:58–101 lookup; S:397–417 entry member rights; Web registration/QR/face upload và authorized-member links | Static call paths/templates; T registration/QR/shared members assertions hiện diện | Full approval/consent/dedup/privacy chưa verified; same household không mọi xe; legacy/manual bypass và client face flag; QR không CCCD-face; gate không integrated three-image proof |
| [NV02](requirements/nv02-cards-passes-pricing.md): card/pass/30-day/payment/debt/giá/history | A:635–675 monthly/payment record; S:426–467 fee/pass computation; cards/pricing forms | Static bodies; T monthly zero preview/default prices và fee subset | Record không paid/settled proof; renewal/replacement/refund/debt/price snapshot chưa verified; entry-date eligibility vs source validity-at-exit; payment-only activation vs debt approval unresolved |
| [NV03](requirements/nv03-resident-entry.md): rights/evidence/one OPEN/barrier | S:40–94 entry/precheck/save/slot; D:211–230 warning/manual barrier conditions | Static bodies; T sequential normalized-plate/OPEN happy path | **Warning thiếu/sai card/hết pass vẫn có thể tạo OPEN**; UI barrier không mở không là rollback. Quality/liveness/type mismatch, audit/race/idempotency chưa verified; source Bước9 variants khác |
| [NV04](requirements/nv04-resident-exit.md): OPEN/evidence/authorized collector/fee/closure | S:97–126 preview/confirm,383–395 lookup; D:272–335 preview guard và optional face action | Static bodies; T preview/COMPLETED happy path; monthly test chỉ preview | **Resident confirm chưa bắt buộc verifyDriver/face** trong inspected path; exit member persistence chưa proven; payment/waiver/replay/races chưa verified. **Source CLOSED vs code COMPLETED** |
| [NV05](requirements/nv05-visitor-parking.md): khách evidence/ownership/payment-before-close | Optional capture D:337–338; guest lookup P:69–74; S:420–423 conditional guest-face helper | Static body; T one-pixel image + false/true client flag branch | **Chỉ guest có entry-face path mới bắt face**, trừ override; capture optional và flag không biometric authenticity. Payment/lost-card/ownership/receipt/audit chưa verified |
| [NV06](requirements/nv06-vehicle-classification.md): verified taxonomy/correction; A/B variants | S:482–491 registry type/guest fallback; [recognizer](../anpr-service/app/recognizer.py):58–139 YOLO/OCR/heuristics | Static processing; ANPR unit assertions về normalize/type/format/token ordering | Không six-class/EV accuracy hoặc correction reason-history proof. **A classification-only vs B EV charging unresolved**; slots không ChargingSession/connector/rights proof |
| [NV07](requirements/nv07-exceptions-recovery.md): quality/manual/outage/recovery/privacy | D errors/manual/barrier; ANPR error branches; [ApiResponseReader](../desktop-winform/ApiResponseReader.cs):8–74 messages | Static source, không fault-injection/recovery run | Manual flag không reason/actor/audit enforcement; native restart không recovery guarantee; offline queue/resync, DB-file compensation, idempotency/locking và privacy chưa verified |
| [NV08](requirements/nv08-reporting.md): filters/totals/reconciliation/permissions | A:725–858 reports; dashboard filter/chart/detail, sessions history; COMPLETED fees + subscription amounts | Static query/UI observations; T admin page200, không known-total test | Revenue không visit settlement proof; Payment–Session–PriceRule lineage, cutoff/refund/waiver/export/role/charging dashboard còn unresolved |

## Supporting components: không thêm NV chính thức

| Area / evidence owner | Observed và verification | Gaps |
| --- | --- | --- |
| [Slots/lookup/health](requirements/traceability.md) | P:104–204/S:129–380 assignment, borrow/cancel, status/release/dispatch, unassigned/batch; D map và slot-dialog actions. Static; no runtime allocation result | Race uniqueness, dispatch preconditions, borrow-expiry refresh/physical occupancy, real site layout và recovery chưa verified; health metadata/readiness không integration PASS |
| [Admin/QR/media](operations/user-guide.md) | Observed navigation/forms; registration editing/upload/browser camera/QR UI; Spring filesystem/media mappings và relevant T assertions | Full validation/quota/approval/browser/device/accessibility chưa exercised; file/DB rollback, retention/encryption/privacy/audit chưa verified |
| [ANPR](architecture/system.md) / [contracts](api/contracts.md) | Desktop direct recognition/verify-camera/capture calls; model/cache/quality and explicit errors; unit tests present | No actual camera/model accuracy or liveness PASS. `/face/verify-camera` URL-prefix rejection400 đã ghi riêng; correction còn pending final review, không runtime contract pass |
| [Standalone face demo](architecture/system.md) | Three-image API riêng, models fallback và decision unit tests present | Không gate caller/integrated enrollment; demo livenessChecked=false; launcher interpreter path lệch repo ANPR venv |
| [Persistence/startup](operations/setup-and-troubleshooting.md) | JPA/entities/repositories và source launcher behavior; MySQL runtime driver/H2 test scope; no main-resource application* found in focused listing | Actual datasource/profile/DDL/credentials mapping chưa verified; README H2/MySQL auto-creation claims, Laragon probe/cloud-label mismatch không deployment guarantee |
| [Desktop](operations/user-guide.md) | WinForms .NET8, API URLs, image/video, entry/exit/preview/lookup/face/manual/map; label/timer barrier | No UI runtime/global SDK update/hardware verification; error guidance không transaction recovery; no automatic safe replay claim |
| [Security](security/overview.md) | [SecurityConfig](../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java):11–33 form login/in-memory ADMIN; API/uploads/H2 permitAll, API/H2 CSRF exclusions, same-origin frame option | Full RBAC/account lifecycle, signed AI evidence, TLS/retention/access auditing/deployed behavior chưa verified. Public sensitive APIs/media không production-hardening PASS |
| [Academic/testing](thesis/requirements.md) | Dated regulation extract, proposal commitments và tests-present/needed được tách | Track/applicability/DOCX/QA/survey source authority chưa chốt; tests không executed results; no thesis acceptance or algorithmic contribution certificate |

## Mười nhóm deployment gaps được giữ nguyên

[PROJECT_GAPS.md](../evidence/archive/PROJECT_GAPS.md) là author statements, không independent runtime audit. Ánh xạ đầy đủ để không mất nội dung:

1. **Barrier/sensor:** hiện simulation; relay/PLC SDK, loop sensor và device feedback chưa verified.
2. **Face/liveness:** YuNet/SFace và thresholds không chống spoofing được chứng minh; live-site calibration/REVIEW policy còn cần.
3. **Camera:** default camera0, source DirectShow; entry/exit cameras, RTSP/resolution/ROI/config UI chưa verified.
4. **Media privacy:** disk storage không encryption/role/retention/backup/access logs được nghiệm thu.
5. **Gate API:** public demo access/client face flags; API authentication, signed AI evidence và TLS chưa proven.
6. **Slots/site layout:** initial diagram không real-site occupancy/capacity/placement policy; existing create/dispatch UI không complete site acceptance.
7. **Physical/financial devices:** RFID, printer, POS/transfer, invoice và shift reconciliation chưa integrated evidence.
8. **Roles/accounts/audit:** login không complete staff/admin role lifecycle, DB-backed accounts/lockout/audit.
9. **Adverse scenarios:** night/backlight/rain/dirty plate/outage/power/tailgating/manual barrier tests chưa executed evidence.
10. **Windows SDK updates:** .NET8 target không substitute regression after SDK/hardware changes.

## Các quyết định và mức sẵn sàng

R01 source-authority/QA/current DOCX và academic/survey lineage vẫn PARTIAL. Không chọn A/B bằng “final”, không publish survey khi provenance/privacy chưa resolved. Full distinct-variant/diagram lineage closure chưa có bằng chứng; originals phải được giữ.

Những behavior đã quan sát chỉ chứng minh subset, không full NV fulfillment. Transaction annotations/OPEN precheck/model lock không financial/session idempotency; warning OPEN, conditional visitor face, resident-exit rights và payment gates phải được quyết định/kiểm thử rõ. Không có end-to-end deployment/production datasource/security/AI accuracy/physical-device readiness result trong bộ tài liệu này.

R06 correction HTTP400 còn chờ final review; R07/R08 cùng tham gia consolidated documentation review. Aggregate R09 integrity check báo 21 protected-file fingerprints khớp; đây là bằng chứng integrity tài liệu, không phải runtime/application readiness. R08-specific standalone comparison không được thực hiện. Trạng thái review tài liệu độc lập với trạng thái phần mềm. Xem [testing strategy](testing/strategy.md) để biết actual test IDs, mức assertions và planned scenarios; không suy passing tests từ tên method hoặc page200.
