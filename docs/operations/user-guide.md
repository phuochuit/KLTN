# Hướng dẫn sử dụng Web và trạm gác

[Setup / troubleshooting](setup-and-troubleshooting.md) · [Phạm vi nghiệp vụ](../requirements/README.md) · [Traceability](../requirements/traceability.md) · [Contracts](../api/contracts.md)

## Cách đọc và an toàn vận hành

Hướng dẫn mô tả **UI/source main tại `abd6183`, đối chiếu tĩnh ngày2026-10-24**, không ghi nhận thao tác runtime đã kiểm thử. Các nghiệp vụ NV01–NV08 là yêu cầu, không đồng nghĩa mọi validation/approval/payment/authorization đã được UI thực hiện. Dùng dữ liệu thử, không dữ liệu người thật khi chưa có consent/privacy và deployment policy được duyệt. Source backup và kết quả nhánh Security không thay current-main verification.

Ban quản lý quản trị hồ sơ/giá/thẻ/ngoại lệ; nhân viên gate kiểm xe/người/evidence và lượt vào-ra; cư dân/thành viên chỉ dùng xe được cấp quyền, khách theo luồng khách. Source dùng tài khoản DB: Web session/CSRF và Desktop JWT, với permission grants và nhánh tương thích `ROLE_MANAGEMENT`/`ROLE_GATE_STAFF`; không coi role names chỉ là metadata. Không kế thừa nguyên matrix media/revenue của feature branch; cần kiểm route và actor thực tế. Gate Evidence thông thường phải do Spring tạo, đúng account/Desktop session/operation/type, chưa hết hạn/chưa consume; client flags không thay evidence khi `PARKING_EVIDENCE_REQUIRED=true`. Đây là source controls, không chứng nhận deployed role matrix hoặc suite đã PASS. Lấy credentials qua người quản trị, không dùng demo login. Xem [security](../security/overview.md).

## Web quản trị

Mở URL Web được cấu hình (local mặc định `http://localhost:8080`) sau setup và đăng nhập tài khoản DB. Tài khoản buộc đổi mật khẩu được chuyển tới `/account/password` trước khi dùng quyền nghiệp vụ: nhập mật khẩu hiện tại/mới/xác nhận, ít nhất12 ký tự và tối đa72 byte UTF-8; thành công quay login và thu hồi phiên cũ. Đổi trên Web trước khi đăng nhập Desktop. Bootstrap/admin reset theo [setup](setup-and-troubleshooting.md), không có default demo password.

[Navigation template](../../spring-web/src/main/resources/templates/fragments.html) hiện nhóm theo quyền: **Tổng quan**; **Hồ sơ** (Đăng ký hồ sơ/Hộ gia đình/Phương tiện/Thẻ & gói); **Vận hành** (Sơ đồ bãi/Lịch sử xe/Giao dịch & đối soát); **Cấu hình** (Bảng giá/Hạn mức); **Quản trị** (Tài khoản/Vai trò & quyền hạn/Nhật ký bảo mật); **Đăng xuất**. Không hiện mục không có nghĩa server đã được kiểm toàn matrix; không invent payment-provider hoặc alert queue từ đề cương.

### Hồ sơ cư dân, hộ và xe

1. **Đăng ký hồ sơ** (`/registrations`): [form](../../spring-web/src/main/resources/templates/registrations.html) chia thông tin hộ/căn hộ, người đại diện và phương tiện. Nhập hồ sơ và giấy tờ cần thiết; tải ảnh đăng ký hoặc **Mở camera** → **Chụp ảnh**. Form ghi tối đa10MB mỗi ảnh.
2. QR CCCD: **Quét từ ảnh đã chọn** hoặc **Mở camera quét QR** → **Chụp và đọc QR**; kiểm lại dữ liệu được điền trước lưu. QR autofill **không** là xác minh ảnh CCCD hoặc consent/identity assurance.
3. Bấm **LƯU HỒ SƠ VÀ CHUYỂN SANG ĐĂNG KÝ GÓI**. Handler/test evidence có redirect sang cards; không proof hồ sơ được phê duyệt/payment settled theo toàn bộ NV01.
4. **Hộ gia đình** (`/households`): [form](../../spring-web/src/main/resources/templates/households.html) có **Lưu hộ gia đình**, thêm/cập nhật thành viên, **Chọn người cần chỉnh sửa**, **Xóa form / thêm mới**, **Chỉnh sửa** và upload/camera ảnh. Bổ sung member theo đúng hộ, kiểm thông tin/ảnh sau save.
5. **Phương tiện** (`/vehicles`): [form](../../spring-web/src/main/resources/templates/vehicles.html) có lựa chọn hộ, **Chủ đăng ký**, người được phép, thông tin plate/giấy đăng ký/nhiên liệu/thông số; **Đăng ký phương tiện** và lựa chọn phương tiện cần chỉnh sửa. Cấp quyền **theo từng xe**, cùng hộ không tự được dùng tất cả xe.
6. **Hạn mức** (`/settings`): [form](../../spring-web/src/main/resources/templates/settings.html) nhập số xe hai bánh/ô tô tối đa mỗi hộ và **Lưu hạn mức**; README còn mô tả override riêng tại hộ. Full quota enforcement/concurrent validation chưa verified; không coi form lưu thành công là toàn policy đã enforced.

