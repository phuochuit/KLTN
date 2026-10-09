# Hợp đồng API quan sát được

[Kiến trúc hệ thống](../architecture/system.md) · [Bảo mật và trust boundaries](../security/overview.md) · [Phạm vi NV01–NV08](../requirements/README.md) · [Traceability](../requirements/traceability.md)

## Phạm vi và mức bằng chứng

Đây là inventory tĩnh từ Spring controllers/DTOs/service, ANPR FastAPI handlers/schemas và Desktop clients/models. Mapping chứng minh route được định nghĩa, không chứng minh server đã chạy hoặc luồng đã được kiểm thử end-to-end. Ngày 2026-10-09 Spring Maven suite chạy 108 tests (0 failed), gồm 54 `ParkingFlowIntegrationTest`, 3 Web revenue tests, 1 `CccdQrControllerTest` và 8 `AnprServiceClientTest`; Desktop tests chạy 12/12. ANPR suite có kết quả trước đó 10/10; không chạy lại trong lát F1–F5 vì ANPR code/dependencies không đổi. H2/unit/fake-service results không phải end-to-end deployment evidence.

| Dấu | Nghĩa |
| --- | --- |
| **Code** | Route/shape/side effect quan sát trong source |
| **Requirement** | Quy tắc được mô tả trong NV tương ứng; không tự chứng minh code đạt |
| **Test-present** | Assertion có trong test file; chưa xác nhận execution ở slice này |
| **Unverified** | Runtime/status/error serialization/deployment chưa được kiểm chứng |

## Ranh giới service và caller

