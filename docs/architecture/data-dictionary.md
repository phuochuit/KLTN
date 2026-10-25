# Data dictionary theo source

[ERD](erd.md) · [Database/lifecycle](database.md) · [API](../api/contracts.md)

## Quy ước

Danh mục bao phủ **20 JPA entities và hai bảng nối/collection** của `main` (`abd6183`), không tính enum, DTO hoặc bảng backup SQL. Mỗi field dưới là **Java property** của entity cùng tên trong [domain](../../spring-web/src/main/java/vn/edu/parking/domain/). Tên SQL chỉ ghi khi có khai báo tường minh; các tên còn lại phụ thuộc naming strategy/schema. `NN` là nullable=false/optional=false hoặc PK trong mapping; `?` là không có ràng buộc non-null tường minh. Primitive Java không thay SQL NOT NULL. String không khai length dùng mặc định ORM; không suy ra cột DB triển khai đã đúng độ dài. Defaults là khởi tạo Java trừ khi ghi DDL. Tiền BigDecimal khai precision12/scale0 trừ field được ghi khác; đơn vị hiển thị hiện tại là VND. [SQL thủ công](../../scripts/database/) được đối chiếu riêng với entity, không phải bằng chứng đã chạy trên DB.

## Nhóm cư dân và xe

| Entity/table | Fields (type, constraint/default) |
| --- | --- |
| Household / `households` | `id` Long PK IDENTITY; `householdCode` String NN/UK/40 (`household_code`); `apartmentNumber` String NN/40; `buildingName`, `contactPhone` String ?; `maxTwoWheelers`, `maxCars` Integer ?; `active` boolean Java true; `createdAt` LocalDateTime ? (`created_at`), PrePersist điền now nếu null |
| FamilyMember / `family_members` | `id` Long PK IDENTITY; `household` Household NN ManyToOne EAGER; `fullName` String NN; `citizenId` String ?/UK/20 (`citizen_id`); `oldCitizenId` String ?/20; `cccdQrRaw` String ?/1000; `dateOfBirth`, `cccdIssueDate` LocalDate ?; `gender`, `relationshipToHead`, `phone`, `email`, `cccdIssuePlace` String ?; `permanentAddress`, `cccdImagePath`, `registrationFaceImagePath` String ?/500; `active` boolean Java true |
| Vehicle / `vehicles` | `id` Long PK IDENTITY; `plateNumber` String NN/UK/20 (`plate_number`); `ownerName` String NN; `ownerPhone`, `apartmentNumber` String ? (legacy); `household` Household ?, `registeredOwner` FamilyMember ? ManyToOne EAGER; `authorizedMembers` Set of FamilyMember ManyToMany EAGER qua bảng nối; `vehicleType` VehicleType STRING NN/50 Java MOTORBIKE; `registrationNumber`, `brand`, `modelName`, `color`, `chassisNumber`, `engineNumber` String ?; `manufactureYear`, `seatCount`, `cylinderCapacityCc` Integer ?; `fuelType` FuelType STRING Java GASOLINE; `registrationImagePath` String ?/500; `active` boolean Java true; `createdAt` LocalDateTime ? (`created_at`), PrePersist điền now |
| `vehicle_authorized_members` (không entity riêng) | `vehicle_id` và `family_member_id` là join columns tham chiếu Vehicle/FamilyMember. Không khai PK/unique pair/constraint names riêng trong annotations; Set không chứng minh constraint DB |

CCCD/raw QR/phone/address/face paths là dữ liệu nhạy cảm, không phải fields để trả mặc định cho gate. Getter/setter và quan hệ EAGER không tạo permission hoặc consent.

## Nhóm thẻ, lượt, vị trí và tài chính