Web camera chạy trong browser và cần quyền/thiết bị phù hợp; khác camera trên máy ANPR của Desktop. Browser compatibility, camera permission, editing/autofill runtime và full validation chưa được thao tác trong đợt tài liệu. Ba phép đối chiếu/liveness/manual approval trong [NV01](../requirements/nv01-registration.md) là nghĩa vụ còn cần kiểm chứng, không workflow đã hoàn chỉnh bởi QR/face upload.

### Thẻ, gói, giá và payment record

[Thẻ & gói](../../spring-web/src/main/resources/templates/cards.html) (`/cards`): chọn **Xe đã đăng ký**, **Hình thức**, **Ngày bắt đầu**, **Số ngày**, **Trạng thái**, **Hình thức thu**, **Mã giao dịch ngoài** → **Tạo / gia hạn gói theo biển số**. Main dùng biển số chuẩn hóa làm mã nhận diện, không nhập/quẹt mã thẻ riêng. Có **Vé lượt** và **Gói 30 ngày**; số ngày default30, input cho1–366. Kiểm danh sách thẻ: vehicle, validity, subscription fee/status, và **Lịch sử thu tiền gói**.

Handler tạo subscription-payment và PaymentTransaction/biên nhận khi lưu gói tháng; yêu cầu hình thức thu, chuyển khoản/POS cần mã giao dịch ngoài. Đây là **ghi nhận thu do người vận hành xác nhận**, không tích hợp xác minh ngân hàng/POS hay proof tiền thực nhận. Không bấm gia hạn lại chỉ vì mất response: có thể tạo thêm payment record; main chưa có contract idempotency cho form gói. Activation payment-only vs debt approval, refund và policy expiry vẫn unresolved; đối soát theo [NV02](../requirements/nv02-cards-passes-pricing.md).

**Vận hành → Giao dịch & đối soát** (`/payments`): kiểm số biên nhận, loại thu, hình thức, số tiền, người thu, trạng thái và mã tham chiếu. Với record `PAID`, nhập ghi chú rồi **Chốt đối soát** để chuyển `RECONCILED`. JSON receipt `/payments/receipts/{id}` và report `/payments/reports/reconciliation` tồn tại theo controller/quyền, không đồng nghĩa có nút in/export hoặc dịch vụ fiscal receipt. Đối soát phải dựa tiền/chứng từ thực tế; không tự chốt vì source ghi `PAID`. Chưa thao tác UI/thu tiền/đối soát runtime trong đợt này.

[Bảng giá](../../spring-web/src/main/resources/templates/pricing.html) (`/pricing`) có **Cập nhật giá**; đối chiếu loại xe và các giá ban ngày/đêm/qua đêm/monthly, khung giờ theo form trước lưu. Giá không bất biến và chưa có historical-rule snapshot proof.

