# ERD hiện tại theo entity và SQL source

[Database/lifecycle](database.md) · [Data dictionary](data-dictionary.md) · [Kiến trúc](system.md)

## Phạm vi và ký hiệu

Sơ đồ bao phủ **20 JPA entities và hai bảng nối/collection** trên `main` (`abd6183`). Nguồn là các lớp trong [domain](../../spring-web/src/main/java/vn/edu/parking/domain/) và [SQL scripts](../../scripts/database/), không phải introspection DB thật. PK/UK/FK mô tả metadata/DDL source, không chứng nhận scripts đã áp dụng hoặc constraints trên MySQL hiện tại. Chi tiết mọi field ở [data dictionary](data-dictionary.md); diagram dùng subset field để giữ khả năng đọc, không bỏ entity khỏi inventory.

- `||`: đúng một; `o|`: không hoặc một; `o{`: không hoặc nhiều.
- Với nghiệp vụ, tên quan hệ là Java property; các FK SQL không được đặt tên tường minh không được tự đoán tên cột.
- Nhóm security/evidence/payment có tên join columns tường minh. GateEvidence hiện là quan hệ JPA LAZY, không còn model scalar IDs của tài liệu legacy.
- Audit actor/evidence references, refresh `replaced_by_hash`, evidence `upstream_evidence_id` và operation `actor_username` là scalar/logical references, **không có FK khai báo**; không vẽ thành FK.
- Với OneToOne payment, phía parent có 0..1 ledger row; các source link nullable nhưng SQL CHECK yêu cầu đúng một trong hai nguồn. Hai chiều subscription/payment là hai join columns riêng, không `mappedBy`.

## Tổng quan quan hệ

```mermaid
erDiagram
    households ||--o{ family_members : household
    households o|--o{ vehicles : household
    family_members o|--o{ vehicles : registeredOwner
    vehicles ||--o{ vehicle_authorized_members : vehicle_id
    family_members ||--o{ vehicle_authorized_members : family_member_id
    vehicles o|--o{ parking_cards : vehicle
    vehicles o|--o{ parking_sessions : vehicle
    parking_cards o|--o{ parking_sessions : parkingCard
    family_members o|--o{ parking_sessions : entryMember
    family_members o|--o{ parking_sessions : exitMember
    vehicles o|--o{ parking_slots : assignedVehicle
    parking_sessions o|--o{ parking_slots : currentSession
    parking_cards ||--o{ subscription_payments : parkingCard
    security_roles o|--o{ system_accounts : role_id
    security_roles ||--o{ security_role_permissions : role_id
    pricing_rules {
        Long id PK
        VehicleType vehicleType UK
    }
    parking_policy {
        Long id PK
    }
    system_accounts ||--o{ desktop_sessions : account_id
    desktop_sessions ||--o{ desktop_refresh_tokens : session_id
    system_accounts ||--o{ gate_shift_assignments : account_id
    system_accounts ||--o{ gate_evidences : account_id
    desktop_sessions ||--o{ gate_evidences : desktop_session_id
    parking_sessions o|--o{ gate_evidences : parking_session_id
    family_members o|--o{ gate_evidences : family_member_id
    gate_shift_assignments o|--o{ gate_evidences : gate_shift_assignment_id
    system_accounts o|--o{ gate_evidences : consumed_by_account_id
    gate_evidences ||--o{ gate_evidence_assets : evidence_id
    system_accounts ||--o{ manual_override_events : account_id
    desktop_sessions ||--o{ manual_override_events : desktop_session_id
    gate_shift_assignments o|--o{ manual_override_events : gate_shift_assignment_id
    parking_sessions o|--o{ manual_override_events : parking_session_id
    gate_evidences o|--o{ manual_override_events : vehicle_evidence_id
    gate_evidences o|--o{ manual_override_events : face_evidence_id
    parking_sessions o|--o| payment_transactions : parking_session_id
    subscription_payments o|--o| payment_transactions : subscription_payment_id
    payment_transactions o|--o| subscription_payments : payment_transaction_id
    system_accounts ||--o{ payment_transactions : collected_by_account_id
    system_accounts o|--o{ payment_transactions : reconciled_by_account_id
    gate_shift_assignments o|--o{ payment_transactions : gate_shift_assignment_id
    parking_operations {
        Long id PK
        String actor_username
        String operation_type
        String operation_key
    }
    security_audit_events {
        Long id PK
        Long actor_account_id
        String evidence_reference
    }
```