| Entity/table | Fields (type, constraint/default) |
| --- | --- |
| ParkingCard / `parking_cards` | `id` Long PK IDENTITY; `cardCode` String NN/UK/30 (`card_code`); `vehicle` Vehicle ? ManyToOne EAGER; `status` CardStatus STRING NN Java ACTIVE; `passType` PassType STRING ? Java PER_VISIT, getter null→PER_VISIT; `validFrom`, `validUntil` LocalDate ?; `subscriptionFee` BigDecimal ? Java0, getter null→0 |
| ParkingSession / `parking_sessions` | `id` Long PK IDENTITY; `vehicle` Vehicle ?, `parkingCard` ParkingCard ?, `entryMember`, `exitMember` FamilyMember ? ManyToOne EAGER; `entryPlate` String NN, `exitPlate` String ?; `entryTime` LocalDateTime NN, `exitTime` LocalDateTime ?; `status` SessionStatus STRING NN Java OPEN; `detectedVehicleType` VehicleType STRING ?/50 Java MOTORBIKE; `fee` BigDecimal NN Java0; `manualOverride`, `entryFaceVerified`, `exitFaceVerified` boolean NN, columnDefinition boolean default false; `entryFaceSimilarity`, `exitFaceSimilarity` Double ?; `entryFaceImagePath` String ?/500 |
| ParkingSlot / `parking_slots` | `id` Long PK IDENTITY; `slotCode` String NN/UK/20 (`slot_code`); `floor`, `zoneName` String ?/50 Java “Tầng hầm B1”/“Khu A”; `slotType` SlotType STRING ?/30 Java RESIDENT_RESERVED; `allowedVehicleType` VehicleType STRING ?/30 Java CAR; `statusOverride` SlotStatusOverride STRING ?/30 Java NORMAL; `assignedVehicle` Vehicle ?, `currentSession` ParkingSession ? ManyToOne EAGER; `borrowedPlate` String ?/20; `borrowedUntil` LocalDateTime ?; `borrowNotes` String ?/255; `active` boolean Java true |
| PricingRule / `pricing_rules` | `id` Long PK IDENTITY; `vehicleType` VehicleType STRING NN/UK/50; `basePrice`, `overnightFee` BigDecimal NN; `nightPrice`, `monthlyPrice`, `extraBlockPrice` BigDecimal ?; `baseHours`, `extraBlockHours` Integer ?. Getters null night→base, monthly/extra→0, hours→0; không historical rule/version |
| SubscriptionPayment / `subscription_payments` | `id` Long PK IDENTITY; `parkingCard` ParkingCard NN ManyToOne EAGER; `periodStart`, `periodEnd` LocalDate NN; `amount` BigDecimal NN; `paidAt` LocalDateTime NN; `pendingPayment` → `pending_payment` boolean NN, columnDefinition boolean default false; `paymentTransaction` → `payment_transaction_id` PaymentTransaction ? OneToOne LAZY. Receipt/method/collector/reconciliation ở ledger riêng; không PriceRule snapshot |
| ParkingPolicy / `parking_policy` | `id` Long PK không generated, Java1; `defaultMaxTwoWheelers`, `defaultMaxCars` int NN Java2/1. Không DB singleton CHECK được khai trong entity |

`isMonthlyValid` cần MONTHLY/ACTIVE và dates inclusive; helper fee dùng ngày entry. Borrowed/overdue/status description của slot là projections từ fields/clock, không columns lịch sử. Session `exitMember` tồn tại trong mapping nhưng confirm hiện không persist người lấy vào field đó. Không có unique OPEN theo plate, unique currentSession/assignedVehicle hoặc unique kỳ gói được khai trong các entity này.

## Nhóm account và Desktop session

Tên SQL/DDL dưới được khai trong entity và các script `security-phase1`, `security-phase2`, `security-phase4-*` dưới [scripts/database](../../scripts/database/); Instant dùng TIMESTAMP(6) trong các script này, chưa xác nhận server timezone/schema thực tế. Main không có thư mục `db/migration` hoặc dependency Flyway.