**Defaults từ [DemoDataConfig](../../spring-web/src/main/java/vn/edu/parking/config/DemoDataConfig.java), không survey authority hoặc runtime verification:** monthly xe đạp/xe đạp điện100.000đ; xe máy/xe máy điện150.000đ; mô tô lớn300.000đ; ô tô4–5 chỗ1.500.000đ,6–7 chỗ1.700.000đ,8–9 chỗ1.800.000đ. Giá lượt default: xe máy5.000/8.000/10.000đ (day/night/chu kỳ24h có overnight), xe đạp2.000/3.000/5.000đ; ô tô40.000đ/4h đầu, thêm20.000đ/2h hoặc phần lẻ, tối thiểu100.000đ mỗi đêm qua00:00. Startup có thể tạo/bổ sung giá và chuyển một số giá legacy, không chỉ read-only. Kiểm bảng giá hiện tại và preview thực tế, không dùng các số này làm chứng từ thu tiền hoặc áp tùy ý cho mọi visitor/type.

Service evidence monthly eligibility dựa ngày vào và trạng thái card hiện tại; khác lời mô tả “còn hạn lúc ra”. Không hứa mọi lượt resident được0đ. Registered vehicle dùng official registry type; visitor/default type có fallback, nên giá khách phải kiểm theo result chứ không theo AI alone.

## Chuẩn bị Desktop

Source chính: [MainForm](../../desktop-winform/MainForm.cs) và [DesktopApplicationContext](../../desktop-winform/DesktopApplicationContext.cs). Khi mở app, dialog yêu cầu đăng nhập Spring; hủy login thì đóng app, đăng xuất chủ động quay lại login. Spring phải có JWT keys/account theo [setup](setup-and-troubleshooting.md). Header có **Spring API** (default `http://localhost:8080`), **Kiểm tra**, **Đăng xuất**, **Sơ đồ bãi**, **Cấu hình trạm**, **MỞ BARRIER**, **ĐÓNG BARRIER**, tên nhân viên/ca/đồng hồ. Không có ô ANPR API; health/processing AI đều đi qua Spring. “Hệ thống đang hoạt động - không có thao tác chờ” không là API/E2E readiness hoặc production certification; **Kiểm tra có thể gửi outbox**, không chỉ đọc health.

Chọn tab **XE VÀO** hoặc **XE RA** trước **Tải ảnh**/**Tải video**/**Camera xe** trong khung **XE / BIỂN SỐ**: nhận dạng điền plate vào tab đang chọn, cập nhật preview/type/confidence/frame và vehicle evidence. Khung **NGƯỜI / KHUÔN MẶT** có **Tải ảnh**/**Tải video**/**Camera mặt**. **XỬ LÝ SONG SONG** chọn media xe và chạy camera mặt cùng lúc; kiểm kết quả đúng tab/người trước xác nhận, không coi hai tác vụ xong là nghiệp vụ đã commit. Main không có checkbox manual OCR tự tick như feature guide cũ; chỉnh qua **CHỈNH SỬA KẾT QUẢ AI** sau khi đã có evidence.

Plate `59A112345`, `51H88888` trong tư liệu là ví dụ, không bảo đảm DB hiện tại đã seed. Không dùng `CARD001`/`CARD002` làm hướng dẫn quẹt thẻ của main: mã nhận diện chính là biển số. Kiểm lookup trước dùng.

### Cấu hình camera, ca trực và bàn giao

- **Cấu hình trạm**: nguồn camera xe/mặt default `0`, hỗ trợ chỉ số USB hoặc RTSP/HTTP(S); nguồn mở trên máy ANPR. Frame count default8 (proxy giới hạn4–16 cho nhận dạng xe), thời gian tự đóng barrier default5 giây (1–120), driver chỉ **Mô phỏng**. Settings lưu local ở `%LOCALAPPDATA%/ParkingGateDesktop/station-settings.json`; file hợp lệ ưu tiên environment fallback. Không chia sẻ URL camera có credentials, không coi nguồn mạng đã được kiểm thử thật.
- Web **Quản trị → Tài khoản** (`/account-management`) có tạo/cập nhật/khóa/reset tài khoản, phân ca và xem/thu hồi phiên theo quyền; **Vai trò & quyền hạn** (`/roles`) quản lý permission grants. Phân ca cần account hoạt động có quyền gate, tên ca/nhiệm vụ, thời gian bắt đầu/kết thúc; handler hiểu thời gian Web theo `Asia/Ho_Chi_Minh`, service chặn overlap, kết thúc trước bắt đầu hoặc ca dài hơn24h. Source validation không proof race/DB locking.
- Desktop hiển thị ca hiện tại; khi nhận ca mới có thể mở **Tổng kết ca trước**, **Đăng xuất** chủ động tải **Tổng kết ca hiện tại**. Dialog có thu theo phương thức, xe vào/ra/đang trong bãi, ô sử dụng/trống, giao dịch chờ đối soát, manual corrections và số outbox local. **XÁC NHẬN BÀN GIAO** là đóng dialog, không receipt/settlement hoặc durable handover approval. Đóng cửa sổ trực tiếp không cùng đường tổng kết như nút Đăng xuất; lỗi tải báo cáo không ngăn đăng xuất. Chưa kiểm tổng số/hiển thị runtime.

