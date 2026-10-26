# Traceability nghiệp vụ, implementation và kiểm thử

[Phạm vi](README.md) · [Quyết định](../decisions/README.md) · [Trạng thái](../implementation-status.md) · [Testing](../testing/strategy.md)

Ma trận nối yêu cầu tới dữ liệu/API/UI/source/tests. Nó không đánh dấu toàn NV hoàn thành. Quyền ưu tiên: yêu cầu ở trang NV; policy đã duyệt ở decisions; source mô tả cách làm hiện tại; kết quả chạy và môi trường chỉ ở testing. Không dùng test name/page200 hoặc annotation làm bằng chứng acceptance/E2E.

## Các nguồn implementation

## Ma trận hiện hành — main `abd6183`, inspection tĩnh

| NV | Source/data hiện hành và trang sở hữu | Mức kiểm chứng / phần còn mở |
| --- | --- | --- |
| NV01 | Household/FamilyMember/Vehicle; AdminController/RegisteredFaceService; [auth](../api/authentication.md), [dictionary](../architecture/data-dictionary.md) | Không full consent/approval/ba ảnh. Entry có legacy null-driver khi chưa có face profiles; exitMember chưa được điền |
| NV02 | ParkingCard/PricingRule/SubscriptionPayment/PaymentTransaction; AdminController/PaymentTransactionService | Plate-as-card, receipt/method/reference/shift đã có source; chưa external settlement/debt/refund/rule-version guarantee |
| NV03 | AiProxyController → GateEvidenceService → ParkingApiController/ParkingService/ParkingIdempotencyService | Required evidence mặc định, member/OPEN/slot checks cùng legacy branches; warning OPEN vẫn có; không production concurrency/physical barrier PASS |
| NV04 | Preview/confirm + GateEvidenceConsumptionService + PaymentTransactionService | Confirm consume/receipt/COMPLETED/clear slot; resident face optional. OperationId replay khác guarantee exactly-once |
| NV05 | GuestRegisteredFaceService, face capture/guest verify routes, ParkingService guest exit | Capture optional, guest có ảnh vào kiểm face; face-to-OPEN binding còn gap; payment receipt không proof ownership/liveness/settlement |
| NV06 | Recognizer/VehicleType + ManualOverrideService/ManualOverrideEvent | Có original/corrected/reason/actor audit; không six-class accuracy hoặc charging acceptance; FAIL/REJECT và blank-plate reason gaps |
| NV07 | OfflineOutboxStore/ParkingApiClient, ParkingOperation services, simulator barrier | Queue/replay source có thật; TTL/account/origin/partial-file recovery và vận hành offline chưa proven |
| NV08 | AdminController dashboard + PaymentController reconciliation/receipt + shift summaries | Có ledger/collector/shift/status; aggregate bases khác nhau, known-total/date-boundary/external settlement chưa chạy |

API method/path/payload/quyền: [inventory](../api/endpoints.md), [contracts](../api/contracts.md), [security](../security/overview.md). Current test assertions/source được sở hữu tại [testing](../testing/strategy.md); không dùng tên test Security + WIP trong ma trận lịch sử dưới đây làm main coverage. Đợt đồng bộ này không chạy application tests/build/DB/camera.

Source AI hiện hành: [AiProxyController](../../spring-web/src/main/java/vn/edu/parking/web/AiProxyController.java), [AiProxyService](../../spring-web/src/main/java/vn/edu/parking/service/AiProxyService.java), [GateEvidenceAdminController](../../spring-web/src/main/java/vn/edu/parking/web/GateEvidenceAdminController.java). Config properties/SQL thủ công, không YAML/Flyway. Internal header `X-Parking-Service-Token`; health có identity fields nhưng không chứng minh readiness.

## Ma trận lịch sử Security + WIP — không phải source mapping main

