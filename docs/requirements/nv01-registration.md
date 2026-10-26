# NV01 — Đăng ký hộ, người, xe và quyền sử dụng

## Implementation main `abd6183` — đối chiếu tĩnh

Main giữ Household/FamilyMember/Vehicle và quyền theo từng xe. Registration/QR/media không chứng minh consent, approval hoặc ba ảnh; RegisteredFaceService tải ảnh theo member nhưng quyền active/per-vehicle được kiểm tại gate, không phải mọi face route. Entry còn nhánh legacy trả driver null khi xe không có authorized-member face profile; không áp kết luận “mọi cư dân bắt buộc chọn member” của nhánh feature cho main. Resident exit chưa bắt buộc face/exitMember ở mọi trường hợp. Xem [contract hiện hành](../api/contracts.md), [security](../security/overview.md) và [inventory tests main](../testing/strategy.md).

**Phạm vi lịch sử:** các bảng quan sát code/test bên dưới được giữ từ Security + WIP, không phải characterization main; ba override cũ/FaceVerificationPolicy và số test lịch sử không là contract hiện tại. Các luồng, yêu cầu và acceptance theo nguồn vẫn giữ nguyên, không hạ yêu cầu để khớp code.

[Phạm vi nghiệp vụ](README.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md)

## Mục tiêu và tác nhân

**Yêu cầu nguồn:** tạo hồ sơ liên kết căn hộ/hộ, cư dân/thành viên, xe và thẻ; xác minh ảnh và quyền sử dụng từng xe trước khi cho phép qua cổng. Ban quản lý xử lý đăng ký/duyệt; cư dân/chủ xe/thành viên cung cấp dữ liệu. AI/camera hỗ trợ xác minh, không thay người duyệt.

Trang này chứa đầy đủ yêu cầu đăng ký đã ghi nhận. Các vấn đề activation, ba ảnh và quyền sạc chưa được quyết định nằm tại [P01/P02/P04/P08](../decisions/README.md); việc viết tài liệu không tự phê duyệt chúng.

## Điều kiện trước, dữ liệu vào và ra

- Căn hộ có thật; người đăng ký được thông báo mục đích xử lý và đồng ý theo quy định nguồn. Không suy diễn đây là kết luận đã tuân thủ pháp luật.
- Input: căn hộ, thông tin cần từ căn cước, quan hệ gia đình, ảnh giấy tờ/ảnh đăng ký/ảnh realtime và giấy đăng ký xe.
- Output: hồ sơ hộ-người-xe, danh sách người được dùng từng xe, card/pass và kết quả duyệt. Hồ sơ `ACTIVE` khi kiểm tra đạt hoặc duyệt thủ công có lý do; **nguồn NV01 nói thẻ/gói chỉ hoạt động sau thanh toán**.

## Luồng chính yêu cầu

| Bước | Thao tác và dữ liệu |
| --- | --- |
| 1 | Tìm căn hộ; nếu chưa có, tạo tòa-tầng-căn, chủ hộ và trạng thái sử dụng |
| 2 | Nhập cư dân chính, chuẩn hóa và kiểm tra trùng định danh nội bộ/thông tin giấy tờ |
| 3 | Thêm vợ/chồng/con/thành viên khác, ghi quan hệ, thời hạn cư trú và trạng thái được dùng xe |
| 4 | Chụp/nhập ảnh chân dung căn cước; chỉ lấy trường cần thiết, không công khai toàn giấy tờ trên UI |
| 5 | Chụp ảnh hồ sơ và realtime; đúng một mặt, đủ sáng/nét, có dấu hiệu sống |
| 6 | Ba so sánh: CCCD–registration, registration–realtime, CCCD–realtime; lưu match score và decision threshold |
| 7 | Nhập xe: plate/chủ/loại giấy tờ/hãng-dòng/nhiên liệu-điện/dung tích hoặc thông số điện |
| 8 | Gán danh sách thành viên có quyền **cho từng xe**; cùng hộ không tự có quyền mọi xe |
| 9 | Cấp card/link xe/chọn gói 30 ngày, hiển thị ngày bắt đầu-hết hạn; variant charging thêm **quyền sạc** |

Bước 9 chỉ cấp quyền sạc nếu phạm vi mở rộng được duyệt ở P01; không mặc nhiên cấp quyền sạc khi đăng ký xe điện.

## Ngoại lệ và xử lý thủ công

1. Ảnh không đạt hoặc ba so sánh mâu thuẫn: chụp lại; vẫn không đạt thì chờ xác minh, không tự coi AI thành công.
2. Plate/card đã tồn tại hoặc giấy tờ không khớp: không kích hoạt, tạo cảnh báo và kiểm tra.
3. Người nhà chưa chứng minh quan hệ/quyền: lưu hồ sơ nhưng không cấp quyền qua cổng.
4. Nguồn cho phép manual approval có lý do để hồ sơ `ACTIVE`; chưa đủ evidence về người có thẩm quyền, form/audit và threshold thực tế. Không biến `manualOverride` của entry thành quy trình duyệt đăng ký.

## Quy tắc, trạng thái và dữ liệu lưu

