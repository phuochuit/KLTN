# Vận hành bảo mật Giai đoạn 4

Giai đoạn 4 triển khai Gate Evidence, lưu trữ ảnh riêng tư, chống replay và manual override có kiểm soát. Spring là nguồn tin cậy cho quyết định AI; Desktop không được tự khai kết quả xác thực.

## 1. Luồng mục tiêu

```text
Desktop --JWT--> Spring --service token--> FastAPI
                         |
                         +--> private evidence storage
                         +--> gate_evidences
```

1. FastAPI trả metadata và ảnh kết quả cho Spring.
2. Spring kiểm tra contract, chuẩn hóa kết quả và áp dụng ngưỡng quyết định.
3. Spring lưu asset riêng tư, hash và Gate Evidence gắn với tài khoản/phiên Desktop.
4. Desktop chỉ nhận UUID evidence và metadata cần hiển thị.
5. Khi xác nhận xe vào/ra, Desktop gửi evidence UUID; Spring consume evidence trong cùng transaction nghiệp vụ.

## 2. Ràng buộc evidence

Evidence chỉ hợp lệ khi:

- Có trạng thái `READY`, chưa hết hạn và chưa bị consume.
- Thuộc đúng tài khoản và `desktop_sessions.session_id` trong JWT.
- Đúng chiều `ENTRY` hoặc `EXIT`.
- Đúng loại `VEHICLE`, `FACE` hoặc `CAPTURE` theo nghiệp vụ.
- Khớp biển số chuẩn hóa và thành viên được chọn nếu có.
- Asset còn tồn tại và SHA-256 khớp metadata.

Consume phải atomic. Hai request đồng thời hoặc replay cùng UUID chỉ một request được phép thành công.

## 3. Schema

Migration MySQL: `scripts/database/security-phase4-evidence-mysql.sql`.

### `gate_evidences`

- UUID do Spring sinh; UUID từ FastAPI chỉ lưu ở `upstream_evidence_id` để truy vết.
- Gắn tài khoản, Desktop session, ca trực, thành viên và parking session.
- Lưu biển số/loại xe/confidence, face decision/similarity/threshold, model version và camera/frame.
- Có request/response hash, thời gian tạo/hết hạn/consume và optimistic `version`.
- Liveness được chuẩn bị trong schema với trạng thái mặc định `NOT_EVALUATED`; chưa được xem là đã triển khai chống giả mạo.

### `gate_evidence_assets`

- Asset role: `ANNOTATED_VEHICLE`, `REALTIME_FACE`, `GUEST_ENTRY_FACE` hoặc loại mở rộng.
- Chỉ lưu storage key nội bộ, SHA-256, MIME thực, kích thước byte/pixel.
- Không lưu đường dẫn tuyệt đối hoặc URL public.

### `manual_override_events`

- Bắt buộc reason code và mô tả.
- Gắn account, Desktop session, ca trực, evidence và parking session.
- Ghi cả override được chấp nhận lẫn bị từ chối.

## 4. Private storage

Biến môi trường mục tiêu:

```text
PARKING_PRIVATE_STORAGE_PATH=<absolute-private-directory>
PARKING_EVIDENCE_RETENTION_DAYS=30
PARKING_BIOMETRIC_RETENTION_DAYS=30
PARKING_EVIDENCE_TTL_MINUTES=10
PARKING_EVIDENCE_REQUIRED=true
PARKING_EVIDENCE_CLEANUP_ENABLED=false
PARKING_EVIDENCE_CLEANUP_CRON=0 0 3 * * *
```

Production phải đặt storage ngoài source, static resources và Web root. Tên file là UUID; ghi file tạm rồi atomic move; kiểm tra MIME từ nội dung, kích thước, pixel và path traversal.

Không ghi base64, JWT, token hoặc đường dẫn tuyệt đối vào database/audit.

## 5. Chuyển ảnh cư dân sang private