PricingRule được tìm bằng VehicleType, ParkingPolicy được tìm bằng id; không có FK Session→PriceRule/Policy. Audit cố ý có thể giữ actor/reference sau khi trạng thái nghiệp vụ thay đổi; không suy ra một quan hệ JPA hoặc ON DELETE policy từ tên field.

## Nghiệp vụ và bảng nối

```mermaid
erDiagram
    households {
        Long id PK
        String household_code UK
        String apartmentNumber
    }
    family_members {
        Long id PK
        String citizen_id UK
        String fullName
        String registrationFaceImagePath
    }
    vehicles {
        Long id PK
        String plate_number UK
        VehicleType vehicleType
        boolean active
    }
    vehicle_authorized_members {
        Long vehicle_id FK
        Long family_member_id FK
    }
    parking_cards {
        Long id PK
        String card_code UK
        CardStatus status
        PassType passType
        LocalDate validFrom
        LocalDate validUntil
    }
    parking_sessions {
        Long id PK
        String entryPlate
        LocalDateTime entryTime
        LocalDateTime exitTime
        SessionStatus status
        BigDecimal fee
    }
    parking_slots {
        Long id PK
        String slot_code UK
        String borrowedPlate
        LocalDateTime borrowedUntil
    }
    subscription_payments {
        Long id PK
        LocalDate periodStart
        LocalDate periodEnd
        BigDecimal amount
        LocalDateTime paidAt
        boolean pending_payment
        Long payment_transaction_id FK
    }
    households ||--o{ family_members : household
    households o|--o{ vehicles : household
    family_members o|--o{ vehicles : registeredOwner
    vehicles ||--o{ vehicle_authorized_members : vehicle_id
    family_members ||--o{ vehicle_authorized_members : family_member_id
    vehicles o|--o{ parking_cards : vehicle
    vehicles o|--o{ parking_sessions : vehicle
    parking_cards o|--o{ parking_sessions : parkingCard
    family_members o|--o{ parking_sessions : entryMember
    family_members o|--o{ parking_sessions : exitMember
    vehicles o|--o{ parking_slots : assignedVehicle
    parking_sessions o|--o{ parking_slots : currentSession
    parking_cards ||--o{ subscription_payments : parkingCard
    payment_transactions o|--o| subscription_payments : payment_transaction_id
```

Bảng nối biểu diễn quyền theo **từng xe**, không quyền mọi xe trong hộ. Không gán PK/unique pair cho bảng nối khi source chưa khai báo rõ. Slot.currentSession và assignedVehicle là ManyToOne, không tự có unique một-một. Guest là session.vehicle null, không có cột enum VISITOR. Phiên gửi xe dùng OPEN/COMPLETED/CANCELLED; PREVIEW là response, không DB state.

## Tài khoản, phiên Desktop và bằng chứng nhận dạng

```mermaid
erDiagram
    system_accounts {
        Long id PK
        String username UK
        String password_hash
        String full_name
        AccountRole role
        Long role_id FK
        String employee_code UK
        String email UK
        String citizen_id UK
        boolean enabled
        long security_version
    }
    security_roles {
        Long id PK
        String code UK
        String display_name
        String employee_prefix UK
        long next_employee_number
        boolean system_role
        boolean enabled
    }
    security_role_permissions {
        Long role_id PK, FK
        String permission_code PK
    }
    desktop_sessions {
        String session_id PK
        Long account_id FK
        Instant last_seen_at
        Instant absolute_expires_at
        Instant revoked_at
    }
    desktop_refresh_tokens {
        Long id PK
        String session_id FK
        String token_hash UK
        Instant consumed_at
        String replaced_by_hash
    }
    gate_shift_assignments {
        Long id PK
        Long account_id FK
        Instant starts_at
        Instant ends_at
    }
    gate_evidences {
        String id PK
        Long account_id FK
        String desktop_session_id FK
        Long parking_session_id FK
        Long family_member_id FK
        Long gate_shift_assignment_id FK
        Long consumed_by_account_id FK
        EvidenceType evidence_type
        EvidenceStatus status
        long version
        String request_id
        String upstream_evidence_id
        Instant expires_at
        Instant consumed_at
        Instant deleted_at
    }
    gate_evidence_assets {
        String id PK
        String evidence_id FK
        EvidenceAssetRole asset_role
        String storage_key UK
        String sha256
        Instant deleted_at
    }
    manual_override_events {
        Long id PK
        Long account_id FK
        String desktop_session_id FK
        Long gate_shift_assignment_id FK
        Long parking_session_id FK
        String vehicle_evidence_id FK
        String face_evidence_id FK
        String reason_code
        String reason_text
        String original_plate_number
        String corrected_plate_number
        String original_vehicle_type
        String corrected_vehicle_type
    }
    system_accounts ||--o{ desktop_sessions : account_id
    security_roles o|--o{ system_accounts : role_id
    security_roles ||--o{ security_role_permissions : role_id
    desktop_sessions ||--o{ desktop_refresh_tokens : session_id
    system_accounts ||--o{ gate_shift_assignments : account_id
    system_accounts ||--o{ gate_evidences : account_id
    desktop_sessions ||--o{ gate_evidences : desktop_session_id
    parking_sessions o|--o{ gate_evidences : parking_session_id
    family_members o|--o{ gate_evidences : family_member_id
    gate_shift_assignments o|--o{ gate_evidences : gate_shift_assignment_id
    system_accounts o|--o{ gate_evidences : consumed_by_account_id
    gate_evidences ||--o{ gate_evidence_assets : evidence_id
    system_accounts ||--o{ manual_override_events : account_id
    desktop_sessions ||--o{ manual_override_events : desktop_session_id
    gate_shift_assignments o|--o{ manual_override_events : gate_shift_assignment_id
    parking_sessions o|--o{ manual_override_events : parking_session_id
    gate_evidences o|--o{ manual_override_events : vehicle_evidence_id
    gate_evidences o|--o{ manual_override_events : face_evidence_id
```