Toàn bộ nguồn P/S/A/D/T, quan sát tests và supporting features bên dưới giữ snapshot cũ để bảo toàn provenance; có path trùng main nhưng body/contract đã đổi. Các claims ba override, member bắt buộc, missing payment/offline, YAML và health mismatch chỉ thuộc snapshot đó. Dùng ma trận hiện hành và technical owners phía trên cho main.

- **S:** [ParkingService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java) — session/fee/rights/slots.
- **P (lịch sử):** ParkingApiController và GateEvidenceController của nhánh Security + WIP — không còn mapping processing/evidence main; dùng AiProxyController/GateEvidenceAdminController ở ma trận hiện hành.
- **A:** [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) — Web forms/dashboard/revenue.
- **D:** [MainForm](../../desktop-winform/MainForm.cs), [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) — Desktop UI/state/authenticated requests.
- **T:** [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java) — H2/mock/synthetic integration subsets. Exact executions nằm trong testing, không suy đã chạy từ việc có source.
- Schema: [data dictionary](../architecture/data-dictionary.md)/[ERD](../architecture/erd.md). Routes/auth/shapes: [contracts](../api/contracts.md)/[inventory](../api/endpoints.md).

## NV01–NV08

| Yêu cầu / quyết định liên quan | Data/API/UI/source hiện tại | Tests có liên quan | Gap còn lại |
| --- | --- | --- | --- |
| [NV01](nv01-registration.md), P02/P04/P07 | Household/FamilyMember/Vehicle và bảng quyền; A registrations/members/vehicles, QR/media; P lookup/verifyFace; Web hồ sơ | T combinedRegistrationCreatesHouseholdMemberVehicleThenOpensPackageStep, familyMembersCanBeAuthorizedForTheSameVehicle, residentFaceVerificationUsesServerRegistrationImageAndBindsEntryAndExitEvidence | Full consent/approval/dedup/concurrent activation, ba ảnh và liveness chưa proven |
| NV01 quyền theo xe | S verifyDriver bắt selected active authorized member; P face proof phải khớp member/plate | residentEntryWithoutSelectedMemberFailsWithoutConsumingEvidenceOrCreatingSession; residentEntryRequiresActiveAuthorizedMemberAndOverrideForMissingReferenceImage | Cùng hộ không đủ quyền; exit người lấy chưa bắt buộc/persist trong mọi lượt |
| [NV02](nv02-cards-passes-pricing.md), P02/P03/P05 | ParkingCard/PricingRule/SubscriptionPayment; A saveCard; S calculateFee/hasValidMonthlyPass; Web cards/pricing | monthlyPassKeepsOwnerAndMakesVisitFeeZero, surveyPricesAreTheDefaults và completesEntryPreviewAndExitFlow | Renewal continuity/overlap, debt/settlement/refund/card replacement/history và rule snapshot chưa đủ |
| [NV03](nv03-resident-entry.md), P03/P04/P06/P09 | D recognition/lookup/face → P entry → S enter/verifyDriver; Spring-owned evidence bind/consume OPEN, slot assignment | T image/video proxy, resident proof/member allow-deny và ba reasoned override cases | Missing/mismatched/expired warning vẫn có thể tạo OPEN; quality/type-mismatch/liveness, unique concurrent OPEN và physical barrier chưa proven |
| [NV04](nv04-resident-exit.md), P03/P04/P05/P09 | P openSessionId/preview/confirm kiểm EXIT evidence; S previewExit/confirmExit tính lại fee, COMPLETED và clear slot; D preview guard | completesEntryPreviewAndExitFlow; resident proof binding; backendReviewExitRequiresReasonedOverrideAndAuditsTheOpenSession | Mandatory resident face/authorized collector/exitMember, payment-before-close và concurrent replay còn gap; CLOSED khác COMPLETED |
| [NV05](nv05-visitor-parking.md), P04/P05/P06 | Guest vehicle null; D CaptureGuestAsync/VerifyFaceAsync → Spring → FastAPI; optional capture bind; conditional guest exit proof/validated exceptions | guestFaceCaptureAndExitVerificationStayBehindSpringAndBindToOneParkingSession; clientFaceAndOverrideAssertionsCannotBypassGuestExitVerification | Mandatory capture, real ownership/liveness, lost-card, payment/receipt/waiver còn gap; flags không tạo proof |
| [NV06](nv06-vehicle-classification.md), P01 | VehicleType registry/guest fallback ở S; YOLO/OCR/heuristics ở recognizer; giá theo official registry | recognizer normalize/type/format/two-line-token helpers; default-price subset | Không six-class/EV benchmark/correction reason-history proof; ChargingSession/right/connector/hardware không suy từ slot |
| [NV07](nv07-exceptions-recovery.md), P06/P07/P09 | D error/manual controls và barrier mô phỏng; Spring validates override/permission/reason/audit; evidence retention; ApiResponseReader không queue | gateStaffOverrideIsAllowedOnlyForAuthorizedMemberWithoutRegistrationImageAndIsAudited; recognitionOutageOverrideRequiresBackendFaceEvidenceAndIsAudited; onlyBackendReviewCanBeOverriddenAndBackendRejectCannot; retention/access subsets | Không operational outage/recovery/offline/event idempotency/DB-file compensation hoặc audit mọi barrier click |
| [NV08](nv08-reporting.md), P01/P05/P07 | A dashboard/revenue/sessions; COMPLETED fees theo exitTime + subscription amount theo paidAt; dashboard requires `DASHBOARD_VIEW` + `REVENUE_VIEW`, revenue API uses Web session + `REVENUE_VIEW` | rendersAllAdminPages; WebRevenueSecurityIntegrationTest permission matrix | Không known-total/reconciliation/price-lineage/time-boundary/refund/waiver/export/charging dashboard proof |