| Entity/table | Fields (Java → SQL khi khác, type, constraint) |
| --- | --- |
| SystemAccount / `system_accounts` | `id` Long PK IDENTITY; `username` String NN/UK/80; `fullName` → `full_name` String ?/160 (getter fallback username); `passwordHash` → `password_hash` String NN/100; `role` AccountRole STRING NN/24, legacy compatibility; `securityRole` → `role_id` SecurityRole ? ManyToOne EAGER (SQL FK RESTRICT); `employeeCode` → `employee_code` String ?/UK/24; `phone` String ?/20; `email` String ?/UK/160; `citizenId` → `citizen_id` String ?/UK/20; `dateOfBirth` → `date_of_birth` LocalDate ?; `address` String ?/500; `enabled` boolean NN Java/DDL true; `mustChangePassword` → `must_change_password` boolean NN, DDL false; `securityVersion` → `security_version` long NN, DDL0; `createdAt` → `created_at` Instant NN/updatable=false |
| SecurityRole / `security_roles` | `id` Long PK IDENTITY; `code` String NN/UK/48; `displayName` → `display_name` String NN/120; `employeePrefix` → `employee_prefix` String NN/UK/8; `description` String ?/500; `systemRole` → `system_role` boolean NN/DDL false; `enabled` boolean NN/Java/DDL true; `nextEmployeeNumber` → `next_employee_number` long NN/Java/DDL1; `createdAt` → `created_at` Instant NN/DDL current timestamp/updatable=false; `permissions` Set of SecurityPermission ElementCollection EAGER |
| SecurityRole.permissions / `security_role_permissions` | `role_id` join column; `permission_code` SecurityPermission STRING NN/64. Entity declares UK pair `(role_id,permission_code)`; SQL declares composite PK and role FK ON DELETE CASCADE. No separate permission-catalog table/FK |
| DesktopSession / `desktop_sessions` | `sessionId` → `session_id` String PK/36; `account` → `account_id` SystemAccount NN ManyToOne LAZY; `createdAt`, `lastSeenAt`, `absoluteExpiresAt` → snake_case Instant NN; `revokedAt` → `revoked_at` Instant ?; `securityVersion` → `security_version` long NN; `deviceLabel` → `device_label` String ?/120. Idle timeout constant 2 giờ là logic, không field |
| DesktopRefreshToken / `desktop_refresh_tokens` | `id` Long PK IDENTITY; `session` → `session_id` DesktopSession NN ManyToOne LAZY; `tokenHash` → `token_hash` String NN/UK length64 (DDL CHAR64); `createdAt`, `expiresAt` → snake_case Instant NN; `consumedAt` → `consumed_at` Instant ?; `replacedByHash` → `replaced_by_hash` String ?/64 (DDL CHAR64, không FK) |
| SecurityAuditEvent / `security_audit_events` | `id` Long PK IDENTITY; `actorAccountId` → `actor_account_id` Long ? không FK; `actorUsername` → `actor_username` String ?/80; `action` String NN/48; `targetType` → `target_type` String ?/48; `targetReference` → `target_reference` String ?/120; `outcome` String NN/16; `reason` String ?/500; `evidenceReference` → `evidence_reference` String ?/500 không FK; `occurredAt` → `occurred_at` Instant NN/updatable=false |

`security-phase1-mysql.sql` giữ CHECK `role` hai giá trị MANAGEMENT/GATE_STAFF; dynamic-RBAC SQL để `role_id`/employee code nullable cho rollout, `SecurityRoleBootstrap` xử lý backfill. Authority dùng dynamic grants khi có role và còn legacy compatibility; không gọi role enum là metadata vô hiệu hoàn toàn. Account/session state/version được kiểm bởi auth services/filters; access JWT không phải entity. Refresh lưu hash chứ không raw token. Web HttpSession không có entity/table riêng. Audit actor/evidence references là scalar, không tự thêm FK.

### GateShiftAssignment / gate_shift_assignments

Nguồn [GateShiftAssignment.java](../../spring-web/src/main/java/vn/edu/parking/domain/GateShiftAssignment.java) và [gate-shifts-mysql.sql](../../scripts/database/gate-shifts-mysql.sql).

| Fields | Type / constraints |
| --- | --- |
| `id`; `account` → `account_id` | Long PK IDENTITY; SystemAccount NN ManyToOne LAZY |
| `shiftName` → `shift_name`; `duty`; `notes` | String NN/80; String NN/240; String ?/500 |
| `startsAt`, `endsAt`, `createdAt` → snake_case | Instant NN; createdAt updatable=false, constructor now |