| Service | Base URL phía client / route ownership | Caller đã xác minh |
| --- | --- | --- |
| Spring Web | [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `/api/parking/**`; [CccdQrController](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java) `/api/cccd/**`; [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) pages, forms và một số JSON routes. Desktop default `http://localhost:8080` | [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) gọi gate, image/video/face recognition, lookup, health, slots, evidence media, unassigned/slot-operation APIs. Browser quản trị dùng Admin pages/forms và revenue JSON |
| ANPR FastAPI | [main.py](../../anpr-service/app/main.py) routes tại service root; Desktop default `http://localhost:8001` | Spring gọi image/video/face processing với internal Bearer; Desktop không gửi recognition/camera requests trực tiếp. `/health` remains public and Desktop may query it for status. No client-supplied face URL fetch |
| Face-verification demo | ASGI app độc lập tại [app.py](../../face-verification-demo/app.py) | Không tìm thấy gate/desktop caller tới demo `/api/compare`; không coi đây là tích hợp production |

Base URLs là client defaults, không bind address/deployment proof. Desktop dùng JSON camelCase và multipart `file`; ordinary API calls use 10s, Desktop image/video/face requests use 45s for image and 60s for video/face, while Spring's ANPR HTTP client has a 45s whole-request timeout. The Spring filter chain has no CORS allowlist; MockMvc observed no `Access-Control-Allow-Origin` on an untrusted CCCD preflight. A deployed proxy may alter this; network/TLS/CORS deployment remains unverified.

## Spring — trạm gác, nghiệp vụ và sơ đồ bãi xe (NV03–NV07)

### Session entry/exit

`POST /api/parking/entry`, `/exit-preview`, `/exit-confirm` đều nhận JSON. Entry gắn `@Valid EntryRequest`; hai exit route dùng `@Valid ExitRequest`. Chỉ `plateNumber` có `@NotBlank`. Không có constraints Bean Validation cho chuỗi khác, boolean hoặc IDs trong records.

| Route | Body (type; required theo DTO) | Normal body / side effect theo source |
| --- | --- | --- |
| `POST /api/parking/entry` | `EntryRequest`: `plateNumber` string **NotBlank**; `cardCode`, `vehicleType` nullable; legacy `manualOverride`, `faceVerified`, `faceSimilarity`; `familyMemberId` Long nullable in the DTO but required for a registered vehicle; `evidenceId` required; `faceEvidenceId` optional; `override` optional `ManualOverrideRequest { reason: string (10–450 chars) }`; legacy `realtimeFaceImageBase64` nullable and non-authoritative | `ParkingResponse`. Server loads operation-matched evidence and uses its plate/type, except an authenticated reasoned outage override may enter the operator-supplied plate bound to the resulting session. A registered vehicle requires a selected active family member authorized for that vehicle; a missing/inactive/unrelated member is rejected. Optional face evidence must be recent and server-owned. Allowed override cases: authorized member's missing registration reference image, linked backend `AI_REVIEW`, or backend-recorded recognition-service outage. Requires `OVERRIDE_CREATE`, specific reason, actor from principal and audit; AI `REJECT`/unlisted failures denied. Guest capture remains optional and, when supplied, is bound/consumed to the new session. Existing business checks remain |
| `POST /api/parking/exit-preview` | `ExitRequest`: `plateNumber` **NotBlank**; `cardCode`, `vehicleType` nullable; legacy `manualOverride`, `faceVerified`, `faceSimilarity`; `familyMemberId`; required `evidenceId`; optional `faceEvidenceId`; optional `override: { reason: string (10–450 chars) }` | `ParkingResponse` with `PREVIEW`; server recognition matches EXIT operation and binds to the open session. For a backend-recorded recognition outage the manual plate must match the open session's entry plate; linked `AI_REVIEW` or service outage requires the reasoned permissioned override. Missing registration image is entry-only. Guest with stored entry-face image requires matching face proof unless an approved linked `AI_REVIEW`/service-outage exception applies; resident exit face proof is accepted when supplied but not mandatory. Preview does not consume evidence or close session |
| `POST /api/parking/exit-confirm` | Same `ExitRequest` | `ParkingResponse`; revalidates operation/session, writes `COMPLETED`, and consumes exit recognition plus any supplied face proof in the transaction. The same limited reasoned exit exceptions apply; no payment receipt/settlement field or idempotency key |
| `POST /api/parking/anpr/recognize/image` | Desktop JWT; GATE_STAFF/MANAGEMENT; multipart `file` + `operation`; max 10 MiB, jpg/jpeg/png/bmp/webp suffix must match signature | Spring forwards with external service Bearer to FastAPI, validates response, privately stores uploaded/derived evidence, and returns server-created `evidenceId` with recognition result |
| `POST /api/parking/anpr/recognize/video` | Desktop JWT; GATE_STAFF/MANAGEMENT; multipart `file` + `operation`; max 10 MiB and supported video suffix/signature | Spring proxies to authenticated FastAPI; stores selected result frame as private evidence and returns `evidenceId`. Isolated H2/fake test only; no live video/camera proof |
| `POST /api/parking/anpr/face/capture` | Desktop JWT; JSON `{ "operation":"ENTRY", "plateNumber":string }`; guest capture only | Spring proxies authenticated FastAPI camera capture, stores private `FACE_VERIFICATION` evidence and returns `evidenceId`, `CAPTURED`, similarity/message and preview image data |
| `POST /api/parking/anpr/face/verify` | Desktop JWT; JSON `{ "operation":"ENTRY"|"EXIT", "plateNumber":string, "familyMemberId":Long|null }` | Spring reads an active authorized member's registration image or the guest entry image for the open session; proxies camera verification, stores result privately and returns evidence ID/decision/similarity. `PASS` is required where the service applies the face check; liveness is not a blocking decision |
| `GET /api/parking/sessions/{sessionId}/operations/{operation}/evidence/{evidenceKind}/{evidenceId}/image` | Desktop JWT; optional `derived` boolean | Private image, `no-store`; every path value must match a persisted gate-evidence record bound to that parking session/operation/kind. GATE_STAFF additionally requires its own account/device session and an OPEN session; MANAGEMENT can read any matching gate-evidence object through this route. Every read records actor, time, session/operation, evidence ID and image action without image content. Expired unheld/unbound or mismatched evidence is hidden; resident/CCCD images are not in this store. The former UUID-only route is not exposed and is denied by the allowlist |
| `POST/DELETE /api/parking/sessions/{sessionId}/operations/{operation}/evidence/{id}/preservation` | Desktop JWT; MANAGEMENT only; POST JSON `{preserveUntil, reason}` | Preservation/release is bound to the evidence's session and operation; finite deadline and reason required for approval, and the action is audited. Gate-evidence 30-day policy remains unchanged |

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
  "realtimeFaceImageBase64": null
}
```

`realtimeFaceImageBase64` còn trong `EntryRequest` để tương thích nhưng controller không dùng nó làm ảnh bằng chứng; Desktop gửi `faceEvidenceId` do Spring cấp. `faceVerified`/`faceSimilarity` vẫn có trong Java DTO cũ nhưng được service từ chối nếu client gửi giá trị khẳng định.

**Service behavior:** `ParkingService` rejects client-supplied true/non-null `faceVerified` and all client `manualOverride` claims. The optional `override.reason` is accepted only for the three server-established cases: missing registration reference image for an active authorized resident member, backend-linked `AI_REVIEW`, or backend-recorded service outage. `OVERRIDE_CREATE`, a specific reason, principal-derived actor and evidence/session association are required; AI `REJECT` and unlisted failures fail closed. For outage entry, the operator-supplied plate is stored on the new session and tied to the server-created outage evidence/audit; for outage exit, it must match the OPEN session plate. Existing member/card/session checks still apply. Guest entry face capture is optional; if supplied, it is bound and consumed with the new parking session. Guest exit requires recent same-session PASS evidence when entry-face evidence exists unless the approved backend `AI_REVIEW`/service-outage exception applies. Resident exit face proof can be supplied and is bound/consumed, but source does not require it for every resident exit. Face proofs expire for operation use; liveness is calculated upstream but does not block PASS. `findOpenSession` prefers plate and falls back through card.

If a resident vehicle is found but no authorized member is selected, the entry fails before recognition evidence is consumed or a session is created. Missing registration-face image still requires the approved, reasoned override; it does not waive member authorization.

### Tra cứu cư dân và vị trí đỗ

| Route | Request | Response / effects |
| --- | --- | --- |
| `GET /api/parking/lookup` | Query `plate` và `cardCode`, cả hai optional, default `""` | `ResidentLookupResponse`: registered/cardMatched, plate/owner/apartment/type/card/pass/dates/monthlyValid/message, `authorizedMembers[]` with ID/name/relationship/face-image-available only. Registration paths and guest face evidence IDs are not returned. `ownerPhone` is retained as a compatibility field but empty for gate lookup |
| `GET /api/parking/open` | none | `OpenParkingSessionResponse[]` allowlists session id, entry plate, vehicle type and entry time; does not serialize `ParkingSession` entities |
| `GET /api/parking/slots` | none | Operational `ParkingSlotResponse[]`: slot identity/location/type/status, assigned/occupied/borrowed plate/time info and status description. The API omits `assignedOwnerName`, `assignedOwnerPhone` and `assignedApartment` for both signed roles; the Desktop map model accepts these absent optional fields. The separate Management Web `/slots` page retains its management view |
| `GET /api/parking/recent-unassigned` | none | Tối đa 30 OPEN session maps `{sessionId, plateNumber, vehicleType, entryTime, slotCode}`; không trả owner name |
| `GET /api/parking/vehicles-unassigned` | none | Vehicle maps `{id, plateNumber, vehicleType}` cho active xe chưa assigned; không trả owner name/apartment |

### Slot operations

Các `POST` sau nhận JSON trừ route ghi “no body”. Success body là map với `success:true`, message, và riêng batch có `createdCount`.

| Route | Body | Service action |
| --- | --- | --- |
| `POST /api/parking/slots/{id}/assign` | `{ "vehicleId": Long|null }` | Gán hoặc bỏ xe khỏi slot |
| `POST /api/parking/slots/{id}/borrow` | `{ "borrowedPlate": string, "hours": Integer|null, "borrowNotes": string|null }`; null hours thành 2 | Thiết lập mượn slot |
| `POST /api/parking/slots/{id}/cancel-borrow` | no body | Hủy mượn |
| `POST /api/parking/slots/{id}/status` | `{ "statusOverride": string }`; controller uppercase rồi `valueOf` enum | Đặt trạng thái override |
| `POST /api/parking/slots/{id}/release` | no body | Giải phóng slot |
| `POST /api/parking/slots/{id}/dispatch-session` | `{ "sessionId": Long }` | Điều phối session vào slot |
| `POST /api/parking/slots/batch-generate` | `{ "floor":string, "zoneName":string, "prefix":string, "startNumber":int, "count":int, "slotType":string, "vehicleType":string }` | Tạo batch và trả `createdCount` |

Request records không khai báo Bean Validation; validation/domain errors phụ thuộc service và enum parsing.

### Health và lỗi Spring JSON

| Route | Response |
| --- | --- |
| `GET /api/parking/health` | Map `{status:"UP", service:"parking-web"}`; minimal application response, không kiểm tra tổng thể database/API/AI/camera readiness |

ParkingApiController có local handler map `IllegalArgumentException`/`IllegalStateException` thành HTTP 400 với JSON `{ "message": ex.getMessage() }`. Nó không bao phủ mọi exception/framework binding errors. Route body methods không khai explicit success status; test source dùng 200, nhưng status/error bodies khác chưa runtime-verified. Không có error envelope thống nhất được chứng minh.

## Spring — quản trị, báo cáo và OCR QR

AdminController là MVC controller không có `/admin` class prefix. `GET` pages render Thymeleaf views; form `POST`s nhận request parameters/multipart và thường redirect kèm flash message. Các route cũng chịu broad SecurityConfig rule `/api/**` nếu path khớp; tên controller không tạo role protection. Form field matrices dưới đây là các tham số trọng yếu, không phải OpenAPI schema; route/controller source là chuẩn cho field đầy đủ.

| Method/path | Input | Output / validation nổi bật |
| --- | --- | --- |
| `GET /`, `GET /vehicles`, `GET /registrations`, `GET /households`, `GET /settings`, `GET /slots`, `GET /cards`, `GET /pricing`, `GET /sessions` | `/slots` nhận optional query `floor`, `zone`; `/cards` optional `vehicleId` | HTML view tương ứng, không JSON contract |
| `POST /registrations` | Form parameters hộ/member/CCCD/xe và optional face/registration multipart images; các required params được đánh dấu trong handler | Tạo/cập nhật hộ + member + xe trong `@Transactional`, redirect `/cards?vehicleId=...`; kiểm CCCD 12 chữ số, plate dài ≥5, required text/images và quota. Redirect/error view hành vi framework chưa runtime-verified |
| `POST /vehicles` | Form plate, householdId, registeredOwnerId, VehicleType/FuelType, technical vehicle fields; optional IDs/authorized member list/image/path | Owner/member cùng hộ và biển duplicate được kiểm trong controller; redirect `/vehicles` |
| `POST /households`, `POST /members` | Form hộ hoặc member profile; member supports optional CCCD/dates/contact/QR raw/face upload/capture | Save + redirect `/households`; member validates household, duplicate citizen ID, image presence and updates related vehicle links |
| `GET /admin-data/vehicles/{id}`, `GET /admin-data/members/{id}` | path `id:Long` | JSON maps với dữ liệu hồ sơ, bao gồm identity/contact/path; vehicle endpoint trả authorized member IDs; member endpoint trả CCCD raw/path |
| `POST /slots`, `POST /slots/assign`, `POST /slots/borrow`, `POST /slots/cancel-borrow`, `POST /slots/status`, `POST /slots/release`, `POST /slots/dispatch-session`, `POST /slots/batch-generate` | Form fields `slotCode`, IDs, enum/status, plate/hours/notes hoặc batch fields tùy route | Service operation + redirect `/slots`; status/release/dispatch có branches bắt exception và flash error, không API JSON |
| `POST /settings` | `defaultMaxTwoWheelers`, `defaultMaxCars` ints | Không âm check; save policy + redirect |
| `POST /cards` | cardCode, vehicleId, CardStatus, PassType; optional startDate; durationDays default30 | Monthly dates/fee và `SubscriptionPayment` record; duration 1..366, bảng giá required/positive; redirect `/cards`. Record không chứng minh payment settlement |
| `POST /pricing` | vehicleType/basePrice/nightPrice/overnightFee/monthlyPrice; optional/default car hours/blocks/price | Negative prices rejected; cars require positive hours; save + redirect `/pricing` |
| `GET /api/parking/revenue/monthly` | none | JSON array 12 months: year/month/label/visitRevenue/subscriptionRevenue/totalRevenue |
| `GET /api/parking/revenue/filter` | required `type`; optional `year`, `month`, `from`, `to` | JSON map label/revenues/counts/session and payment lists; invalid/incomplete type falls back current month |
| `GET /api/parking/revenue/monthly/{year}/{month}` | path integers | JSON totals and raw COMPLETED session/payment lists for month |
| `GET /api/parking/revenue/stats/{year}/{month}` | path integers | JSON household/vehicle/open counts and today/month/visit/subscription/total revenue fields |

Revenue selection sums COMPLETED session fees by exit time plus subscription-payment amounts by paid time; it is not receipt/payment confirmation or reconciliation. The four revenue routes are excluded from the Desktop JWT chain and handled by the Web/session chain with `ROLE_MANAGEMENT`. Anonymous requests receive JSON 401 and GATE_STAFF receives JSON 403. Existing GETs are read-only; Web CSRF remains enabled for unsafe methods. The browser uses its Web session and does not need a JWT.

`POST /api/cccd/scan-qr` accepts multipart `file`; success response includes parsed citizen IDs/name/date/gender/address/issue date/message, not the raw decoded QR string. Service caps the file at 10 MiB. Handled validation errors return 400 `{message}` with the validation message; handled scan errors return 400 with a fixed generic message. Successful scans, handled scan failures, and unauthenticated/insufficient-role denials are audited without image or decoded CCCD values. Other framework/unhandled failures do not have a uniform response contract established here. The response still exposes parsed personal identity fields by design; the Web security chain restricts this route to `ROLE_MANAGEMENT`.

### Stored media access

`ResidentImageStorage` uses `var/private-media` as its source default root (overrideable by `parking.upload-dir`) and returns `/uploads/residents/{uuid}.jpg` references. Its internal `readResidentImage` accepts only a UUID filename in that namespace, rejects symlinks/non-files and bounds/decodes the bytes. `WebConfig` maps `/uploads/**`; the Web security chain allows that route only to `ROLE_MANAGEMENT`; registration paths are not returned by gate lookup DTOs. This resident-image permission is separate from Gate Evidence and is not granted by its read endpoint. Gate evidence uses a separate private store; gate staff reads require owner/device-session and OPEN-session binding, while Management reads require a matching business session/operation/kind/evidence ID. Reads are audited, evidence has 30-day retention and finite holds, and no generic storage download route is exposed. Image/video/face uploads go through authenticated Spring and FastAPI routes and operation/session evidence is stored. H2 tests verify source behavior; encryption, deployment root, backup/restore, and production behavior remain unverified.

## ANPR — recognition and face endpoints

Desktop recognition/camera requests use Spring proxies; FastAPI processing routes require an internal Bearer. Request fields below are from FastAPI route signatures and its `/health` route is intentionally unauthenticated.

| Method/path | Request | Response / explicit failures in code |
| --- | --- | --- |
| `GET /health` | none | `{status:"UP"}` only; does not attest model readiness, camera availability, accuracy or end-to-end health |
| `POST /recognize/image` | multipart `file`; internal `Authorization: Bearer` required; token from external `ANPR_SERVICE_TOKEN`, minimum 32 printable ASCII characters; accepted suffix jpg/jpeg/png/bmp/webp; FastAPI/Spring max10MiB | `RecognitionResponse`; missing/weak service config→503; missing/invalid token→401; unsupported format/recognition ValueError→400; oversize→413 |
| `POST /recognize/video` | multipart `file`; internal Bearer required; accepted video suffix set in `main.py`; FastAPI/Spring max10MiB | Same response model; unsupported suffix/recognition ValueError→400; oversize→413; temporary file unlinked in finally |
| `POST /face/verify` | multipart `registration`, `realtime`; internal Bearer required; max10MiB each | Face compare result includes decision/similarity/matchThreshold/warnings/face details/message. ValueError→422; RuntimeError→503 |
| `POST /face/verify-camera` | multipart `registration` and optional form `cameraIndex` (default0); internal Bearer required; max10MiB registration | Compare fields plus `realtimeImageBase64`; caller-supplied URL fetch was removed. ValueError→422, RuntimeError→503, unexpected camera/face error→502 |
| `POST /face/capture-camera` | no body; internal Bearer required; fixed camera index0 | `{decision:"CAPTURED", similarity:0, message, realtimeImageBase64}`; ValueError→422 |
| `GET /` | none | Redirect to `/docs`; not production integration contract |
| `GET /favicon.ico` | none | Empty HTTP204 |

`liveness_score` is called in camera endpoints but its value does not gate `PASS` or appear in the returned verification result. Desktop `FaceVerificationResponse` binds selected fields only; generic ANPR `face` response has additional data. ANPR’s FastAPI default HTTPException serialization and infrastructure failures were not exercised here; explicit status numbers above come from `raise HTTPException` source.

The separate [face-verification-demo `/api/compare`](../../face-verification-demo/app.py):223–265 accepts multipart `cccd`, `registration`, `realtime`, but no caller integration was found. Do not treat this demo contract as gate security proof.

## Cross-service contract, auth and retry limits

- Desktop `ParkingApiClient` sends Spring JSON/GETs and authenticated image/video/face requests; `AnprApiClient` is retained for service health. Spring and FastAPI must receive the same externally provisioned `ANPR_SERVICE_TOKEN` for processing routes; Desktop never receives that service credential. `ANPR_BASE_URL` selects Spring-to-FastAPI destination and only HTTPS or loopback HTTP is accepted. Base URLs can be configured in UI; localhost defaults are not a secure transport guarantee.
- Spring uses a stateless JWT chain for Desktop gate, evidence, image/video/face proxy routes and `/api/auth/**`; `/api/parking/revenue/**` is routed to the Web/session chain and restricted to `ROLE_MANAGEMENT`. The Web chain denies other unlisted `/api/**` routes and allows resident `/uploads/**` only to `ROLE_MANAGEMENT`. Revenue has actual Web-session MockMvc evidence; image/video/face and evidence flows have H2/MockMvc coverage plus fake-service client tests. Gate reads are limited to owned active sessions; Management evidence access is role-based but each read must name a matching session, operation, evidence kind and ID. See [security overview](../security/overview.md).
- Desktop requires preview before clicking confirm in UI, but server confirm does not require a preview token/version. No endpoint accepts idempotency key, request nonce or expected session version. Timeout after mutation leaves result unknown; automatic/replayed request safety is not established.
- On ambiguous/invalid refresh responses, Desktop clears local authentication and requires a new login; it does not replay the failed entry/exit mutation. The regression test verifies the pending request object is unchanged, not an offline queue or server-side idempotency guarantee.
- No offline queue/sync endpoint is implemented. Compatibility requirement for a future queue: keep immutable gate-event identity separate from JWT `jti`, access/refresh credentials and evidence IDs; persist a unique server-side operation key before claiming idempotent sync; store no credentials in queued events, require re-authentication before sync, and never auto-replay entry/exit after an ambiguous timeout. Current routes do not accept such an event key and do not guarantee exactly-once processing.
- Self-service password change and management account mutations serialize on the account row; concurrency regressions exercise combined password/reset/disable/role-change state on H2 only. MySQL locking behavior remains unverified.
- Browser UI assumptions, such as confirmation, selected member, low-confidence/manual handling and barrier simulation, are not server API guarantees. `manualOverride` carries no client-supplied operator/reason; the narrow accepted entry case uses principal-derived actor and server-fixed reason.
- **Requirement**: NV01–NV08 define business rules and exceptions, including state/identity/price/report requirements. **Code**: this page lists current mappings. **Runtime**: not verified. **Executed**: Spring Maven suite passed 108/108 on 2026-10-09; `ParkingFlowIntegrationTest` passed 54/54 and `CccdQrControllerTest` passed 1/1. Desktop tests passed 12/12; the prior ANPR result is 10/10 and was not rerun for F1–F5. This does not establish all route contracts, effective deployment authorization, model/camera behavior, or end-to-end ANPR.
- Source authority remains unresolved (R01 PARTIAL); NV06 classification-only versus EV-charging variants remain conflicting; `CLOSED` requirement wording versus code `COMPLETED` remains distinct. See [NV04](../requirements/nv04-resident-exit.md), [NV06](../requirements/nv06-vehicle-classification.md), [NV08](../requirements/nv08-reporting.md).