## Cư dân vào — quan sát UI, không chứng nhận đầy đủ NV03

1. Chọn **XE VÀO**, nhận dạng ảnh/video/camera để có vehicle evidence, kiểm lại **Biển số AI tự điền** và loại được hiển thị. Không có ô mã thẻ riêng; main dùng biển số làm identifier. Chỉ gõ plate không tạo evidence; nhận dạng entry có thể tự gọi lookup.
2. Bấm **TRA CỨU CƯ DÂN**; xem owner/hộ/card/pass/message và chọn **Người đang điều khiển** trong danh sách authorized members trả về.
3. Chọn **Camera mặt** hoặc tải ảnh/video mặt để Spring đọc ảnh đăng ký theo member ID và gọi FastAPI qua service header. Spring lưu face decision/score do upstream trả về thành face evidence riêng; main không có policy tính lại threshold độc lập như feature branch. Face evidence phải đúng member/operation/account/Desktop session; không hứa bind plate/recognition ID ở mọi bước khi current source chỉ kiểm các fields này. Desktop không gửi URL ảnh tham chiếu.
4. Kiểm member và evidence rồi **XÁC NHẬN XE VÀO**. Result hiển thị mã lượt, plate/type/owner/identifier/status/time/fee. Mặc định backend đòi Gate Evidence; không tự tắt cấu hình để dùng client face flag.
5. Source gọi auto-open barrier khi response không Warning **hoặc có manual correction reason**; đồng bộ map event, copy plate sang exit, clear guest capture và có thể clear entry form sau2 giây. Barrier **mô phỏng**, tự đóng theo Cấu hình trạm (default5 giây), không device integration.

**Cảnh báo quan trọng:** thiếu gói/mapping card theo biển hoặc gói tháng hết hạn có thể tạo OPEN và warning, trong khi UI không auto-open nếu chưa manual. Warning không đồng nghĩa “không có session”; kiểm history/lookup trước submit lại. Card tìm theo plate có trạng thái không ACTIVE bị chặn; không hứa server dùng mã thẻ client để xác định quyền. Nhánh cư dân legacy không có face profile vẫn có thể không cần member; đây là gap với NV03, không acceptance đã duyệt.

**Chỉnh sửa có lý do:** sau nhận dạng, **CHỈNH SỬA KẾT QUẢ AI** mở [ManualOverrideDialog](../../desktop-winform/ManualOverrideDialog.cs), chọn lý do, kiểm/sửa plate và loại xe, mô tả10–500 ký tự. Có lý do plate không đọc được/sai, sai loại, face review/failed hoặc thiếu ảnh đăng ký. [ManualOverrideService](../../spring-web/src/main/java/vn/edu/parking/service/ManualOverrideService.java) kiểm account/quyền, Desktop session còn hiệu lực, **ca trực đang hoạt động**, đủ vehicle+face evidence và reason phù hợp, ghi approved/rejected events và before/after. Không mô tả nhánh này thành ba ngoại lệ `AI_REVIEW`/outage hoặc checkbox `OVERRIDE_CREATE` của feature branch. Một số reason dùng decision `REVIEW`/`FAIL`/`CAPTURED`; FastAPI có `REJECT` nên không bảo đảm mọi thất bại đều override được. Khi bị từ chối, chuyển quản trị, không sửa JSON/flag hoặc mở barrier để vượt. Xem [NV03](../requirements/nv03-resident-entry.md).

## Cư dân ra