SQL CHECK ends_at > starts_at; account FK RESTRICT, indexes account/time and time range. Không có unique cấm ca chồng lấn trong mapping.

## GateEvidence / gate_evidences và assets

Nguồn [GateEvidence.java](../../spring-web/src/main/java/vn/edu/parking/domain/GateEvidence.java), [GateEvidenceAsset.java](../../spring-web/src/main/java/vn/edu/parking/domain/GateEvidenceAsset.java) và [security-phase4-evidence-mysql.sql](../../scripts/database/security-phase4-evidence-mysql.sql). Đây là model main; model legacy `gate_evidence` singular với filename/hold/outage fields trong tài liệu nhánh cũ không phải mapping hiện tại.

| Nhóm | Fields và constraints |
| --- | --- |
| Identity/state | `id` String PK/36, columnDefinition char(36); `evidenceType` → `evidence_type` EvidenceType STRING NN/32; `operation` EvidenceOperation STRING NN/16; `sourceKind` → `source_kind` EvidenceSourceKind STRING NN/16; `status` EvidenceStatus STRING NN/16, constructor CREATED; `version` long NN @Version |
| Relations (all LAZY) | `account` → `account_id` SystemAccount NN; `desktopSession` → `desktop_session_id` DesktopSession NN; `parkingSession` → `parking_session_id` ParkingSession ?; `familyMember` → `family_member_id` FamilyMember ?; `gateShiftAssignment` → `gate_shift_assignment_id` GateShiftAssignment ?; `consumedByAccount` → `consumed_by_account_id` SystemAccount ? |
| Correlation | `requestId` → `request_id` String NN/128; `upstreamEvidenceId` → `upstream_evidence_id` String ?/80 (no FK); `modelVersion` → `model_version` String NN/160; `cameraId` → `camera_id` String ?/120; `frameIndex` → `frame_index` Integer ? |
| Recognition | `plateNumber` → `plate_number` String ?/30; `vehicleType` → `vehicle_type` String ?/50; `detectionConfidence`, `ocrConfidence`, `vehicleConfidence` → snake_case BigDecimal ? precision8/scale6 |
| Face | `faceDecision` → `face_decision` String ?/24; `faceSimilarity`, `faceMatchThreshold`, `faceReviewThreshold` → snake_case BigDecimal ? precision8/scale6 |
| Liveness | `livenessStatus` → `liveness_status` LivenessStatus STRING NN/24, constructor/DDL NOT_EVALUATED; `livenessScore` → `liveness_score` BigDecimal ? precision8/scale6; `livenessProvider` → `liveness_provider` String ?/80; `livenessModelVersion` → `liveness_model_version` String ?/120 |
| Digests | `requestSha256` → `request_sha256` String ?/64; `responseSha256` → `response_sha256` String NN/64; SQL CHAR64 |
| Lifetime | `createdAt` → `created_at`, `expiresAt` → `expires_at` Instant NN; createdAt updatable=false; `consumedAt` → `consumed_at`, `deletedAt` → `deleted_at` Instant ? |

| GateEvidenceAsset / `gate_evidence_assets` | Type / constraints |
| --- | --- |
| `id`; `evidence` → `evidence_id` | String PK/36 char(36); GateEvidence NN ManyToOne LAZY, join column char(36) |
| `assetRole` → `asset_role`; `storageKey` → `storage_key` | EvidenceAssetRole STRING NN/32; String NN/UK/255 |
| `sha256`; `mediaType` → `media_type`; `sizeBytes` → `size_bytes` | String NN/64 (SQL CHAR64); String NN/80; long NN |
| `widthPx`, `heightPx` → snake_case | Integer ? |
| `createdAt`, `deletedAt` → snake_case | Instant NN/updatable=false; Instant ? |