## Supporting features

| Nhóm | Owner/source/test | Giới hạn |
| --- | --- | --- |
| Account/password/role/session/audit | [Auth](../api/authentication.md); AccountController/AccountAdministrationController/DesktopAuthController; account/password/refresh/JWT tests | H2 locks không MySQL; key provisioning/TLS/distributed abuse limits chưa verified |
| Slots/borrow/cancel/release/dispatch/batch | S slot methods; P slot routes; ParkingMapForm/SlotDetailDialog và Web slots; selected DTO/PII tests | Không physical occupancy/race uniqueness/dispatch lifecycle/site-layout acceptance |
| Lookup/open/unassigned | P allowlisted DTOs, D lookup; unassignedParkingListsOmitResidentNameAndApartment | Lookup/card status không cấp quyền; broader response minimization vẫn cần |
| Image/video/face/private media | P → AnprServiceClient → FastAPI; GateEvidenceService/storage/policy; H2/fake proxy/storage/access/retention tests | Không real models/camera/shared-token/TLS/storage deployment/backup/liveness proof |
| CCCD QR và resident media | CccdQrController/CccdQrService/ResidentImageStorage/WebConfig; QR/storage/security tests | QR parsed fields không CCCD-photo identity match; `PRIVATE_EVIDENCE_VIEW` media access is permission-wide, not operation-scoped like Gate Evidence |
| Health/startup/DB | [Operations](../operations/setup-and-troubleshooting.md), tracked YAML/scripts; status-only health | Health không DB/JWT/model readiness; launcher expectations còn lệch contract |
| Demo ba ảnh | face-verification-demo/app.py và tests/test_decision.py | API độc lập; không gate enrollment hoặc liveness integration |

## Cách cập nhật ma trận

Khi thay đổi hành vi: cập nhật NV/quyết định nếu được duyệt → contracts/data/UI liên quan → test cases/results → status/ma trận. Giữ requirement chưa đáp ứng ở cột gap thay vì xóa. Xem [thesis requirements](../thesis/requirements.md) cho các sản phẩm học thuật chưa được chứng nhận.