1. Chọn **XE RA**, nhận dạng/kiểm plate để có EXIT vehicle evidence; không có thao tác nhập/quẹt card riêng.
2. **TRA CỨU NGƯỜI ĐƯỢC PHÉP** → chọn **Người đang lấy xe**. Người này có thể khác người vào nếu được cấp quyền cho đúng xe theo yêu cầu nguồn.
3. Dùng **Camera mặt** hoặc tải ảnh/video với người đã chọn, kiểm decision/score/message. Spring lấy ảnh đăng ký từ server và kiểm member evidence khi cung cấp; `confirmExit` không gọi lại `verifyDriver` cho cư dân. Không coi danh sách UI hoặc evidence member là enforcement đầy đủ quyền người lấy/plate/open-session binding; resident-exit policy vẫn cần xác nhận.
4. Bấm **XEM TRƯỚC PHÍ** và đọc result. **XÁC NHẬN XE RA** yêu cầu `_lastPreview` phù hợp normalized plate; nếu thiếu, UI báo “Hãy bấm Xem trước phí trước khi xác nhận.” Thay plate phải preview lại. Preview không là proof đã thanh toán và không giữ giá cố định; confirm tính lại.
5. Khi preview có phí>0, request builder mở **Thu phí xe ra**: chọn **Tiền mặt**, **Chuyển khoản** hoặc **Máy POS**; chuyển khoản/POS cần mã giao dịch. **Xác nhận thu** tiếp tục gửi exit-confirm; **Hủy/Bỏ qua** hủy request này, không phải miễn phí. Builder còn được gọi ở preview nên nếu còn `_lastPreview` có phí, preview lại cũng có thể hiện dialog; không coi dialog là receipt đã commit. Server tính lại fee; với fee>0 nó tạo PaymentTransaction/receipt trong transaction trước khi trả `COMPLETED`, fee0 cho gói hợp lệ không tạo exit payment. UI auto-open theo cùng Warning/manual condition, refresh map/reset preview và có thể clear form. `COMPLETED` khác tên `CLOSED` trong yêu cầu, không tự equate là acceptance.

Main có dialog/payment record/receipt/đối soát source, nhưng không có bằng chứng tiền thực nhận, bank/POS integration hoặc refund/waiver được duyệt. Manual open barrier không tạo/đóng session. Repeat confirm, races, alternate driver, fee thay đổi giữa preview/confirm và payment-before-close vẫn cần kiểm thử trên môi trường được duyệt theo [NV04](../requirements/nv04-resident-exit.md).

## Khách vãng lai

- Entry: cùng tab **XE VÀO**, nhận dạng plate, lookup xem khách vãng lai; **Camera mặt** khi không chọn member sẽ capture face. Kiểm preview rồi confirm entry. Capture đi qua Spring, lưu private và nếu được gửi kèm thì consume vào parking session; không có visitor enrollment/special KHÁCH selector. Chỉ nhập plate không tạo evidence mặc định.
- Capture vẫn optional. Nếu không có ảnh face entry thì server không áp dụng conditional guest face check khi ra; policy yêu cầu capture bắt buộc chưa được xác nhận.
- Exit: **XE RA** → nhận dạng plate đang có OPEN → **Camera mặt** hoặc tải ảnh/video mặt để Spring dùng ảnh entry private làm registration image → preview/thu phí/confirm. Guest verification route dùng EXIT vehicle evidence để tìm entry reference; consumption kiểm face operation/type/actor/session/member, không kế thừa claim mọi proof đều bind parking-session/plate như feature branch. Khi entry image có mặt, `verifyGuestExit` đòi face-passed hoặc manual override đã authorize. Không có bước chọn guest session riêng trong MainForm; server tìm OPEN theo plate.
- Fee/payment/waiver/lost-card/ownership cần policy người quản trị và evidence. Main có thu phí/biên nhận record như trên, không payment-provider hoặc lost-card wizard được chứng minh. Không cấp cư dân/card để “sửa” thiếu evidence của khách. Xem [NV05](../requirements/nv05-visitor-parking.md).

## Phân loại xe

Desktop hiển thị type AI/confidence và có manual correction dialog cho `MOTORBIKE`/`CAR`/`UNKNOWN`, reason và before/after audit theo source; đây không official six-class taxonomy editor. Nhân viên kiểm giấy tờ và cập nhật xe qua Web theo quyền quản trị; registered official type được Spring sử dụng. Fallback theo hình dạng biển/heuristics không bảo đảm đúng six-class/EV detection. Không có charging screen/hardware được chứng minh. [NV06](../requirements/nv06-vehicle-classification.md) giữ hai nhánh A/B; parking slot/xe điện không là charging rights/session.

