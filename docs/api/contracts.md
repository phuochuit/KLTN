# Hợp đồng API quan sát được

[Kiến trúc hệ thống](../architecture/system.md) · [Bảo mật và trust boundaries](../security/overview.md) · [Phạm vi NV01–NV08](../requirements/README.md) · [Traceability](../requirements/traceability.md)

## Phạm vi và mức bằng chứng

Đây là inventory tĩnh từ Spring controllers/DTOs/service, ANPR FastAPI handlers/schemas và Desktop clients/models. Mapping chứng minh route được định nghĩa, không chứng minh server đã chạy hoặc luồng đã được kiểm thử end-to-end. Không có application/test execution trong R06. Tests được dẫn là test source hiện hữu, không phải kết quả chạy mới.

| Dấu | Nghĩa |
| --- | --- |
| **Code** | Route/shape/side effect quan sát trong source |
| **Requirement** | Quy tắc được mô tả trong NV tương ứng; không tự chứng minh code đạt |
| **Test-present** | Assertion có trong test file; chưa xác nhận execution ở slice này |
| **Unverified** | Runtime/status/error serialization/deployment chưa được kiểm chứng |

## Ranh giới service và caller

| Service | Base URL phía client / route ownership | Caller đã xác minh |
| --- | --- | --- |
| Spring Web | [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `/api/parking/**`; [CccdQrController](../../spring-web/src/main/java/vn/edu/parking/web/CccdQrController.java) `/api/cccd/**`; [AdminController](../../spring-web/src/main/java/vn/edu/parking/web/AdminController.java) pages, forms và một số JSON routes. Desktop default `http://localhost:8080` | [ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) gọi gate, lookup, health, slots, unassigned/slot-operation APIs. Browser quản trị dùng Admin pages/forms và revenue JSON |
| ANPR FastAPI | [main.py](../../anpr-service/app/main.py) routes tại service root; Desktop default `http://localhost:8001` | [AnprApiClient](../../desktop-winform/AnprApiClient.cs) gọi recognition/face camera trực tiếp. Đây không phải Spring proxy |
| Face-verification demo | ASGI app độc lập tại [app.py](../../face-verification-demo/app.py) | Không tìm thấy gate/desktop caller tới demo `/api/compare`; không coi đây là tích hợp production |

Base URLs là client defaults, không bind address/deployment proof. Desktop dùng JSON camelCase và multipart `file`; timeout mặc định 10s Spring, 5 phút ANPR. Hợp đồng mạng thực tế/TLS/CORS chưa runtime-verified.

## Spring — trạm gác, nghiệp vụ và sơ đồ bãi xe (NV03–NV07)

### Session entry/exit

`POST /api/parking/entry`, `/exit-preview`, `/exit-confirm` đều nhận JSON. Entry gắn `@Valid EntryRequest`; hai exit route dùng `@Valid ExitRequest`. Chỉ `plateNumber` có `@NotBlank`. Không có constraints Bean Validation cho chuỗi khác, boolean, IDs hoặc similarity trong records.

| Route | Body (type; required theo DTO) | Normal body / side effect theo source |
| --- | --- | --- |
| `POST /api/parking/entry` | `EntryRequest`: `plateNumber` string **NotBlank**; `cardCode`, `vehicleType` string nullable; `manualOverride`, `faceVerified` boolean (Java default false nếu omitted); `familyMemberId` Long nullable; `faceSimilarity` Double nullable; `realtimeFaceImageBase64` string nullable | `ParkingResponse`: `sessionId`, `plateNumber`, `ownerName`, `vehicleType`, `cardCode`, `status`, `entryTime`, `exitTime`, `fee`, `message`, `warning`. Tạo `OPEN`, lưu các face flags/score, optional guest image, thử assign slot. Xe thiếu/sai thẻ hoặc pass hết hạn có thể vẫn mở session với `warning=true`; thẻ inactive/driver invalid/duplicate OPEN ném `IllegalStateException` |
| `POST /api/parking/exit-preview` | `ExitRequest`: `plateNumber` **NotBlank**; `cardCode`, `vehicleType` nullable; `manualOverride`, `faceVerified` boolean; `familyMemberId` Long nullable; `faceSimilarity` Double nullable. Không có face image field | `ParkingResponse`; response status được đổi thành `PREVIEW`, fee/exitTime tính tạm; service read-only, không đóng session |
| `POST /api/parking/exit-confirm` | Cùng `ExitRequest` | `ParkingResponse`; service ghi exit time/fee/flags, `COMPLETED`, clear slot tìm được đầu tiên. Không có payment receipt/settlement field hoặc idempotency key |

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

`realtimeFaceImageBase64` chỉ thuộc `EntryRequest`; Desktop tạo cùng shape cho exit, gửi chuỗi rỗng ở đó. [Models.cs](../../desktop-winform/Models.cs):5–13 và [MainForm.Request](../../desktop-winform/MainForm.cs):329–335 xác nhận client payload.

**Service behavior:** `ParkingService.enter` chuẩn hóa/kiểm tra plate, tìm member và card rồi tạo session. Entry member phải active và authorized theo vehicle; nếu có face profile thì server tin `faceVerified` hoặc `manualOverride` từ caller thay vì ký/xác minh bằng chứng AI. `verifyGuestExit` chỉ yêu cầu face nếu xe là guest **và** entry image path tồn tại. Resident exit không buộc member/face re-verification trong endpoint path này. `findOpenSession` ưu tiên plate rồi fallback qua card. Xem [ParkingService.java](../../spring-web/src/main/java/vn/edu/parking/service/ParkingService.java):40–126,383–424.

### Tra cứu cư dân và vị trí đỗ

| Route | Request | Response / effects |
| --- | --- | --- |
| `GET /api/parking/lookup` | Query `plate` và `cardCode`, cả hai optional, default `""` | `ResidentLookupResponse`: registered/cardMatched, plate/owner/contact/apartment/type/card/pass/dates/monthlyValid/message, `authorizedMembers[]`, guest entry image path. Khi không tìm thấy xe đã đăng ký trả `registered=false` cùng guest form và có thể đường dẫn ảnh entry. `cardMatched` có ý nghĩa khác theo guest/resident branch |
| `GET /api/parking/open` | none | Trả danh sách entities `ParkingSession` OPEN trực tiếp, không dedicated response DTO |
| `GET /api/parking/slots` | none | `ParkingSlotResponse[]`: slot identity/location/type/status, assigned/occupied/borrowed info, overdue/status description |
| `GET /api/parking/recent-unassigned` | none | Tối đa 30 OPEN session maps `{sessionId, plateNumber, vehicleType, entryTime, ownerName}`; ownerName có thêm slot note |
| `GET /api/parking/vehicles-unassigned` | none | Vehicle maps `{id, plateNumber, ownerName, apartmentNumber, vehicleType}` cho active xe chưa assigned |

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
| `GET /api/parking/health` | Map `{status:"UP", service:"parking-web", version, database}`; database cố lấy JDBC product name, fallback `Unknown`; không phải tổng health check phụ thuộc API/AI/camera |

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

Revenue selection sums COMPLETED session fees by exit time plus subscription-payment amounts by paid time; it is not receipt/payment confirmation or reconciliation. The `/api/**` permitAll rule exposes these JSON routes unauthenticated in current configuration.

`POST /api/cccd/scan-qr` accepts multipart `file`; response map contains `raw`, citizen IDs/name/date/gender/address/issue date/message. Service caps file at 10 MiB and parses QR content; controller converts `IllegalArgumentException`/`IllegalStateException` to 400 `{message}`. The response exposes personal identity fields by design; auth policy currently permits `/api/**`.

## ANPR — recognition and face endpoints

The Desktop calls ANPR directly. Request fields below are from FastAPI route signatures and the [AnprApiClient](../../desktop-winform/AnprApiClient.cs); no Spring proxy path is involved.

| Method/path | Request | Response / explicit failures in code |
| --- | --- | --- |
| `GET /health` | none | `{status, service, version, modelLoaded, faceModelsReady}`; file/cache readiness indicators only |
| `POST /recognize/image` | multipart `file`; accepted suffix jpg/jpeg/png/bmp/webp; max25MiB | `RecognitionResponse` aliases `plateText`, `vehicleType`, detection/ocr/vehicle confidence, nullable boundingBox `{x,y,width,height}`, frameIndex, annotatedImageBase64, message. Unsupported suffix/recognition ValueError → HTTP400; oversize →413 |
| `POST /recognize/video` | multipart `file`; accepted video suffix set in `main.py`; max250MiB | Same response model; unsupported suffix/recognition ValueError →400; oversize →413; temporary file unlinked in finally |
| `POST /face/verify` | multipart `registration`, `realtime` | Face compare result includes decision/similarity/matchThreshold/warnings/face details/message. ValueError→422; RuntimeError→503 |
| `POST /face/verify-camera` | JSON `{ "registeredImageUrl": string, "cameraIndex": int }`; cameraIndex defaults0 | Compare fields plus `realtimeImageBase64`. URL string must start with localhost/127.0.0.1 HTTP prefix. Prefix rejection returns HTTP 400. ValueError→422, RuntimeError→503, fetch/other exception→502 |
| `POST /face/capture-camera` | no body; fixed camera index0 | `{decision:"CAPTURED", similarity:0, message, realtimeImageBase64}`; ValueError→422 |
| `GET /` | none | Redirect to `/docs`; not production integration contract |
| `GET /favicon.ico` | none | Empty HTTP204 |

`liveness_score` is called in camera endpoints but its value does not gate `PASS` or appear in the returned verification result. Desktop `FaceVerificationResponse` binds selected fields only; generic ANPR `face` response has additional data. ANPR’s FastAPI default HTTPException serialization and infrastructure failures were not exercised here; explicit status numbers above come from `raise HTTPException` source.

The separate [face-verification-demo `/api/compare`](../../face-verification-demo/app.py):223–265 accepts multipart `cccd`, `registration`, `realtime`, but no caller integration was found. Do not treat this demo contract as gate security proof.

## Cross-service contract, auth and retry limits

- Desktop `ParkingApiClient` sends Spring JSON or GETs and deserializes response records; `AnprApiClient` sends files as multipart `file` (application/octet-stream), face verify JSON or empty capture POST. Base URLs can be configured in UI; localhost defaults are not a secure transport guarantee.
- Spring security permits `/api/**`; browser API callers need no Spring login under current matcher. Admin form pages require authenticated session but no route requires `ADMIN` specifically. See [security overview](../security/overview.md).
- Desktop requires preview before clicking confirm in UI, but server confirm does not require a preview token/version. No endpoint accepts idempotency key, request nonce or expected session version. Timeout after mutation leaves result unknown; automatic/replayed request safety is not established.
- Browser UI assumptions, such as confirmation, selected member, low-confidence/manual handling and barrier simulation, are not server API guarantees. `manualOverride` has no operator identity/reason in DTO.
- **Requirement**: NV01–NV08 define business rules and exceptions, including state/identity/price/report requirements. **Code**: this page lists current mappings. **Runtime**: not verified. **Test-present**: [ParkingFlowIntegrationTest](../../spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java):51–125 has entry/preview/confirm, monthly and guest-face MockMvc assertions; lines107–112 admin page checks; source not executed in R06. It does not establish all route contracts or production auth.
- Source authority remains unresolved (R01 PARTIAL); NV06 classification-only versus EV-charging variants remain conflicting; `CLOSED` requirement wording versus code `COMPLETED` remains distinct. See [NV04](../requirements/nv04-resident-exit.md), [NV06](../requirements/nv06-vehicle-classification.md), [NV08](../requirements/nv08-reporting.md).
