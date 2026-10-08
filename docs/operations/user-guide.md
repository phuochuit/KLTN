# Hướng dẫn sử dụng Web và trạm gác

[Setup / troubleshooting](setup-and-troubleshooting.md) · [Phạm vi nghiệp vụ](../requirements/README.md) · [Traceability](../requirements/traceability.md) · [Contracts](../api/contracts.md)

## Cách đọc và an toàn vận hành

Hướng dẫn mô tả **UI/source quan sát**, không ghi nhận thao tác runtime đã kiểm thử. Các nghiệp vụ NV01–NV08 là yêu cầu nguồn, không đồng nghĩa mọi validation/approval/payment/authorization đã được UI thực hiện. R06/R07 còn pending review. Dùng dữ liệu thử, không dữ liệu người thật khi chưa có consent/privacy và deployment policy được duyệt.

Ban quản lý quản trị hồ sơ/giá/thẻ/ngoại lệ; nhân viên gate kiểm xe/người/evidence và lượt vào-ra; cư dân/thành viên chỉ dùng xe được cấp quyền, khách theo luồng khách. Đây là trách nhiệm nghiệp vụ, **không role matrix đã enforced**: source có Web login/in-memory ADMIN nhưng public API/media/H2 và client-trusted face/manual flags. Không cung cấp password; lấy credentials qua người quản trị. Xem [security](../security/overview.md).

## Web quản trị

Mở URL Web được cấu hình (local mặc định `http://localhost:8080`) sau setup. [Navigation template](../../spring-web/src/main/resources/templates/fragments.html) có **Dashboard**, **Đăng ký hồ sơ**, **Hộ gia đình**, **Phương tiện**, **Thẻ & gói**, **Bảng giá**, **Hạn mức**, **Sơ đồ bãi**, **Lịch sử**, **Đăng xuất**. Không invent màn hình users/roles/password-change/payment-provider hoặc alert queue từ yêu cầu đề cương.

### Hồ sơ cư dân, hộ và xe

1. **Đăng ký hồ sơ** (`/registrations`): [form](../../spring-web/src/main/resources/templates/registrations.html) chia thông tin hộ/căn hộ, người đại diện và phương tiện. Nhập hồ sơ và giấy tờ cần thiết; tải ảnh đăng ký hoặc **Mở camera** → **Chụp ảnh**. Form ghi tối đa10MB mỗi ảnh.
2. QR CCCD: **Quét từ ảnh đã chọn** hoặc **Mở camera quét QR** → **Chụp và đọc QR**; kiểm lại dữ liệu được điền trước lưu. QR autofill **không** là xác minh ảnh CCCD hoặc consent/identity assurance.
3. Bấm **LƯU HỒ SƠ VÀ CHUYỂN SANG ĐĂNG KÝ GÓI**. Handler/test evidence có redirect sang cards; không proof hồ sơ được phê duyệt/payment settled theo toàn bộ NV01.
4. **Hộ gia đình** (`/households`): [form](../../spring-web/src/main/resources/templates/households.html) có **Lưu hộ gia đình**, thêm/cập nhật thành viên, **Chọn người cần chỉnh sửa**, **Xóa form / thêm mới**, **Chỉnh sửa** và upload/camera ảnh. Bổ sung member theo đúng hộ, kiểm thông tin/ảnh sau save.
5. **Phương tiện** (`/vehicles`): [form](../../spring-web/src/main/resources/templates/vehicles.html) có lựa chọn hộ, **Chủ đăng ký**, người được phép, thông tin plate/giấy đăng ký/nhiên liệu/thông số; **Đăng ký phương tiện** và lựa chọn phương tiện cần chỉnh sửa. Cấp quyền **theo từng xe**, cùng hộ không tự được dùng tất cả xe.
6. **Hạn mức** (`/settings`): [form](../../spring-web/src/main/resources/templates/settings.html) nhập số xe hai bánh/ô tô tối đa mỗi hộ và **Lưu hạn mức**; README còn mô tả override riêng tại hộ. Full quota enforcement/concurrent validation chưa verified; không coi form lưu thành công là toàn policy đã enforced.

Web camera chạy trong browser và cần quyền/thiết bị phù hợp; khác camera trên máy ANPR của Desktop. Browser compatibility, camera permission, editing/autofill runtime và full validation chưa được thao tác trong R08. Ba phép đối chiếu/liveness/manual approval trong [NV01](../requirements/nv01-registration.md) là nghĩa vụ còn cần kiểm chứng, không workflow đã hoàn chỉnh bởi QR/face upload.