## Sơ đồ bãi và điều phối

[ParkingMapForm](../../desktop-winform/ParkingMapForm.cs):41–140 mở từ **Sơ đồ bãi**, có filters **Tầng**, **Khu vực**, **Loại xe**, **Làm mới**. Map inline cũng mở [SlotDetailDialog](../../desktop-winform/SlotDetailDialog.cs) khi chọn ô. Đây là dữ liệu database, không physical occupancy sensor verified.

**Khác biệt main quan trọng:** `SlotDetailDialog.BuildUi` chỉ thêm `BuildGuestTab` (**Điều phối xe vừa vào**). Các builder resident/borrow/management và labels dưới vẫn còn trong source nhưng không được nối vào dialog; không hướng dẫn tìm các tab không hiển thị trên Desktop. Web `/slots` có đủ bốn nhóm theo quyền.

| Tab/chức năng | Thao tác source và vị trí khả dụng |
| --- | --- |
| **Gán xe cư dân** | Dùng Web: chọn xe chưa có ô hoặc bỏ gán → **Lưu gán xe cư dân**. Desktop có labels **LƯU GÁN XE**/**HỦY GÁN (TRẢ VỀ VỊ TRÍ TRỐNG)** trong builder chưa nối, không thao tác UI được từ main dialog. Service có one-vehicle/one-slot precheck, không race guarantee. |
| **Đỗ nhờ (Hàng xóm)** | Dùng Web nhập plate/thời gian/ghi chú và lưu/hủy. Desktop builder chưa nối có1/2/4/8/12/24/48h (default2), **XÁC NHẬN CHO ĐỖ NHỜ**/**HỦY THIẾT LẬP ĐỖ NHỜ**; giữ mô tả này để nhận diện source, không hứa tab hiển thị. Status/overdue cần refresh, không proof timer auto-sync. |
| **Điều phối xe vừa vào** | Desktop/Web: chọn recent unassigned session → **ĐIỀU PHỐI VÀO Ô NÀY** trên Desktop; không tạo entry mới và không reader/camera trigger thật. |
| **Khóa / Giải phóng** | Dùng Web block/unblock/giải phóng ô; Desktop builder chưa nối có **GIẢI PHÓNG Ô (XÓA TOÀN BỘ GÁN XE & ĐỖ NHỜ)**. Thay slot links không exit-confirm/settlement; kiểm đúng ô trước thao tác có ảnh hưởng dữ liệu. |

Web [Sơ đồ bãi](../../spring-web/src/main/resources/templates/slots.html) có bốn nhóm điều phối và thêm **Tạo ô lẻ**, **Tạo sơ đồ nhanh** → **Tạo sơ đồ ngay**, chọn tầng/khu/prefix/range/type. `batchGenerateSlots` clamp count1–100 và skip mã đã tồn tại theo source; không race/duplicate-concurrency guarantee. Backend cũng kiểm nhóm loại xe khi gán/đỗ nhờ/điều phối; runtime field parity/dispatch constraints còn cần kiểm chứng. Inline map refresh có catch bỏ qua lỗi; sơ đồ không update không có nghĩa thao tác đã rollback.

## Lịch sử, dashboard và doanh thu

[Lịch sử](../../spring-web/src/main/resources/templates/sessions.html) (`/sessions`) trình bày sessions vào/ra. [Dashboard](../../spring-web/src/main/resources/templates/dashboard.html) (`/`) có filter **Theo tháng**, **Theo năm**, **Theo khoảng ngày** → **Áp dụng**, chart và recent-session rows; detail/reporting source đã ghi tại traceability. Kiểm records gốc khi totals khác kỳ vọng.

Main có PaymentTransaction và `/payments` phục vụ thu/đối soát, nhưng dashboard/revenue aggregates và receipts không tự là proof tiền thực nhận hoặc đối soát ngân hàng. Kiểm query và thời gian cắt kỳ trước so các tổng; không hứa mọi màn hình đều dùng cùng nguồn session/subscription/transaction. Chưa có known-total runtime reconciliation/export-permission validation; không invent nút export/alert-resolution/trend-prediction. Tách revenue, parked sessions, registry vehicle count và slot occupancy. Xem [NV08](../requirements/nv08-reporting.md).