EvidenceOperation ENTRY/EXIT; EvidenceType VEHICLE/FACE/CAPTURE/COMBINED; EvidenceSourceKind IMAGE/VIDEO/CAMERA; EvidenceStatus CREATED/READY/CONSUMED/EXPIRED/REJECTED; EvidenceAssetRole ANNOTATED_VEHICLE/REALTIME_FACE/GUEST_ENTRY_FACE/ORIGINAL_INPUT. Enum presence does not prove every variant is emitted. `GateEvidenceService` currently creates VEHICLE/FACE with annotated vehicle/realtime face assets, TTL default10 minutes (1–60). Retention default30 days is based on createdAt, skips OPEN sessions, and is distinct from TTL. No finite hold fields exist here. Liveness columns exist, but creation leaves NOT_EVALUATED; camera motion score is not surfaced as a gate decision.

## ManualOverrideEvent / manual_override_events

Nguồn [ManualOverrideEvent.java](../../spring-web/src/main/java/vn/edu/parking/domain/ManualOverrideEvent.java), phase4 evidence SQL và [manual-corrections SQL](../../scripts/database/security-phase4-manual-corrections-mysql.sql).

| Fields | Type / constraints |
| --- | --- |
| `id`; `operation`; `outcome` | Long PK IDENTITY; EvidenceOperation STRING NN/16; ManualOverrideOutcome STRING NN/16 (APPROVED/REJECTED) |
| `reasonCode` → `reason_code`; `reasonText` → `reason_text` | String NN/50; String NN/500 |
| Relations (LAZY) | `account` → `account_id` SystemAccount NN; `desktopSession` → `desktop_session_id` DesktopSession NN; `gateShiftAssignment` → `gate_shift_assignment_id` GateShiftAssignment ?; `parkingSession` → `parking_session_id` ParkingSession ?; `vehicleEvidence` → `vehicle_evidence_id`, `faceEvidence` → `face_evidence_id` GateEvidence ? char(36) |
| Before/after | `originalPlateNumber`, `correctedPlateNumber` → snake_case String ?/30; `originalVehicleType`, `correctedVehicleType` → snake_case String ?/50 |
| `createdAt` → `created_at` | Instant NN/updatable=false |

Reason codes: PLATE_NOT_DETECTED, PLATE_CORRECTION, VEHICLE_TYPE_CORRECTION, FACE_REVIEW, FACE_FAILED, REGISTRATION_IMAGE_MISSING. Validation also requires an active shift, active Desktop session, AI_RESULT_CORRECT/legacy gate-staff eligibility, both vehicle and face evidence, and 10–500-character text. FACE_FAILED expects faceDecision FAIL, whereas current FaceEngine emits REJECT; createVehicle also requires nonblank plateText despite PLATE_NOT_DETECTED reason. Enum presence is not live branch acceptance. These source rules differ from the old three-case B1-A/outage policy; do not treat either as requirement acceptance automatically.

## PaymentTransaction / payment_transactions

Nguồn [PaymentTransaction.java](../../spring-web/src/main/java/vn/edu/parking/domain/PaymentTransaction.java) và [phase5 payments SQL](../../scripts/database/security-phase5-payments-mysql.sql).

| Fields | Type / constraints |
| --- | --- |
| `id`; `receiptNumber` → `receipt_number` | Long PK IDENTITY; String NN/UK/40 |
| `paymentType` → `payment_type`; `paymentMethod` → `payment_method`; `status` | PaymentType STRING NN/32 (PARKING_EXIT/SUBSCRIPTION); PaymentMethod STRING NN/32 (CASH/BANK_TRANSFER/POS); PaymentStatus STRING NN/32, constructor PAID (also RECONCILED/VOIDED/REFUNDED enum) |
| `amount`; `externalReference` → `external_reference` | BigDecimal NN precision12/scale0; String ?/120 |
| Source links (LAZY) | `parkingSession` → `parking_session_id` ParkingSession ? OneToOne; `subscriptionPayment` → `subscription_payment_id` SubscriptionPayment ? OneToOne; both have named UKs in entity/SQL |
| Actor/shift links (LAZY) | `collectedBy` → `collected_by_account_id` SystemAccount NN ManyToOne; `gateShiftAssignment` → `gate_shift_assignment_id` GateShiftAssignment ? ManyToOne; `reconciledBy` → `reconciled_by_account_id` SystemAccount ? ManyToOne |
| Times/note | `paidAt` → `paid_at` Instant NN; `reconciledAt` → `reconciled_at` Instant ?; `reconciliationNote` → `reconciliation_note` String ?/500 |

