# Hợp đồng API quan sát được

[Kiến trúc hệ thống](../architecture/system.md) · [Bảo mật và trust boundaries](../security/overview.md) · [Phạm vi NV01–NV08](../requirements/README.md) · [Traceability](../requirements/traceability.md)

[Danh mục đầy đủ method/path](endpoints.md) · [Auth, password, accounts và audit](authentication.md). Kết quả suite lịch sử được sở hữu tại [testing](../testing/strategy.md), không phải kiểm chứng API live trong đợt tài liệu.

## Phạm vi và mức bằng chứng

Inventory đối chiếu tĩnh với source hiện hành tại **main `abd6183f9307472f604905dd9378c02deb563e2b`**, ngày 2026-10-24: Spring controllers/DTOs/services, FastAPI handlers/schemas và Desktop client. Chỉ source dưới `spring-web/`, `anpr-service/`, `desktop-winform/` là authority implementation; bản sao `.artifacts/`, backups và nhánh feature không phải source main. Không chạy build/test/service/DB trong đợt đồng bộ này. Mapping không chứng minh runtime/E2E hoặc yêu cầu nghiệp vụ đã đạt.

**Bằng chứng lịch sử kế thừa, không kiểm chứng main:** Spring 128/128, report `20261024T020410Z-spring-04215e1a`, cùng Desktop 12/12 và ANPR 10/10 thuộc các lượt Security + WIP được ghi tại [testing](../testing/strategy.md). Không dùng chúng chứng nhận contracts, migration hoặc permissions của revision main trên.

| Dấu | Nghĩa |
| --- | --- |
| **Code** | Route/shape/side effect quan sát trong source |
| **Requirement** | Quy tắc được mô tả trong NV tương ứng; không tự chứng minh code đạt |
| **Test-present** | Assertion có trong test file; chưa xác nhận execution ở slice này |
| **Unverified** | Runtime/status/error serialization/deployment chưa được kiểm chứng |

## Ranh giới service và caller