- Quyền gắn người với xe cụ thể, không chỉ hộ; NV03/NV04 phải dùng quyền đó.
- Lưu hồ sơ, links hộ-xe-người, face features, match scores, image files, người duyệt và change history. Retention/encryption implementation chưa xác minh.
- Không tạo duplicate; reverse lookup từ card/plate phải tới đúng căn hộ, xe và authorized members.
- `ACTIVE` ở đây là thuật ngữ nguồn cho hồ sơ/thẻ-gói; không khẳng định actual entity đều dùng cùng enum.
- **Xung đột activation P02:** NV02 cho phép ACTIVE khi số tiền hợp lệ **hoặc phê duyệt công nợ**. Chưa chốt công nợ là ngoại lệ của NV01 hay thay đổi yêu cầu.

## Quan sát implementation, claims và unknowns

Đối chiếu tĩnh source main `abd6183` ngày 2026-10-24, không dùng backups/artifacts; không chạy ứng dụng hoặc tests. Các yêu cầu và acceptance ở trang này không đổi theo code. Contracts hiện hành tại [API](../api/contracts.md); assertions hiện diện và execution lịch sử được tách tại [testing](../testing/strategy.md).

| Evidence | Quan sát / giới hạn |
| --- | --- |
| [ParkingApiController](../../spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java) `lookup` | Lookup vehicle/card, active authorized family members và pass status; code lookup dùng Household/FamilyMember, không chứng minh toàn registration workflow |
| `ParkingService.enter`/`verifyDriver` + `AiProxyController` | Khi có member ID, kiểm active và quyền chính xe; thiếu ID vẫn được qua nếu không authorized member nào có ảnh, hoặc manual correction đã được authorize. Có ảnh cần PASS trừ override. Không khẳng định mọi resident entry bắt buộc chọn member; đây là gap so với yêu cầu NV01/NV03 |
| Spring → FastAPI face path | `RegisteredFaceService` đọc ảnh member server-side, `AiProxyController` proxy bằng `X-Parking-Service-Token`; evidence kiểm owner/Desktop sid/type/operation/TTL/member khi consume. Giữ decision upstream, không `FaceVerificationPolicy` hay liên kết face–vehicle/plate/session đầy đủ của feature. Legacy evidence-disabled/no-ID mode dùng client face flags/base64; mặc định tracked required=true |
| [Security](../security/overview.md) và [trạng thái](../implementation-status.md) | QR autofill/registration–camera pairwise không thay ba phép so ảnh hoặc liveness; chưa được quảng bá integrated three-photo verification |
| `AdminController.saveRegistration`/`saveVehicle`, `CccdQrController`, `ResidentImageStorage` | Combined registration upsert hộ/member/xe theo code/citizenId/plate, yêu cầu ảnh mặt/giấy xe, gán quyền member đăng ký; edit có cùng-hộ/duplicate/quota checks. QR trả raw + parsed PII và member lưu raw; resident ảnh private/re-encode JPEG, đọc qua permission-wide admin route. Không full consent/approval/dedup-concurrency/privacy proof |
| `ManualOverrideService.authorize` | Main dùng `AI_RESULT_CORRECT`, JWT/account/Desktop session/ca active, đủ VEHICLE+FACE evidence và reason text10..500; codes/corrections khác ba ngoại lệ `OVERRIDE_CREATE` của feature. Không thay quy trình duyệt đăng ký; chi tiết [NV07](nv07-exceptions-recovery.md) |
| [face-verification-demo/app.py](../../face-verification-demo/app.py) | Demo ba ảnh độc lập; model resource chung không chứng minh gate tích hợp |

## Phụ thuộc và tiêu chí chấp nhận

NV02 quản lý card/pass/payment; NV06 nhóm chính thức/quyền sạc theo variant; NV03/NV04 kiểm tra quyền; NV07 xử lý chất lượng/manual; NV08 tra cứu evidence. Các đặc tả liên quan đã có canonical page: [NV02](nv02-cards-passes-pricing.md), [NV03](nv03-resident-entry.md), [NV04](nv04-resident-exit.md), [NV05](nv05-visitor-parking.md), [NV06](nv06-vehicle-classification.md), [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md). Implementation evidence và authority/conflicts xem [traceability](traceability.md).

Checklist acceptance **theo nguồn, chưa có test PASS**:

- Tra ngược card/plate tới căn hộ, xe, authorized members; không duplicate.
- Ba phép so ảnh, điểm/ngưỡng và evidence được lưu; mâu thuẫn không tự kích hoạt.
- Member cùng hộ nhưng không được gán xe không có quyền cổng.
- Manual approval có lý do; card/pass activation giữ payment/debt conflict hiển thị.
- Dữ liệu giấy tờ chỉ lấy phần cần; privacy/retention cần kiểm chứng riêng.

Test: main `ParkingFlowIntegrationTest` có registration/authorized-list/QR subsets; class tắt evidence required, không chứng minh default gate face enforcement. `GateEvidenceConsumptionServiceTest` kiểm owner/sid/operation/member bằng mocks. Các test bắt buộc selected active member và full resident face-evidence binding của feature thuộc ledger **lịch sử**, không current-main coverage. [Testing strategy](../testing/strategy.md) tách assertions và execution; không chạy lại khi đồng bộ docs, không chứng nhận full consent/approval/three-way identity hoặc toàn luồng đăng ký.