SQL CHECK requires exactly one session/subscription source and nonnegative amount; this is not declared as a JPA CHECK. Service records collection and PAID→RECONCILED; VOIDED/REFUNDED enum values alone are not implemented refund workflows or external bank/POS settlement proof.

## ParkingOperation / parking_operations

Nguồn [ParkingOperation.java](../../spring-web/src/main/java/vn/edu/parking/domain/ParkingOperation.java) và [phase5 offline/idempotency SQL](../../scripts/database/security-phase5-offline-idempotency-mysql.sql).

| Fields | Type / constraints |
| --- | --- |
| `id`; `actorUsername` → `actor_username` | Long PK IDENTITY; String NN/100, scalar (not account FK) |
| `operationType` → `operation_type`; `operationKey` → `operation_key` | String NN/32; String NN/36; composite UK actor/type/key |
| `payloadHash` → `payload_hash`; `status` | String NN/64; ParkingOperationStatus STRING NN/16 (PROCESSING/COMPLETED) |
| `responseJson` → `response_json` | String ? @Lob columnDefinition LONGTEXT |
| `createdAt`, `updatedAt` → snake_case | Instant NN; createdAt updatable=false |

Desktop outbox JSON is **not** another JPA entity: local records contain operationId/type/request/createdAt/lastAttemptAt/lastError; see [system recovery boundary](system.md). Keys are optional at the API service boundary; presence of this table does not protect every request or distinct operation key.

## Index/FK và initialization

| Script dưới `scripts/database/` | Khai báo tường minh |
| --- | --- |
| security-phase1-mysql.sql | PK account/audit; UK username; CHECK role/outcome; audit occurred_at and action/occurred_at indexes; no actor/evidence FK |
| security-phase2-mysql.sql | PK session/token, UK token_hash; account/session FK RESTRICT; session account/revoked/absolute-expiry and refresh session/expiry indexes |
| gate-shifts-mysql.sql | Account FK RESTRICT; account/time and time indexes; ends_at > starts_at CHECK |
| security-phase4-account-full-name-mysql.sql; security-phase4-dynamic-rbac-mysql.sql | full_name; role/code/prefix UK; role permissions composite PK/CASCADE FK; nullable account role_id RESTRICT FK; employee/email/citizen UKs and employee profile columns |
| security-phase4-evidence-mysql.sql | Evidence session/status/expiry, parking-session, created, status/expiry, request indexes; asset storage UK and evidence/role index; override account/created and parking-session indexes. Evidence/asset/override FKs RESTRICT except shift references SET NULL. Also adds historical `manual_override_allowed` account column, not a current entity property or authorization input |
| security-phase4-manual-corrections-mysql.sql | Four nullable original/corrected plate/type columns; no new FK |
| security-phase5-payments-mysql.sql | Receipt/source UKs, exact-one-source/nonnegative CHECKs, FK sources/collector/reconciler RESTRICT and shift SET NULL; subscription reverse transaction FK/UK; paidAt/status/collector-time/shift-time indexes |
| security-phase5-offline-idempotency-mysql.sql | UK actor/type/key; status CHECK PROCESSING/COMPLETED; updated_at index |
| plate-as-card-identifier-mysql.sql | Preflight conflict queries, retained `parking_card_code_migration_backup` table and normalized card-code update; backup is not a JPA entity or a new source of pass attributes |

Main uses manual SQL scripts, not Flyway V1–V6. `application.properties` declares Hibernate ddl-auto=update; SQL CHECKs/indexes/backfills are not guaranteed equivalent to ORM update, and scripts depend on an existing compatible business schema. Legacy V1–V6/H2 rehearsal claims belong to prior branch documentation, not current execution evidence. No SQL, application, test or operational DB was run during this docs-only sync. Default Java, getter fallback and annotations do not prove deployed constraints, historical data accuracy or approval.