### Thẻ, gói, giá và payment record

[Thẻ & gói](../../spring-web/src/main/resources/templates/cards.html) (`/cards`): điền **Mã thẻ**, **Xe đã đăng ký**, **Hình thức**, **Ngày bắt đầu**, **Số ngày**, **Trạng thái** → **Tạo / gia hạn thẻ**. Có **Vé lượt** và **Gói 30 ngày**; số ngày default30, input cho1–366. Kiểm danh sách thẻ: vehicle, validity, subscription fee/status, và **Lịch sử thu tiền gói**.

Handler evidence tạo subscription-payment record khi monthly card được lưu; đây không proof thu tiền thật, gateway/provider, receipt, debt approval hay refund. Không có payment-confirmation bước riêng đã được chứng minh ở Desktop exit. Activation payment-only vs debt approval và policy expiry vẫn unresolved; đối soát với người quản trị theo [NV02](../requirements/nv02-cards-passes-pricing.md).

[Bảng giá](../../spring-web/src/main/resources/templates/pricing.html) (`/pricing`) có **Cập nhật giá**; đối chiếu loại xe và các giá ban ngày/đêm/qua đêm/monthly, khung giờ theo form trước lưu. Giá không bất biến và chưa có historical-rule snapshot proof.

**README defaults, không survey authority hoặc runtime verification:** monthly xe đạp/xe đạp điện100.000đ; xe máy/xe máy điện150.000đ; mô tô lớn300.000đ; ô tô4–5 chỗ1.500.000đ,6–7 chỗ1.700.000đ,8–9 chỗ1.800.000đ. Giá lượt README: xe máy5.000/8.000/10.000đ (day/night/chu kỳ24h có overnight), xe đạp2.000/3.000/5.000đ; ô tô40.000đ/4h đầu, thêm20.000đ/2h hoặc phần lẻ, tối thiểu100.000đ mỗi đêm qua00:00. Kiểm bảng giá hiện tại và preview thực tế, không dùng các số này làm chứng từ thu tiền hoặc áp tùy ý cho mọi visitor/type.

Service evidence monthly eligibility dựa ngày vào và trạng thái card hiện tại; khác lời mô tả “còn hạn lúc ra”. Không hứa mọi lượt resident được0đ. Registered vehicle dùng official registry type; visitor/default type có fallback, nên giá khách phải kiểm theo result chứ không theo AI alone.

## Chuẩn bị Desktop

Source chính: [MainForm](../../desktop-winform/MainForm.cs):78–338,386–394. Header có **Spring API**, **ANPR API**, **Kiểm tra**, **Sơ đồ bãi**, **MỞ BARRIER**, **ĐÓNG BARRIER**. Defaults8080/8001; nhập đúng destinations rồi bấm **Kiểm tra**. “Spring + ANPR đang hoạt động” là kết quả health checks của client, không E2E/production certification.