## Lỗi và can thiệp thủ công

- Đọc message/status, kiểm URL/services với **Kiểm tra**, xem logs theo [troubleshooting](setup-and-troubleshooting.md). Lookup lỗi hiển thị thông tin cư dân lỗi; không chọn member/confirm khi evidence không chắc.
- Recognition quality thấp: kiểm plate/giấy tờ và nhận dạng lại; dùng dialog chỉnh sửa sau khi đã có evidence nếu được phân ca/quyền và đủ điều kiện server. Không có checkbox OCR tự bật; reason cần10–500 ký tự và audit như trên. Không dùng barrier trực tiếp để thay quy trình này.
- **MỞ BARRIER/ĐÓNG BARRIER** chỉ đổi state mô phỏng; không thay entry/exit/payment. Không dùng chúng như recovery transaction.
- Timeout/lost response: lookup/history/receipt và outbox trước retry, vì commit có thể đã xảy ra. Main **có** operationId cache và offline queue như mục dưới; chưa có live proof safe resync/financial exactly-once.
- Với liveness/ảnh giả, authorization mismatch, mất thẻ, payment/waiver hoặc evidence thiếu: chuyển quản trị, không coi “bấm thủ công” là chấp nhận theo NV07.

### Mất kết nối và outbox — có code, chưa kiểm recovery live

[ParkingApiClient](../../desktop-winform/ParkingApiClient.cs) tạo `operationId` UUID cho entry/exit-confirm, thử gửi và enqueue khi gặp lỗi kết nối/I/O/cancel. [OfflineOutboxStore](../../desktop-winform/OfflineOutboxStore.cs) lưu `%LOCALAPPDATA%/ParkingGateDesktop/offline-outbox.json`, chống trùng operationId trong file, ghi qua `.tmp` rồi move; **từ chối request có base64 ảnh mặt**. Vì entry capture có thể còn base64, không phải mọi thất bại đều được queue. JSON local không được mã hóa bởi store, có plate/evidence/payment fields và lastError; giữ quyền truy cập, không gửi nguyên file ra báo lỗi công khai.

MainForm thử flush định kỳ và **Kiểm tra** cũng flush khi Spring health thành công; gửi lại giữ nguyên payload/operationId, xóa item sau response thành công, dừng ở lỗi đầu tiên và giữ lastError. Nó không hiển thị lại kết quả queued như confirm trực tiếp hoặc mở barrier theo replay. [ParkingIdempotencyService](../../spring-web/src/main/java/vn/edu/parking/service/ParkingIdempotencyService.java) có lưu response/cache và kiểm payload; đây không bảo đảm exactly-once trên DB thật khi crash/concurrent lanes.

Khi báo còn thao tác chờ: giữ app/file, đối chiếu session/receipt với quản trị trước tạo thao tác mới. Không sửa/xóa JSON, đổi Spring URL hoặc dùng account/session mới để ép gửi evidence cũ; evidence TTL và owner/Desktop session có thể làm queue không gửi được. Nếu mất response sau thu tiền, không thu lần hai chỉ vì UI báo lỗi. Nhãn hàng đợi là pending, không xác nhận entry/exit đã commit hay chưa; recovery/retention/đổi ca cần quy trình được duyệt, không dùng barrier để đồng bộ dữ liệu.

## Giới hạn chưa xác minh

Chưa có UI/runtime exercise trong đợt tài liệu: browser webcam/QR/editing, full quota/validation, real USB/network camera/AI accuracy, mandatory resident exit rights, tiền thực nhận/refund/receipt/đối soát, ca/bàn giao, audit/privacy/retention, concurrency/live recovery/idempotency/outbox, exports/hardware và production deployment. FastAPI camera path tính liveness nhưng không dùng làm gate quyết định chống giả, không suy tên version là liveness proof. Standalone demo ba ảnh không lấp các gaps gate. Các yêu cầu còn mở tại [NV07](../requirements/nv07-exceptions-recovery.md), [traceability](../requirements/traceability.md) và [testing strategy](../testing/strategy.md); không nâng status thành implemented hoặc PASS từ hướng dẫn này.