Phase2/phase4 evidence SQL khai FK RESTRICT, riêng shift references của evidence/override SET NULL; shift→account RESTRICT. Dynamic-RBAC SQL khai account.role_id nullable/FK RESTRICT, role permissions CASCADE. `permission_code` không FK tới catalog riêng. File bytes ở private filesystem, metadata/digest ở asset rows, không BLOB ảnh. Evidence TTL/single-use khác retention; không hold-approver/finite-preservation fields trong main. Kiểm quyền và retention là service policies, xem [security](../security/overview.md).

## Thu tiền, đối soát và operation replay

```mermaid
erDiagram
    payment_transactions {
        Long id PK
        String receipt_number UK
        PaymentType payment_type
        PaymentMethod payment_method
        PaymentStatus status
        BigDecimal amount
        String external_reference
        Long parking_session_id FK, UK
        Long subscription_payment_id FK, UK
        Long collected_by_account_id FK
        Long gate_shift_assignment_id FK
        Long reconciled_by_account_id FK
        Instant paid_at
        Instant reconciled_at
        String reconciliation_note
    }
    subscription_payments {
        Long id PK
        boolean pending_payment
        Long payment_transaction_id FK, UK
    }
    parking_operations {
        Long id PK
        String actor_username
        String operation_type
        String operation_key
        String payload_hash
        ParkingOperationStatus status
        String response_json
        Instant created_at
        Instant updated_at
    }
    parking_sessions o|--o| payment_transactions : parking_session_id
    subscription_payments o|--o| payment_transactions : subscription_payment_id
    payment_transactions o|--o| subscription_payments : payment_transaction_id
    system_accounts ||--o{ payment_transactions : collected_by_account_id
    system_accounts o|--o{ payment_transactions : reconciled_by_account_id
    gate_shift_assignments o|--o{ payment_transactions : gate_shift_assignment_id
```

SQL payment CHECKs: đúng một session/subscription source, amount>=0; source/actor FK RESTRICT, shift SET NULL. Operation UK là **composite** `(actor_username,operation_type,operation_key)`, không từng field riêng. ParkingOperation không FK tới session/account; response JSON là replay result, không domain relation. Payment enum VOIDED/REFUNDED không chứng minh có refund workflow. Desktop outbox nằm ngoài DB, không nằm trong ERD.

## Legacy và giới hạn kiểm chứng

Mô tả V1–V6 Flyway, `gate_evidence` singular, scalar owner IDs/hold/processing-outage fields là **legacy nhánh trước**, không được vẽ như schema main. Main có scripts thủ công và Hibernate update, không Flyway dependency/directory. `manual_override_allowed` là cột legacy được phase4 SQL giữ lại nhưng không entity field hoặc input authorization hiện tại. `parking_card_code_migration_backup` là bảng backup của script chuyển card identifier, không entity ứng dụng. Không suy DB thật không có bảng khác.

Đã đối chiếu source/SQL tĩnh; chưa render Mermaid, chạy SQL/migrations/test/build hoặc introspect database trong lượt docs-only này. Cardinality/UK/FK source không chứng nhận deployment/concurrency/backup-restore.