| Service | Base URL phía client / route ownership | Caller đã xác minh |
| --- | --- | --- |
| Spring Web | [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `/api/parking/**`; AiProxyController `/api/ai/**`; [CccdQrController](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java) `/api/cccd/**`; [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) pages/forms/JSON. Desktop default `http://localhost:8080` | [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) gọi auth/gate/AI/lookup/health/slots/unassigned/slot operations. Browser quản trị dùng pages/forms/revenue/private evidence media; không claim Desktop có feature media routes |
| ANPR FastAPI | [main.py](../../anpr-service/app/main.py) routes tại service root; Spring default `http://127.0.0.1:8001` | Spring gọi processing bằng `X-Parking-Service-Token`; Desktop dùng Spring `/api/ai/**`, gồm AI health, không nhận service token. Camera chạy trên máy ANPR; cameraSource có thể là USB hoặc URL mạng |
| Face-verification demo | ASGI app độc lập tại [app.py](../../face-verification-demo/app.py) | Không tìm thấy gate/desktop caller tới demo `/api/compare`; không coi đây là tích hợp production |

Base URLs là defaults, không bind address/deployment proof. Desktop dùng JSON camelCase, multipart recognition `file`, face `realtime`; `ParkingApiClient` mặc định 10s, recognition camera 30s, upload recognition 5 phút, face camera/image 2 phút và face video 3 phút. [AiProxyConfiguration](../../spring-web/src/main/java/vn/edu/parking/config/AiProxyConfiguration.java) đặt connect timeout 5s/read timeout 5 phút, cấu hình bằng `PARKING_AI_CONNECT_TIMEOUT`/`PARKING_AI_READ_TIMEOUT`; không phải whole-request deadline 45s. Không thấy CORS allowlist trong security source; proxy/network/TLS/CORS thực tế chưa kiểm chứng.

## Spring — trạm gác, nghiệp vụ và sơ đồ bãi xe (NV03–NV07)

### Session entry/exit

`POST /api/parking/entry`, `/exit-preview`, `/exit-confirm` nhận JSON với `@Valid`; [EntryRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/EntryRequest.java) và [ExitRequest](../../spring-web/src/main/java/vn/edu/parking/web/dto/ExitRequest.java) chỉ đánh `plateNumber` là `@NotBlank`. Evidence và override được kiểm ở service, không có nested `override.reason` trong main. Fields chung: `operationId`, `plateNumber`, `cardCode`, `vehicleType`, `manualOverride`, `familyMemberId`, `faceVerified`, `faceSimilarity`, `vehicleEvidenceId`, `faceEvidenceId`, `manualOverrideReasonCode`, `manualOverrideReasonText`. Entry thêm `realtimeFaceImageBase64`; exit thêm `paymentMethod`, `paymentReference`.

| Route | Body (type; required theo DTO) | Normal body / side effect theo source |
| --- | --- | --- |
| `POST /api/parking/entry` | `EntryRequest`; `GATE_OPERATE` khi JWT bật | Tạo OPEN, lấy plate/type/member/face result từ evidence khi không legacy; `manualOverride:true` cho sửa plate/type sau kiểm quyền/ca/lý do. Consume evidence và ghi override cùng nghiệp vụ; guest có face evidence sẽ lưu reference ảnh vào session. Card được tra theo plate chuẩn hóa, không theo `cardCode` đầu vào |
| `POST /api/parking/exit-preview` | `ExitRequest`; `GATE_OPERATE` khi JWT bật | Trả `PREVIEW`, fee/exitTime dự kiến; không consume evidence, không đóng session hay thu tiền. Tìm OPEN theo plate rồi fallback card; không phát preview token bắt buộc cho confirm |
| `POST /api/parking/exit-confirm` | `ExitRequest`; nếu fee > 0 cần `paymentMethod`; `BANK_TRANSFER`/`POS` cần `paymentReference` | Revalidate/lock evidence, COMPLETED, ghi PaymentTransaction/receipt nếu fee > 0, consume evidence, giải phóng slot. `operationId` optional UUID hỗ trợ cached response; không đồng nghĩa payment provider đã xác nhận |

### Spring AI proxy và evidence

Nguồn [AiProxyController](../../spring-web/src/main/java/vn/edu/parking/web/AiProxyController.java), [AiProxyService](../../spring-web/src/main/java/vn/edu/parking/service/AiProxyService.java), [GateEvidenceService](../../spring-web/src/main/java/vn/edu/parking/service/GateEvidenceService.java). Tất cả `/api/ai/**` cần `GATE_OPERATE` khi JWT bật, kể cả health. Header optional `X-Request-ID` phải khớp `[A-Za-z0-9._:-]{1,128}`, sai/thiếu thì Spring sinh UUID và trả header tương ứng.

| Route | Input | Output / side effect |
| --- | --- | --- |
| `GET /api/ai/health` | Không body | Proxy FastAPI health, JSON 200 nếu upstream thành công |
| `POST /api/ai/recognize/image` | multipart `file`, `operation` ENTRY/EXIT | Proxy, tạo VEHICLE evidence/source IMAGE; trả recognition kèm Spring-issued `evidenceId` |
| `POST /api/ai/recognize/video` | multipart `file`, `operation` | VEHICLE evidence/source VIDEO |
| `POST /api/ai/recognize/camera` | JSON `{cameraIndex, cameraSource, frameCount, operation}` | VEHICLE evidence/source CAMERA; operation bắt buộc, cameraIndex ≥0, frameCount clamp4..16 |
| `POST /api/ai/face/verify-camera` | JSON `{familyMemberId, cameraIndex, cameraSource, operation}` | Server đọc ảnh member, proxy multipart registration; tạo FACE evidence gắn member |
| `POST /api/ai/face/verify-image`, `/api/ai/face/verify-video` | multipart `familyMemberId`, `operation`, `realtime` | Server đọc registration; FACE evidence/source IMAGE hoặc VIDEO |
| `POST /api/ai/face/guest/verify-camera` | JSON `{vehicleEvidenceId, cameraIndex, cameraSource}` | Từ EXIT vehicle evidence tìm OPEN guest/CONSUMED ENTRY face asset; proxy và tạo EXIT FACE evidence, không member |
| `POST /api/ai/face/guest/verify-image`, `/api/ai/face/guest/verify-video` | multipart `vehicleEvidenceId`, `realtime` | Cùng lookup guest; tạo EXIT FACE evidence |
| `POST /api/ai/face/capture-camera` | JSON `{cameraIndex, cameraSource, operation}` | Capture và tạo FACE evidence không member, decision upstream `CAPTURED` |
| `GET /admin-data/gate-evidences/{evidenceId}` | Web session; `PRIVATE_EVIDENCE_VIEW` hoặc `ROLE_MANAGEMENT` | Metadata và assets `{id,role,mediaType,sizeBytes,viewUrl}`, audit success |
| `GET /admin-data/gate-evidences/{evidenceId}/assets/{assetId}` | Cùng quyền; asset phải thuộc evidence, chưa deleted | Bytes có MIME và `Cache-Control:no-store`; kiểm SHA-256, audit success |

Evidence metadata fields: `id`, `type`, `operation`, `source`, `status`, `plateNumber`, `vehicleType`, `faceDecision`, `faceSimilarity`, `modelVersion`, `createdAt`, `expiresAt`, `consumedAt`, `assets`; không storageKey hoặc raw hash/path trong JSON admin này. Không có session/operation/owner reader checks ngoài matcher và asset association nêu trên.

Spring proxy kiểm MIME allowlist, image10MiB/video25MiB; không kiểm signature của mọi upload recognition. Servlet main giới hạn file10MB/request25MB nên video25MiB ở service không phải limit HTTP hiệu lực. `cameraSource` nhận numeric hoặc URI rtsp/http/https có host, ≤1024 ký tự; không có host/network allowlist. Không có current `/api/parking/anpr/**`, session/operation image route hoặc preservation API của nhánh feature.

Ví dụ request chỉ minh họa tên/kiểu fields được khai báo, không khuyến nghị tin flags từ client:

```json
{
  "plateNumber": "59A112345",
  "cardCode": "CARD001",
  "vehicleType": "MOTORBIKE",
  "manualOverride": false,
  "familyMemberId": null,
  "faceVerified": false,
  "faceSimilarity": null,
  "realtimeFaceImageBase64": null,
  "operationId": "<UUID for this immutable operation>",
  "vehicleEvidenceId": "<Spring-issued ENTRY vehicle evidence ID>",
  "faceEvidenceId": null,
  "manualOverrideReasonCode": null,
  "manualOverrideReasonText": null
}
```

Ví dụ là entry khách không capture mặt; placeholders không chạy độc lập. Với evidence mode, `faceVerified`, `faceSimilarity`, `realtimeFaceImageBase64` từ client không là proof; service lấy kết quả từ evidence, không có controller xóa/reject mọi flags như feature. Nếu cấu hình `parking.evidence.required=false` và cả hai IDs trống, legacy mode dùng client face flags/base64. Tracked properties bật evidence required mặc định; không suy cấu hình hiệu lực khi deploy.

**Evidence behavior:** [GateEvidenceConsumptionService](../../spring-web/src/main/java/vn/edu/parking/service/GateEvidenceConsumptionService.java) yêu cầu VEHICLE/đúng ENTRY hoặc EXIT, READY, chưa hết hạn, cùng account và Desktop `sid`; FACE optional, đúng loại/operation/owner/session và member nếu request chỉ định. Mutation lock evidence rồi đánh CONSUMED/gắn ParkingSession; preview không consume. TTL mặc định10 phút, cấu hình `parking.evidence.ttl-minutes` 1..60. Main không chứng minh FACE được link riêng tới VEHICLE/plate/open session trước consume; không gán cho nó object-scoping của feature. `GateEvidenceService` kiểm `contractVersion:"1.0"`, requestId khớp và model/result/image fields, lưu private asset; giữ decision/similarity upstream, không dùng `FaceVerificationPolicy` của feature.

**Manual override main:** [ManualOverrideService.authorize](../../spring-web/src/main/java/vn/edu/parking/service/ManualOverrideService.java) nhận `manualOverride:true`, code và text10..500 ký tự; cần JWT actor, account enabled, Desktop session active, ca trực hiện tại, quyền `AI_RESULT_CORRECT` của role enabled (legacy role-null fallback GATE_STAFF), và đủ VEHICLE + FACE evidence không legacy. Codes: `PLATE_NOT_DETECTED` (plate evidence trống), `PLATE_CORRECTION`, `VEHICLE_TYPE_CORRECTION`, `FACE_REVIEW` (REVIEW), `FACE_FAILED` (FAIL), `REGISTRATION_IMAGE_MISSING` (CAPTURED). Không phải ba exception/fail-closed REJECT/outage policy của nhánh feature; không có outage evidence fallback được xác nhận. Approved/rejected events được ghi qua ManualOverrideEvent/audit; rejected recorder có transaction riêng. Plate/type corrections được ghi original/corrected khi mutation thành công.

Có code reason không chứng minh mọi nhánh sử dụng được: createVehicle yêu cầu plateText không blank trước khi lưu nên PLATE_NOT_DETECTED gặp lệch precondition. VEHICLE_TYPE_CORRECTION không thay type đăng ký của resident vì resolveVehicleType ưu tiên Vehicle. Đây là source gaps, chưa thực thi.

`ParkingService.verifyDriver` kiểm active/authorized khi có member; nhưng member null được qua nếu không member được quyền nào có ảnh, hoặc override đã được authorize. Không mô tả main là luôn bắt buộc active member. Guest capture optional; guest exit chỉ đòi PASS hoặc override khi entry-face reference tồn tại. Resident exit không bắt buộc face proof hoặc quyền người lấy như yêu cầu NV04; đây là gap, không giảm yêu cầu.

### Tra cứu cư dân và vị trí đỗ

### Response fields dùng chung

JSON dùng camelCase; Long là number, BigDecimal là JSON number/VND theo mapping, LocalDate dạng YYYY-MM-DD, LocalDateTime ISO local không offset, Instant ISO có UTC/offset. Không suy timezone triển khai từ kiểu Java.

| Response | Fields được khai báo |
| --- | --- |
| ParkingResponse | `sessionId` Long; `plateNumber`, `ownerName`, `vehicleType`, `cardCode`, `status`, `message` strings; `entryTime`, `exitTime` LocalDateTime; `fee` BigDecimal; `warning` boolean; nullable `receiptNumber`, `paymentMethod`, `paymentReference`. Không generic success flag |
| ResidentLookupResponse | `registered`, `cardMatched`, `packageValid`, `guestEntryFaceAvailable` booleans; `plateNumber`, `ownerName`, `ownerPhone`, `apartmentNumber`, `vehicleType`, `cardCode`, `passType`, `message` strings; `validUntil` LocalDate; `authorizedMembers` array |
| AuthorizedMemberResponse | `id` Long, `fullName`, `relationship` strings, `faceImageAvailable` boolean; không image path |
| Open sessions | Controller trả `ParkingSession` entities, không `OpenParkingSessionResponse` allowlist; quan hệ/lazy serialization thực tế chưa kiểm runtime |
| ParkingSlotResponse | `id` Long; strings `slotCode`, `zoneName`, `floor`, `slotType`, `allowedVehicleType`, `statusOverride`, `assignedPlate`, **assignedOwnerName/assignedOwnerPhone/assignedApartment**, `occupiedPlate`, `borrowedPlate`, `borrowNotes`, `status`, `statusDescription`; `occupiedEntryTime`, `borrowedUntil` LocalDateTime; `overdue` boolean. Owner fields được trả từ ParkingService.toSlotResponse |
| FACE proxy result | `contractVersion`, `requestId`, `evidenceId`, `processedAt`, `modelVersion`; decision/similarity/message, thresholds nếu upstream có, `realtimeImageBase64` và các face/quality fields upstream. Spring yêu cầu image để tạo evidence |
| RecognitionResponse (ANPR) | `contractVersion`, `requestId`, `evidenceId`, `processedAt`, `modelVersion`; `plateText`, `vehicleType`, `annotatedImageBase64`, `message`; confidence floats; nullable `boundingBox` x/y/width/height; `frameIndex`. Spring thay evidenceId upstream bằng ID local |

`PREVIEW` là response state, không persisted enum. OPEN/COMPLETED/CANCELLED là SessionStatus; warning không tự rollback OPEN. Lookup/slot display cũng không tự cấp quyền nghiệp vụ.

### Lookup/open/slot reads

| Route | Request | Response / effects |
| --- | --- | --- |
| `GET /api/parking/lookup` | Query `plate`, `cardCode` optional default `""`; `GATE_OPERATE` khi JWT bật | DTO trên, ownerPhone thực, active authorizedMembers (ID/name/relationship/face-image-available), guestEntryFaceAvailable; không registration path |
| `GET /api/parking/open` | `GATE_OPERATE` khi JWT bật | OPEN ParkingSession entities, order entryTime desc; không DTO minimization của feature |
| `GET /api/parking/slots` | `GATE_OPERATE` khi JWT bật | ParkingSlotResponse[] gồm cả owner contact/apartment; Web `/slots` cần SLOT_MANAGE hoặc ROLE_MANAGEMENT |
| `GET /api/parking/recent-unassigned` | `GATE_OPERATE` khi JWT bật | Tối đa30 OPEN maps `{sessionId,plateNumber,vehicleType,entryTime,ownerName}`; ownerName kèm mô tả ô, không field slotCode riêng |
| `GET /api/parking/vehicles-unassigned` | `VEHICLE_MANAGE` khi JWT bật | Active chưa assigned maps `{id,plateNumber,ownerName,apartmentNumber,vehicleType}` |

### Slot operations

Các `POST` sau nhận JSON trừ route ghi “no body”. Khi JWT bật, dispatch-session cần `GATE_OPERATE`, các slot mutations khác cần `SLOT_MANAGE`. Success map có `success:true`, message; batch thêm `createdCount`.

| Route | Body | Service action |
| --- | --- | --- |
| `POST /api/parking/slots/{id}/assign` | JSON field `vehicleId` (Long/null) | Gán hoặc bỏ xe khỏi slot |
| `POST /api/parking/slots/{id}/borrow` | JSON fields `borrowedPlate` (string), `hours` (Integer/null), `borrowNotes` (string/null); null hours thành 2 | Thiết lập mượn slot |
| `POST /api/parking/slots/{id}/cancel-borrow` | no body | Hủy mượn |
| `POST /api/parking/slots/{id}/status` | `{ "statusOverride": string }`; controller uppercase rồi `valueOf` enum | Đặt trạng thái override |
| `POST /api/parking/slots/{id}/release` | no body | Giải phóng slot |
| `POST /api/parking/slots/{id}/dispatch-session` | `{ "sessionId": Long }` | Điều phối session vào slot |
| `POST /api/parking/slots/batch-generate` | `{ "floor":string, "zoneName":string, "prefix":string, "startNumber":int, "count":int, "slotType":string, "vehicleType":string }` | Tạo batch và trả `createdCount` |

Request records không khai báo Bean Validation; validation/domain errors phụ thuộc service và enum parsing.

### Health và lỗi Spring JSON

| Route | Response |
| --- | --- |
| `GET /api/parking/health` | Map `{status:"UP",service:"parking-web",version:"4.0-face-slots",database:<productName hoặc "Unknown">}`; thử kết nối DB nhưng nuốt lỗi, vẫn UP; không readiness tổng thể |

ParkingApiController có local handler map `IllegalArgumentException`/`IllegalStateException` thành HTTP400 JSON `{ "message": ex.getMessage() }`. Không bao phủ mọi exception/framework binding error; bình thường body methods dùng default200, nhưng không được service-call kiểm chứng trong đợt này. Không error envelope chung được chứng minh.

## Spring — quản trị, báo cáo và OCR QR

AdminController là MVC controller không có `/admin` prefix. GET pages render Thymeleaf, form POST nhận parameters/multipart và thường redirect/flash. [SecurityConfig](../../spring-web/src/main/java/vn/edu/parking/config/SecurityConfig.java) main cho `/` bằng `DASHBOARD_VIEW` hoặc `ROLE_MANAGEMENT`; vehicle/registration bằng `VEHICLE_MANAGE` hoặc `ROLE_MANAGEMENT`, không union resident/revenue guards của feature. Form matrices là tham số trọng yếu, không OpenAPI đầy đủ.

| Method/path | Input | Output / validation nổi bật |
| --- | --- | --- |
| `GET /`, `/vehicles`, `/registrations`, `/households`, `/settings`, `/slots`, `/cards`, `/pricing`, `/sessions` | `/slots`: optional floor/zone; `/cards`: optional vehicleId | HTML; lần lượt DASHBOARD_VIEW, VEHICLE_MANAGE, VEHICLE_MANAGE, RESIDENT_MANAGE, SLOT_MANAGE, SLOT_MANAGE, CARD_MANAGE, PRICING_MANAGE, HISTORY_VIEW; mọi nhóm này có ROLE_MANAGEMENT fallback |
| `POST /registrations` | VEHICLE_MANAGE hoặc ROLE_MANAGEMENT; form hộ/member/CCCD/xe và ảnh | Upsert hộ/member/xe, redirect `/cards?vehicleId=...`; checks text, CCCD, ảnh và quota theo handler; không chứng nhận đáp ứng toàn NV01 |
| `POST /vehicles` | VEHICLE_MANAGE hoặc ROLE_MANAGEMENT; plate/householdId/registeredOwnerId/type/fuel/technical fields | Kiểm owner/member cùng hộ và duplicate; redirect `/vehicles` |
| `POST /households`, `POST /members` | Form hộ/member; member có optional `cccdQrRaw`, CCCD/dates/contact/face upload/capture | Save + redirect `/households`; registration/member **lưu** raw QR trim, không tự purge |
| `GET /admin-data/vehicles/{id}`, `/admin-data/members/{id}` | id Long; VEHICLE_MANAGE/RESIDENT_MANAGE hoặc ROLE_MANAGEMENT | Vehicle có authorized member IDs. Member fields: id/householdId/fullName/citizenId/oldCitizenId/dateOfBirth/gender/relationshipToHead/phone/email/permanentAddress/cccdIssueDate/cccdIssuePlace/**cccdQrRaw**/faceImageAvailable/faceImageUrl; không raw registration storage path |
| `GET /admin-data/members/{id}/face-image` | PRIVATE_EVIDENCE_VIEW hoặc ROLE_MANAGEMENT | Server đọc private/legacy reference, MIME JPEG/PNG, no-store, audit RESIDENT_FACE_VIEW |
| `POST /slots`, `POST /slots/assign`, `POST /slots/borrow`, `POST /slots/cancel-borrow`, `POST /slots/status`, `POST /slots/release`, `POST /slots/dispatch-session`, `POST /slots/batch-generate` | Form fields `slotCode`, IDs, enum/status, plate/hours/notes hoặc batch fields tùy route | Service operation + redirect `/slots`; status/release/dispatch có branches bắt exception và flash error, không API JSON |
| `POST /settings` | `defaultMaxTwoWheelers`, `defaultMaxCars` ints | Không âm check; save policy + redirect |
| `POST /cards` | vehicleId, status CardStatus, passType; optional startDate, durationDays default30, paymentMethod/paymentReference | cardCode được sinh từ plate. MONTHLY tạo SubscriptionPayment + PaymentTransaction/receipt; method required ở service, chuyển khoản/POS cần reference; duration1..366, monthlyPrice>0. Redirect `/cards`; không provider settlement proof |
| `POST /pricing` | vehicleType/basePrice/nightPrice/overnightFee/monthlyPrice; optional/default car hours/blocks/price | Negative prices rejected; cars require positive hours; save + redirect `/pricing` |
| `GET /api/parking/revenue/monthly` | none | JSON array 12 months: year/month/label/visitRevenue/subscriptionRevenue/totalRevenue |
| `GET /api/parking/revenue/filter` | required `type`; optional `year`, `month`, `from`, `to` | JSON map label/revenues/counts/session and payment lists; invalid/incomplete type falls back current month |
| `GET /api/parking/revenue/monthly/{year}/{month}` | path integers | JSON totals and raw COMPLETED session/payment lists for month |
| `GET /api/parking/revenue/stats/{year}/{month}` | path integers | JSON household/vehicle/open counts and today/month/visit/subscription/total revenue fields |

Revenue vẫn cộng COMPLETED session fees theo exitTime + SubscriptionPayment theo paidAt, không thay bằng báo cáo PaymentTransaction đối soát. Bốn route nằm ngoài Desktop chain; Web/session matcher nhận `DASHBOARD_VIEW` hoặc `HISTORY_VIEW` hoặc `ROLE_MANAGEMENT`, không `REVENUE_VIEW`. Main không có JSON entrypoint401 riêng cho revenue; form-login redirect/403 và serialization phải kiểm runtime. CSRF Web **bỏ qua `/api/parking/**`**, kể cả revenue; GET hiện tại read-only.

### Thu tiền, biên nhận và đối soát

Nguồn [PaymentController](../../spring-web/src/main/java/vn/edu/parking/web/PaymentController.java), [PaymentTransactionService](../../spring-web/src/main/java/vn/edu/parking/service/PaymentTransactionService.java). Web session + `PAYMENT_RECONCILE` hoặc `ROLE_MANAGEMENT`; mutation cần CSRF.

| Method/path | Input | Output / effects |
| --- | --- | --- |
| `GET /payments` | Không body | HTML, recent tối đa200 giao dịch |
| `POST /payments/{id}/reconcile` | Form note (service yêu cầu không blank, ≤500 ký tự) | Chỉ PAID → RECONCILED, actor/time/note + audit, redirect `/payments` |
| `GET /payments/receipts/{id}` | id long | JSON receiptNumber/paymentType/paymentMethod/amount/status/externalReference/paidAt/sessionId/subscriptionPaymentId/collectedBy; không có→404 |
| `GET /payments/reports/reconciliation` | Optional from/to Instant; mặc định30 ngày tới hiện tại | JSON from/to/count/amount/transactions đã RECONCILED; không bank/POS reconciliation tự động |

Exit fee>0 ghi thu tiền trong transaction nghiệp vụ; method CASH/BANK_TRANSFER/POS, external reference≤120 và bắt buộc cho hai loại sau. Actor từ authentication; ca hiện tại được gắn nếu có, không luôn bắt buộc. Không có call payment provider/webhook xác nhận tiền. Fee0 không tạo payment; nhánh fee0 không có monthly pass có nguy cơ dereference payment null trong `confirmExit` — source gap chưa chạy kiểm chứng, không tự sửa code.

### Tham số form hồ sơ

Các tên dưới là request parameters, không JSON; các Web forms này cần CSRF. String tùy chọn mặc định rỗng trừ chỗ ghi khác; date dùng LocalDate, IDs Long, số kỹ thuật Integer. Optional binding không miễn checks nghiệp vụ.

| Form | Required ở handler | Optional / defaults |
| --- | --- | --- |
| `/registrations` | householdCode, apartmentNumber, fullName, citizenId, plateNumber, vehicleType, fuelType, registrationNumber, brand, modelName, color, chassisNumber, engineNumber | buildingName, contactPhone, maxTwoWheelers, maxCars, oldCitizenId, dateOfBirth, gender, relationshipToHead mặc định “Chủ hộ”, phone, email, permanentAddress, cccdIssueDate, cccdIssuePlace, **cccdQrRaw**, registrationFaceImage multipart, registrationFaceCapture base64, manufactureYear, seatCount, cylinderCapacityCc, registrationImage multipart |
| `/vehicles` | plateNumber, householdId, registeredOwnerId, vehicleType, fuelType | vehicleId, authorizedMemberIds (list Long), registrationNumber, brand, modelName, color, manufactureYear, chassisNumber, engineNumber, seatCount, cylinderCapacityCc, registrationImage multipart, registrationImagePath |
| `/households` | householdCode, apartmentNumber | buildingName, contactPhone, maxTwoWheelers, maxCars |
| `/members` | householdId, fullName | memberId, citizenId, oldCitizenId, dateOfBirth, gender, relationshipToHead, phone, email, permanentAddress, cccdIssueDate, cccdIssuePlace, **cccdQrRaw**, registrationFaceImage multipart, registrationFaceCapture base64 |

Combined registration upsert theo household code/citizenId/plate, yêu cầu ảnh đăng ký mặt và giấy xe tồn tại sau save; quyền xe bị đặt thành member đăng ký. Vehicle edit kiểm owner cùng hộ, duplicate plate, quota và requireText cho technical strings; có thể cập nhật xe của OPEN tìm theo plate. Member edit cập nhật links xe chủ sở hữu và gỡ quyền khỏi xe khác hộ. Những side effects này không có nghĩa mọi thay đổi đã có historical audit hoặc file rollback.

`POST /api/cccd/scan-qr` nhận multipart `file`; [CccdQrController](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java) trả **raw decoded QR** (`raw`) cùng citizenId/oldCitizenId/fullName/dateOfBirth/gender/permanentAddress/cccdIssueDate/message. IllegalArgumentException/IllegalStateException →400 `{message:ex.getMessage()}`; không generic scan-error/audit controls của feature. Web matcher cần RESIDENT_MANAGE hoặc ROLE_MANAGEMENT, unsafe request cần CSRF. Dữ liệu trả về và lưu raw QR là privacy gap cần xem xét theo yêu cầu, không được tài liệu hóa như đã loại bỏ.

### Stored media access

[ResidentImageStorage](../../spring-web/src/main/java/vn/edu/parking/service/ResidentImageStorage.java) lưu dưới `parking.private-storage-path` (default private-data)/residents, decode/kiểm kích thước và **re-encode JPEG**, trả `resident:{uuid}.jpg`. `parking.upload-dir` (default uploads) chỉ đọc legacy references; không có current WebConfig publish `/uploads/**`. HTTP đọc ảnh qua member face route ở trên; UUID filename/path/symlink/file-size checks ở storage không thay quyền object-level. GateEvidenceAdminController đọc metadata/assets bằng Web permission-wide matcher, không session/operation route của feature. SHA-256 kiểm asset read; no-store và success audit có source. Không có preservation/release API main. Retention mặc định30 ngày theo createdAt, batch≤100, bỏ qua OPEN sessions; cleanup mặc định **tắt** (`PARKING_EVIDENCE_CLEANUP_ENABLED:false`), không chứng minh deletion đã chạy. Encryption, effective storage root, backup/restore và privacy compliance chưa kiểm chứng.

## ANPR — recognition and face endpoints

Desktop processing dùng Spring proxy. [internal_auth.py](../../anpr-service/app/internal_auth.py) chỉ public `/health`, `/favicon.ico`; mọi path khác (kể cả `/`, `/docs`, `/redoc`, `/openapi.json`) cần `X-Parking-Service-Token`. FastAPI nhận current `PARKING_INTERNAL_SERVICE_TOKEN` hoặc previous `PARKING_INTERNAL_PREVIOUS_TOKEN` (strip, nonempty); source không enforce minimum32/ASCII/no-whitespace như feature. Thiếu config→503 `{error:"service_auth_not_configured",requestId}`; thiếu/sai token hoặc candidate>512→401 `{error:"invalid_service_token",requestId}`. Middleware trả X-Request-ID.

| Method/path | Request | Response / explicit failures in code |
| --- | --- | --- |
| `GET /health` | none, public | `{status:"UP",service:"parking-anpr",version:"4.0-robust-liveness",modelLoaded,faceModelsReady}`; flags không chứng minh accuracy/camera/E2E |
| `POST /recognize/image` | multipart file; jpg/jpeg/png/bmp/webp | RecognitionResponse; suffix/ValueError→400, >25MiB→413 (Spring HTTP limit nhỏ hơn) |
| `POST /recognize/video` | multipart file; mp4/avi/mov/mkv/wmv/m4v/webm | RecognitionResponse; suffix/ValueError→400, >250MiB→413; temp unlink finally |
| `POST /recognize/camera` | JSON cameraIndex default0, cameraSource optional, frameCount default8 clamp4..16 | RecognitionResponse; ValueError→422 |
| `POST /face/verify` | multipart registration/realtime | Compare result + contract metadata; ValueError→422, RuntimeError→503; không tạo realtimeImageBase64 như các proxy-backed variants |
| `POST /face/verify-camera` | multipart registration; form cameraIndex default0, cameraSource optional | Compare-best + realtimeImageBase64/metadata; ValueError422, RuntimeError503, unexpected502 |
| `POST /face/verify-image` | multipart registration/realtime | Compare-best + image/metadata; ValueError422, other exception502 |
| `POST /face/verify-video` | multipart registration/realtime video | Unsupported suffix400; compare-best/image/metadata; ValueError422, other exception502, temp unlink finally |
| `POST /face/capture-camera` | JSON cameraIndex default0, cameraSource optional | CAPTURED, similarity0, image/metadata; ValueError422 |
| `GET /` | Service-token header | Redirect `/docs`, cũng được middleware bảo vệ |
| `GET /favicon.ico` | none | Empty HTTP204 |

`liveness_score` is called in camera endpoints but its value does not gate `PASS` or appear in the returned verification result. Desktop `FaceVerificationResponse` binds selected fields only; generic ANPR `face` response has additional data. ANPR’s FastAPI default HTTPException serialization and infrastructure failures were not exercised here; explicit status numbers above come from `raise HTTPException` source.

The separate [face-verification-demo `/api/compare`](../../face-verification-demo/app.py) accepts multipart `cccd`, `registration`, `realtime`, but no gate caller integration was found. Do not treat this demo contract as gate security proof.

Demo `GET /api/health` trả `status` ok/missing_models, `modelsReady` boolean và matchThreshold/reviewThreshold numbers; chỉ kiểm file model tồn tại, không load/accuracy readiness. `GET /` trả HTML file, `/static/**` là resource mount. Compare trả decision/message/livenessChecked=false, thresholds.match/review, comparisons.cccdVsRegistration/realtimeVsRegistration/realtimeVsCccd mỗi mục score/status, và quality theo cccd/registration/realtime gồm box/detectionScore/brightness/sharpness/warnings. ValueError→422 `{detail}`, RuntimeError→503; không có auth gate được thừa hưởng từ ANPR.

### Preservation và lỗi multipart

Preservation/finite hold là coverage lịch sử của nhánh feature, **không có endpoint tương ứng trên main**. Main media success200 bytes/MIME/no-store; security/framework/object errors không có một envelope chung được xác nhận. [AiProxyExceptionHandler](../../spring-web/src/main/java/vn/edu/parking/web/AiProxyExceptionHandler.java) chỉ map AiProxyException thành `{message,requestId}`: local missing/invalid file400, oversize413, MIME415; upstream4xx→400, upstream5xx→502, connection/resource-access→503. Lỗi GateEvidence/registration không tự được advice này map.

Multipart quá lớn có thể qua UploadExceptionHandler thành HTML413. Tracked [application.properties](../../spring-web/src/main/resources/application.properties) main đặt max-file-size10MB/max-request-size25MB/Tomcat form-post25MB; không YAML11MB. Không suy error JSON đồng nhất hoặc nâng service limit thành HTTP limit.

## Cross-service contract, auth and retry limits

- Spring/FastAPI dùng cùng external `PARKING_INTERNAL_SERVICE_TOKEN`; previous-token chỉ phía FastAPI cho chuyển tiếp. Desktop không giữ service credential. Destination là `PARKING_AI_BASE_URL`; AiProxyConfiguration không enforce HTTPS/loopback host policy của feature. CameraSource nhận network URI, cần đánh giá SSRF/network boundary riêng.
- Tracked properties mặc định `PARKING_SECURITY_JWT_ENABLED:true`; external key locations/inline PEM phải hợp lệ. Khi tắt rõ ràng, compatibility chain **permitAll `/api/parking/**` (trừ revenue thuộc Web) và `/api/ai/**`**, không fail-closed. Service evidence/override checks vẫn có thể từ chối thiếu JWT; không vì vậy gọi permit-all là an toàn. Xem [auth](authentication.md) và [security](../security/overview.md).
- [ParkingIdempotencyService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingIdempotencyService.java) áp dụng entry/exit-confirm khi operationId không blank: UUID, key theo actor/type/ID, SHA-256 serialized payload; completed trả cached ParkingResponse, đổi payload bị400, processing dưới2phút bị400, stale có thể restart. Preview không dùng key; blank bỏ qua idempotency. Không expected-session-version/preview-token contract, không exactly-once deployment guarantee.
- Desktop `PostDurableAsync` sinh operationId và queue Entry/ExitConfirm khi HttpRequestException/IOException/OperationCanceledException; `FlushOutboxAsync` gửi lại request qua các route hiện có và xóa khi thành công. Không có bulk sync endpoint. Authentication/refresh failure không đồng nghĩa event chưa commit; queue có thể bị evidence TTL/owner/sid chặn sau login lại. Không tái khẳng định “không có offline queue” của feature.
- Password/account/role mutations có security-version/session revocation trong source; MySQL locking, retry races và durable outbox recovery chưa được chạy kiểm chứng trong đợt này.
- UI confirmation, selected member, simulated barrier và “đã thu tiền” operator input không tự chứng minh policy NV, physical actuator hoặc bank/POS settlement.
- **Requirement**: NV01–NV08 vẫn định nghĩa identity/state/payment/audit/recovery cần đạt. **Code**: main revision trên có API khác feature. **Executed trong đợt này**: chỉ kiểm tĩnh; các counts lịch sử đã gắn provenance ở đầu trang, không claim PASS main.
- Policy còn mở tại [P01–P09](../decisions/README.md); NV06 chỉ phân loại hoặc gồm sạc chưa chốt, `CLOSED` yêu cầu khác code `COMPLETED`. Xem [NV04](../requirements/nv04-resident-exit.md), [NV06](../requirements/nv06-vehicle-classification.md), [NV08](../requirements/nv08-reporting.md).