Ảnh hiện có dạng `/uploads/residents/{uuid}.jpg`. Migration file và database phải tách thành hai bước có manifest:

1. Dừng thao tác cập nhật ảnh cư dân.
2. Sao lưu database và thư mục `spring-web/uploads/residents`.
3. Export CSV có hai cột `id,registration_face_image_path`, sau đó chạy
   `scripts\database\migrate-resident-images-private.cmd` để lập manifest và SQL được sinh tự động.
4. Copy sang `<private-root>/residents/`; không xóa nguồn. Reference mới có dạng
   `resident:{uuid}.jpg` hoặc `resident:{uuid}.png` theo MIME thực, không phải URL hoặc đường dẫn tuyệt đối.
5. Xác minh số lượng, kích thước và SHA-256 toàn bộ file.
6. Review manifest và SQL được sinh; transaction tạo
   `resident_image_private_migration_backup` rồi cập nhật DB sang reference `resident:{uuid}.{jpg|png}`.
7. Restart Spring, kiểm tra Web quản trị và face verification qua luồng nội bộ.
8. Bỏ public `/uploads/**`.
9. Chỉ xóa file nguồn sau thời gian rollback đã thống nhất.

Rollback: phục hồi giá trị đường dẫn từ manifest/backup DB, bật lại cấu hình cũ tạm thời và giữ nguyên bản private để điều tra. Không rollback bằng cách bỏ xác thực API ảnh private.

Ví dụ (không chứa credential và không tự kết nối Aiven):

```cmd
scripts\database\migrate-resident-images-private.cmd ^
  --input-csv C:\secure\resident-images.csv ^
  --legacy-root D:\parking-data\uploads\residents ^
  --private-root D:\parking-private\residents ^
  --manifest C:\secure\resident-image-manifest.csv ^
  --sql-output C:\secure\resident-image-update.sql
```

Utility dừng và không sinh SQL nếu bất kỳ dòng nào lỗi. SQL đầu ra chỉ được chạy sau khi
backup Aiven, review manifest và xác minh checksum. Không stage CSV, manifest, SQL sinh ra
hoặc ảnh vào Git.

## 6. Contract face nội bộ

Desktop chỉ gửi `familyMemberId`. Spring tự đọc ảnh đăng ký private và gửi bytes cho FastAPI:

- Upload: multipart `registered` + `realtime`.
- Camera: multipart/JSON nội bộ chứa ảnh đăng ký và camera index; không dùng URL public.
- Capture khách: không cần ảnh đăng ký.

Web quản trị xem ảnh qua controller có RBAC; mọi lần xem/tải tạo security audit.

## 7. Manual override

Xử lý ngoại lệ không còn là checkbox tự do hoặc quyền phải cấp riêng cho từng tài khoản. Yêu cầu:

- Tài khoản có vai trò `GATE_STAFF`, đang hoạt động và có ca trực hiện hành.
- Có vehicle evidence và face evidence thuộc đúng tài khoản, Desktop session và chiều nghiệp vụ.
- Reason code hợp lệ và mô tả không rỗng.
- Desktop chỉ bật nút **Chỉnh sửa kết quả AI** sau khi đã có evidence.
- Hộp thoại xác nhận rõ lý do, biển số và loại xe sau khi kiểm tra ảnh camera.
- Audit lưu kết quả AI ban đầu, dữ liệu sau chỉnh sửa, tài khoản, phiên, ca và evidence.
- Cột `system_accounts.manual_override_allowed` chỉ được giữ để rollback schema cũ; ứng dụng không đọc cột này.

## 8. Retention

Cleanup chạy theo batch, idempotent và ghi audit. Quy trình:

1. Chọn evidence hết hạn, chưa bị legal hold và không thuộc lượt cần giữ.
2. Xóa asset private an toàn.
3. Đánh dấu `deleted_at`; không tái sử dụng evidence.
4. Ghi số lượng thành công/thất bại.

