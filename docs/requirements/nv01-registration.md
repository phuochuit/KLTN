# NV01 — Đăng ký hộ, người, xe và quyền sử dụng

[Phạm vi nghiệp vụ](README.md) · [NV02](nv02-cards-passes-pricing.md) · [NV06](nv06-vehicle-classification.md)

## Mục tiêu, tác nhân và evidence

**Yêu cầu nguồn:** tạo hồ sơ liên kết căn hộ/hộ, cư dân/thành viên, xe và thẻ; xác minh ảnh và quyền sử dụng từng xe trước khi cho phép qua cổng. Ban quản lý xử lý đăng ký/duyệt; cư dân/chủ xe/thành viên cung cấp dữ liệu. AI/camera hỗ trợ xác minh, không thay người duyệt.

Nguồn: `../../evidence/qa/proposal-without-ev-charging-final/preview.pdf` pp.5–6; `../../evidence/qa/proposal-with-ev-charging/page-06.png` (trang 6/18, inputs, Bước 1–9, exceptions/output/acceptance). Trang mở đầu NV01 p.5 của variant ảnh chưa inspect. Không chốt authority; không có phê duyệt biến quy tắc còn tranh chấp thành quyết định nghiệp vụ.

## Điều kiện trước, dữ liệu vào và ra

- Căn hộ có thật; người đăng ký được thông báo mục đích xử lý và đồng ý theo quy định nguồn. Không suy diễn đây là kết luận đã tuân thủ pháp luật.
- Input: căn hộ, thông tin cần từ căn cước, quan hệ gia đình, ảnh giấy tờ/ảnh đăng ký/ảnh realtime và giấy đăng ký xe.
- Output: hồ sơ hộ-người-xe, danh sách người được dùng từng xe, card/pass và kết quả duyệt. Hồ sơ `ACTIVE` khi kiểm tra đạt hoặc duyệt thủ công có lý do; **nguồn NV01 nói thẻ/gói chỉ hoạt động sau thanh toán**.

## Luồng chính theo nguồn

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

Bước 9 classification-only lấy từ PDF no-EV p.6; bước có quyền sạc từ ảnh p.6. Giữ cả hai, không mặc nhiên cấp quyền sạc khi đăng ký xe điện.

## Ngoại lệ và xử lý thủ công

1. Ảnh không đạt hoặc ba so sánh mâu thuẫn: chụp lại; vẫn không đạt thì chờ xác minh, không tự coi AI thành công.
2. Plate/card đã tồn tại hoặc giấy tờ không khớp: không kích hoạt, tạo cảnh báo và kiểm tra.
3. Người nhà chưa chứng minh quan hệ/quyền: lưu hồ sơ nhưng không cấp quyền qua cổng.
4. Nguồn cho phép manual approval có lý do để hồ sơ `ACTIVE`; chưa đủ evidence về người có thẩm quyền, form/audit và threshold thực tế. Không biến `manualOverride` của entry thành quy trình duyệt đăng ký.

## Quy tắc, trạng thái và dữ liệu lưu

- Quyền gắn người với xe cụ thể, không chỉ hộ; NV03/NV04 phải dùng quyền đó.
- Lưu hồ sơ, links hộ-xe-người, face features, match scores, image files, người duyệt và change history (nguồn p.6). Retention/encryption implementation chưa xác minh.
- Không tạo duplicate; reverse lookup từ card/plate phải tới đúng căn hộ, xe và authorized members.
- `ACTIVE` ở đây là thuật ngữ nguồn cho hồ sơ/thẻ-gói; không khẳng định actual entity đều dùng cùng enum.
- **Xung đột activation:** NV02 p.7 cho phép ACTIVE khi số tiền hợp lệ **hoặc phê duyệt công nợ**. Chưa chốt công nợ là ngoại lệ của NV01 hay revision yêu cầu.

## Quan sát implementation, claims và unknowns

| Evidence | Quan sát / giới hạn |
| --- | --- |
| `spring-web/src/main/java/vn/edu/parking/web/ParkingApiController.java:58–101` | Lookup vehicle/card, active authorized family members và pass status; code lookup dùng Household/FamilyMember, không chứng minh toàn registration workflow |
| `spring-web/src/main/java/vn/edu/parking/service/ParkingService.java:47–49,397–417` | Entry gọi `verifyDriver`: member phải active và thuộc authorized members; thiếu ảnh hoặc faceVerified=false bị chặn trừ manual override. Legacy nhánh không chọn member và không ai có ảnh có thể bypass; không phải fulfillment ba đối chiếu |
| Cùng service `:397–417` | Client `faceVerified` được dùng; không thấy helper dùng `faceSimilarity` để tự quyết threshold. Không chứng minh tính xác thực AI evidence/liveness |
| `README.md:5–9,78–86`; `evidence/archive/PROJECT_GAPS.md:3–12` | Claims QR autofill/không dùng CCCD-photo so mặt, gaps liveness. Khác yêu cầu nguồn; không được quảng bá integrated three-photo verification |
| `AdminController.java`, `CccdQrController.java`, `ResidentImageStorage.java` | Indexed, chưa đủ body/form/security mapping để xác nhận dedup/consent/approval/media lifecycle |
| `face-verification-demo/app.py:28,207–223` | Demo compare độc lập mới indexed; model resource chung không chứng minh gate tích hợp |

## Phụ thuộc và tiêu chí chấp nhận

NV02 quản lý card/pass/payment; NV06 nhóm chính thức/quyền sạc theo variant; NV03/NV04 kiểm tra quyền; NV07 xử lý chất lượng/manual; NV08 tra cứu evidence. Các đặc tả liên quan đã có canonical page: [NV02](nv02-cards-passes-pricing.md), [NV03](nv03-resident-entry.md), [NV04](nv04-resident-exit.md), [NV05](nv05-visitor-parking.md), [NV06](nv06-vehicle-classification.md), [NV07](nv07-exceptions-recovery.md) và [NV08](nv08-reporting.md). Implementation evidence và authority/conflicts xem [traceability](traceability.md).

Checklist acceptance **theo nguồn, chưa có test PASS**:

- Tra ngược card/plate tới căn hộ, xe, authorized members; không duplicate.
- Ba phép so ảnh, điểm/ngưỡng và evidence được lưu; mâu thuẫn không tự kích hoạt.
- Member cùng hộ nhưng không được gán xe không có quyền cổng.
- Manual approval có lý do; card/pass activation giữ payment/debt conflict hiển thị.
- Dữ liệu giấy tờ chỉ lấy phần cần; privacy/retention cần kiểm chứng riêng.

Test: `spring-web/src/test/java/vn/edu/parking/ParkingFlowIntegrationTest.java` hiện diện, assertions đăng ký/quyền/consent chưa review. Không chạy test hoặc chứng nhận luồng đăng ký. Canonical specification này giữ unknowns, không xác lập rule mới.