Chọn tab **XE VÀO** hoặc **XE RA** trước **Nhận dạng ảnh**/**Nhận dạng video**: nhận dạng điền plate vào tab đang chọn, cập nhật preview/type/confidence/frame. Loại AI read-only; plate có thể chỉnh theo evidence. Low confidence hoặc plate rỗng có thể **tự bật** checkbox **Xác nhận thủ công khi AI cảnh báo** (OCR<0.45 hoặc detection<0.25). Đừng xem checkbox là approval đã được người có quyền cấp; kiểm trạng thái trước submit.

README có dữ liệu mẫu plate `59A112345`/card `CARD001`, `51H88888`/`CARD002`; đây là example claim, không bảo đảm DB hiện tại đã seed. Kiểm lookup trước dùng.

## Cư dân vào — quan sát UI, không chứng nhận đầy đủ NV03

1. Chọn **XE VÀO**, nhận dạng ảnh/video hoặc nhập **Biển số AI tự điền**, kiểm lại biển và loại được hiển thị. Nhập **Mã thẻ (không bắt buộc)** nếu có. Nhận dạng entry có thể tự gọi lookup.
2. Bấm **TRA CỨU CƯ DÂN**; xem owner/hộ/card/pass/message và chọn **Người đang điều khiển** trong danh sách authorized members trả về.
3. Bấm **CHỤP & XÁC THỰC KHUÔN MẶT** khi member có ảnh đăng ký. Desktop gửi URL ảnh tới ANPR camera; chỉ decision `PASS` làm faceVerified=true. Chọn member khác sau verify làm flag không còn hợp lệ cho member đó khi request được tạo; lookup cũng reset face state.
4. Kiểm flags, member và evidence rồi **XÁC NHẬN XE VÀO**. Result hiển thị mã lượt, plate/type/owner/card/status/time/fee.
5. Source gọi auto-open barrier khi response không Warning **hoặc manual được tick**; đồng bộ map event, copy plate/card sang exit, clear guest capture và có thể clear entry form sau2 giây. Barrier label/timer đóng sau5 giây là **mô phỏng**, không device integration.

**Cảnh báo quan trọng:** missing/mismatched card hoặc expired monthly pass có thể tạo OPEN và warning tại backend, trong khi UI không auto-open nếu chưa manual. Warning không đồng nghĩa “không có session”; kiểm history/lookup trước submit lại. Unknown/inactive cards có nhánh reject riêng. Entry rights có legacy/manual bypass và client flag trust; source requirement không tự cho phép dùng chúng để vượt xác minh. Xem [NV03](../requirements/nv03-resident-entry.md).

## Cư dân ra

1. Chọn **XE RA**, nhận dạng/kiểm plate, nhập card nếu có.
2. **TRA CỨU NGƯỜI ĐƯỢC PHÉP** → chọn **Người đang lấy xe**. Người này có thể khác người vào nếu được cấp quyền cho đúng xe theo yêu cầu nguồn.
3. Dùng **CHỤP & XÁC THỰC KHUÔN MẶT** với ảnh đăng ký, kiểm decision/score/message. UI có button này nhưng **resident exit backend chưa được chứng minh bắt buộc face/driver rights**, nên không gọi workflow là secure enforcement.
4. Bấm **XEM TRƯỚC PHÍ** và đọc result. **XÁC NHẬN XE RA** yêu cầu `_lastPreview` phù hợp normalized plate; nếu thiếu, UI báo “Hãy bấm Xem trước phí trước khi xác nhận.” Thay plate phải preview lại. Preview không là proof đã thanh toán và không giữ giá cố định; confirm tính lại.
5. Khi đủ evidence và việc thu/miễn phí được xử lý theo policy quản trị, confirm. Result code dùng `COMPLETED`, nguồn có `CLOSED`; không equate như acceptance đã đạt. UI auto-open theo cùng Warning/manual condition, refresh map/reset preview và có thể clear form.

Không có UI payment settlement/receipt/waiver audit đã được xác minh ở đường confirm. Manual open barrier không tạo/đóng session. Repeat confirm, races, authorized alternate driver và payment-before-close cần kiểm thử riêng theo [NV04](../requirements/nv04-resident-exit.md).

## Khách vãng lai

- Entry: cùng tab **XE VÀO**, nhận dạng/nhập plate, lookup xem **KHÁCH VÃNG LAI**, dùng **CHỤP ẢNH KHÁCH VÃNG LAI** rồi kiểm preview và confirm entry. Source gửi captured base64 cùng request để lưu ảnh theo lượt; không có visitor enrollment/special KHÁCH selector đã observed.
- Capture button không chứng minh backend bắt buộc ảnh: entry source cho phép optional capture. Quy trình nguồn NV05 đòi evidence phải được đối chiếu riêng, không suy ra thiếu capture là được phép.
- Exit: **XE RA** → lookup lấy entry-face path → **CHỤP & XÁC THỰC KHUÔN MẶT** so với ảnh vào → preview/confirm. Backend guest face gate có điều kiện khi entry-face path tồn tại, trừ override; client flag không proof genuine verification.
- Fee/payment/waiver/lost-card/ownership cần policy người quản trị và evidence; không có payment-provider hoặc lost-card wizard được chứng minh. Không cấp cư dân/card để “sửa” thiếu evidence của khách. Xem [NV05](../requirements/nv05-visitor-parking.md).

## Phân loại xe

Desktop hiển thị type AI và confidence, không official taxonomy editor. Nhân viên kiểm giấy tờ và cập nhật xe qua Web theo quyền quản trị; registered official type được Spring sử dụng. Fallback theo hình dạng biển/heuristics không bảo đảm đúng six-class/EV detection. Không có correction reason/history hoặc charging screen được chứng minh. [NV06](../requirements/nv06-vehicle-classification.md) giữ hai nhánh A/B; parking slot/xe điện không là charging rights/session.

## Sơ đồ bãi và điều phối

[ParkingMapForm](../../desktop-winform/ParkingMapForm.cs):41–140 mở từ **Sơ đồ bãi**, có filters **Tầng**, **Khu vực**, **Loại xe**, **Làm mới**. Map inline cũng mở [SlotDetailDialog](../../desktop-winform/SlotDetailDialog.cs) khi chọn ô. Đây là dữ liệu database, không physical occupancy sensor verified.

| Tab/dialog | Thao tác observed |
| --- | --- |
| **Gán xe cư dân** | Chọn xe chưa có ô → **LƯU GÁN XE**; **HỦY GÁN (TRẢ VỀ VỊ TRÍ TRỐNG)**. UI/service có one-vehicle/one-slot precheck, không race guarantee. |
| **Đỗ nhờ (Hàng xóm)** | Plate, thời gian1/2/4/8/12/24/48h (default2), ghi chú → **XÁC NHẬN CHO ĐỖ NHỜ**; **HỦY THIẾT LẬP ĐỖ NHỜ** khi có borrowing. Status/overdue cần refresh, không proof timer auto-sync. |
| **Điều phối xe vừa vào** | Chọn recent unassigned session → **ĐIỀU PHỐI VÀO Ô NÀY**; không tạo entry mới và không reader/camera trigger thật. |
| **Khóa / Giải phóng** | Block/unblock ô bảo trì; **GIẢI PHÓNG Ô (XÓA TOÀN BỘ GÁN XE & ĐỖ NHỜ)** thay slot links, không exit-confirm/settlement. Kiểm đúng ô trước thao tác có ảnh hưởng dữ liệu. |

Web [Sơ đồ bãi](../../spring-web/src/main/resources/templates/slots.html) có cùng nhóm điều phối và thêm **Tạo ô lẻ**, **Tạo sơ đồ nhanh** → **Tạo sơ đồ ngay**, chọn tầng/khu/prefix/range/type. Backend batch count1–100 và skip existing đã được ghi trong evidence; runtime field parity/dispatch constraints còn cần kiểm chứng. Inline map refresh có catch bỏ qua lỗi; sơ đồ không update không có nghĩa thao tác đã rollback.

## Lịch sử, dashboard và doanh thu

[Lịch sử](../../spring-web/src/main/resources/templates/sessions.html) (`/sessions`) trình bày sessions vào/ra. [Dashboard](../../spring-web/src/main/resources/templates/dashboard.html) (`/`) có filter **Theo tháng**, **Theo năm**, **Theo khoảng ngày** → **Áp dụng**, chart và recent-session rows; detail/reporting source đã ghi tại traceability. Kiểm records gốc khi totals khác kỳ vọng.

Revenue code tổng phí session COMPLETED theo exitTime và subscription payments theo paidAt, **không proof visitor đã trả tiền/settlement**. Không có known-total reconciliation/export-permission/runtime validation; không invent nút export/alert-resolution/trend-prediction. Tách revenue, parked sessions, registry vehicle count và slot occupancy. Xem [NV08](../requirements/nv08-reporting.md).

## Lỗi và can thiệp thủ công

- Đọc message/status, kiểm URL/services với **Kiểm tra**, xem logs theo [troubleshooting](setup-and-troubleshooting.md). Lookup lỗi hiển thị thông tin cư dân lỗi; không chọn member/confirm khi evidence không chắc.
- Recognition quality thấp: kiểm plate/giấy tờ và capture lại; manual checkbox có thể tự được bật. Manual trong client là flag, không documented reason/operator/audit form hoặc permission proof.
- **MỞ BARRIER/ĐÓNG BARRIER** chỉ đổi state mô phỏng; không thay entry/exit/payment. Không dùng chúng như recovery transaction.
- Timeout/lost response: lookup/history trước retry, vì commit có thể đã xảy ra. Không claim idempotency, offline queue hay safe resync.
- Với liveness/ảnh giả, authorization mismatch, mất thẻ, payment/waiver hoặc evidence thiếu: chuyển quản trị, không coi “bấm thủ công” là chấp nhận theo NV07.

## Giới hạn chưa xác minh

Chưa có UI/runtime exercise trong R08: browser webcam/QR/editing, full quota/validation, real camera/AI accuracy, mandatory resident exit rights, settlement/refund/receipt, audit/privacy/retention, concurrency/recovery/idempotency, exports/hardware và production deployment. Standalone demo ba ảnh không lấp các gaps gate. Các yêu cầu còn mở tại [NV07](../requirements/nv07-exceptions-recovery.md), [traceability](../requirements/traceability.md) và [testing strategy](../testing/strategy.md); không nâng status thành implemented hoặc PASS từ hướng dẫn này.