Retention evidence không thay thế retention security audit.

## 9. Thứ tự triển khai

1. Entity/repository/migration H2 và MySQL.
2. Private storage + test path traversal/hash/atomic write.
3. Parse response FastAPI và tạo evidence trong Spring.
4. Contract face bytes nội bộ và private ảnh cư dân.
5. Desktop giữ/gửi evidence UUID.
6. Consume evidence trong transaction xe vào/ra.
7. Xử lý ngoại lệ có role/ca trực/evidence/lý do/audit trước-sau.
8. API xem evidence + audit truy cập.
9. Retention cleanup.
10. Migration Aiven và ảnh local sau preflight/backup.

## 10. Tiêu chí nghiệm thu

- Sửa `faceVerified` trong payload không thể qua nghiệp vụ.
- Replay, evidence sai phiên, sai chiều, hết hạn hoặc sai member đều bị từ chối.
- Ảnh cư dân/evidence không có URL public.
- Mỗi lần xem ảnh có audit.
- Xử lý ngoại lệ bắt buộc nhân viên cổng, ca trực, evidence và lý do.
- Cleanup không xóa nhầm evidence đang được giữ.
- Spring/Python/WinForms test sạch; MySQL postcheck đạt.
- ADR-0003 chỉ chuyển `Accepted` sau khi toàn bộ tiêu chí đạt.

## 11. Trạng thái triển khai

Migration Aiven đã chạy thành công sau full backup:

- Backup trước thay đổi được lưu ngoài repository, SHA-256 `e2485fe47313e843f286ece71b2aac86f9cac9535aaba4f30159040927703641`.
- Đã tạo `gate_evidences`, `gate_evidence_assets`, `manual_override_events`; cột legacy `system_accounts.manual_override_allowed` được giữ nhưng không còn dùng.
- Đã thêm `system_accounts.full_name` và bốn cột lưu kết quả AI trước/sau chỉnh sửa. Backup bổ sung SHA-256 `e1cbd64f18719fbccd22f20cbc5402094744943391703d36458e20b0e4bb7e3b`.
- Đã thêm RBAC động (`security_roles`, `security_role_permissions`), hồ sơ nhân viên và mã tự sinh theo tiền tố vai trò. Backup trước migration: `parking_anpr-before-dynamic-rbac-20261020-141059.sql`, SHA-256 `62c28f6bc6f253f40b33bf7ce5ad3e78bf84abff14bbcf7cfa1a4af080607f86`.
- Backfill Aiven giữ nguyên 2 tài khoản: `admin` → `QT-001`, `dphuoc` → `GC-001`; cột enum `role` cũ được giữ để rollback.
- Hai ảnh resident cũ thực tế là PNG, dù tên legacy đuôi `.jpg`; migration giữ nguyên bytes và chuyển sang `resident:{uuid}.png`.
- Đã xác minh 2/2 file private khớp size và SHA-256; 0 reference legacy còn lại.
- `resident_image_private_migration_backup` lưu mapping gốc để rollback.
- File ảnh nguồn legacy chưa bị xóa.

Nghiệm thu tự động cuối đạt: Spring 108 tests (bao gồm correction end-to-end, RBAC, hồ sơ/mã nhân viên và dashboard permission), Python 9 tests, WinForms Release 0 warning/0 error. Runtime Spring/ANPR health đều `UP`.

Đăng nhập Web quản trị và Desktop JWT bằng tài khoản hiện hành đã đạt; Desktop → Spring → ANPR không lỗi. Smoke test tài khoản, hồ sơ bắt buộc, mã nhân viên, vai trò/quyền, menu theo quyền và dashboard bằng `dphuoc` đã đạt trên Aiven. Private image/evidence RBAC-audit và correction được phủ bởi integration tests. Không commit/push. ADR-0003 đã chuyển `Accepted`.
