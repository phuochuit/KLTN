# Tổng duyệt và bàn giao LumiPark Web

Ngày kiểm chứng: 2026-11-03. Phạm vi: nhánh `feature/web-ui-redesign`, giữ checkpoint `d824918`, tích hợp `main` tại `0bed157` bằng merge thông thường theo duyệt của chủ dự án. Người dùng đã chấp thuận chuyển 15 màn hình sang giai đoạn bàn giao GitHub. Đây là kết quả engineering trong môi trường cô lập, không chứng nhận production hay thay thế nghiệm thu trực quan.

## Phạm vi 15 màn hình

| Nhóm | Màn hình và route |
| --- | --- |
| Truy cập | Đăng nhập `/login`; đổi mật khẩu `/account/password` |
| Tổng quan và vận hành | Tổng quan `/`; sơ đồ bãi `/slots`; lịch sử `/sessions` |
| Hồ sơ và phương tiện | Đăng ký `/registrations`; hộ/cư dân `/households`; phương tiện `/vehicles`; thẻ/gói `/cards` |
| Thu phí và cấu hình | Bảng giá `/pricing`; giao dịch/đối soát `/payments`; hạn mức `/settings` |
| Quản trị và bảo mật | Tài khoản/phiên/ca `/account-management`; vai trò `/roles`; nhật ký `/security-audit` |

[Screen inventory](../../design-system/kltn/SCREEN-INVENTORY.md) và [MASTER](../../design-system/kltn/MASTER.md) giữ nguồn thiết kế canonical. Các route ngoại lệ/export mới trên upstream được bảo toàn, không mở chiến dịch redesign ngoài 15 màn hình.

## Kết quả thay đổi

Giao diện Thymeleaf giữ shell LumiPark, Tailwind v4, Lucide và font tự host; layout responsive, điều hướng bàn phím, bảng cuộn, form theo ngữ cảnh, thông báo loading/lỗi và xác nhận thao tác. Các sửa Engineering Loop về điều hướng, editor, khôi phục Back, chống gửi lặp, trạng thái chờ và trang lỗi 403/404/413 được giữ lại.

Backend hỗ trợ gồm truy vấn dashboard, phân trang/lọc quản trị, transactional write services, khóa khi kiểm hạn mức, giữ liên kết payment/receipt và dọn ảnh mới sau rollback đã xác nhận. Matcher API dashboard dùng quyền dashboard tương ứng; không dựa vào việc ẩn nút để thay kiểm quyền server.

Contract upstream hiện hành được giữ: gói mới chỉ DAILY/MONTHLY; PER_VISIT chỉ là dữ liệu cũ; kích hoạt ACTIVE; gói tháng cố định 30 ngày tính cả ngày cuối; gói ngày tính `dailyPassPrice × số ngày`. Bảng giá có giá gói ngày. Đăng ký có thể mua gói tùy chọn trong cùng transaction, theo quyền VEHICLE_MANAGE của upstream; khi không có quyền xem thẻ, chuyển tới phương tiện theo luồng đã có của nhánh UI. Khoản tiền do server tính; client chỉ hiển thị tham khảo. Không tự retry write có kết quả chưa xác định, không đổi schema/chính sách thu phí/settlement.

## Finding được xử lý trong final review

- Hòa giải 50 đường dẫn xung đột với main; giữ tính năng export/ngoại lệ/audit và contract gói mới, thay vì lấy evidence cũ làm PASS cho bản tích hợp.
- Sửa biến sai tên trong phần xem trước thẻ gây ReferenceError sau khi chuyển sang DAILY/MONTHLY; trình duyệt đã chạy thực tế các mode, kỳ và số tiền.
- Sửa nhãn đăng ký để nói rõ lần gửi có gói đồng thời ghi nhận thu; các trường gói vẫn dùng được khi tắt JavaScript.
- Vô hiệu demo account bootstrap trong fixture cô lập và bổ sung H2/media isolation cho test gói mới; sửa assertion theo policy mới và đếm theo delta để không phụ thuộc thứ tự test.
- Thêm kiểm tra rollback hồ sơ/thẻ/payment và số ảnh private sau lỗi thiếu mã chuyển khoản; kiểm chứng quyền VEHICLE_MANAGE đúng upstream.
- Chặn helper browser cũ nếu listener không thuộc process tree của LoginBrowserHarnessTest cho đúng run; kiểm tra marker đơn thuần không còn đủ để cho phép mutation.
- Khôi phục tài liệu upstream còn được tham chiếu, sửa link/anchor, loại liên kết phụ thuộc artifact local; chuẩn bị frontend đúng lockfile trong CI và giữ đúng ngoại lệ tooling đã có trên upstream.

## Kiểm chứng đã thực hiện

| Kiểm chứng | Kết quả và giới hạn |
| --- | --- |
| JDK21, Maven offline `test-compile` | Build thành công, compile source và test; không cài dependency |
| Frontend `npm test`, `npm run build` | 47/47 test đạt; Tailwind/Lucide build đạt |
| Focused Spring | Kết quả mới nhất theo từng lớp: 130 test / 16 lớp, không failure/error/skipped. Đây là tổng hợp các lượt tập trung, không phải một lượt full regression |
| Lịch sử lỗi test | Lượt đầu 129 test có 5 assertion fail; lượt targeted 24 test còn 1 fail do đếm toàn bộ dữ liệu chung; lượt cuối ParkingFlow 13/13 đạt. Report lỗi được giữ, không đổi thành PASS |
| Bảo vệ browser harness | 5/5 test từ chối, gồm listener local đang sống với marker/PID hợp lệ nhưng không phải harness; chính probe mới cũng vượt kiểm tra ownership trên fixture thật |
| Tooling và docs checker | Runner self-test 31/31; checker unittest 8/8; kiểm tra Markdown/link/workspace thực hiện riêng trước commit |
| Browser vùng ảnh hưởng | 7 nhóm quan sát ở 1440 và 390px: quote DAILY, MONTHLY=30, review/pending card, giá gói ngày, gói tùy chọn/validation khi đăng ký, no-JS controls. Không pageerror; hai POST card/pricing kết thúc cục bộ; 0 business POST forward |
| Fixture browser | LoginBrowserHarnessTest H2/mock kết thúc Maven0 và XML1/1; đây là lifecycle fixture, không đếm thành số browser case |
| Review độc lập | Java backend, 12 JS và 10 template được review riêng, Root tổng hợp. Supplemental Codex Security scan `83c49142-724a-41d4-a0cc-0d1914b568ba` hoàn tất với 0 finding reportable trong phạm vi đã ghi |

Native security tool chỉ nhận baseline working-tree `d824918`, không nhận baseline custom `origin/main` cho chế độ này. Inventory 96 mục được đối chiếu: 34 đường dẫn liên quan PR được review; 62 mục kế thừa main không đổi so với `0bed157` được loại rõ. Hai property override local không đọc hoặc xuất bản; index giữ đúng upstream. Đây không phải khẳng định native scan bao phủ toàn bộ lịch sử PR. General code review vẫn đối chiếu diff với main, kết hợp evidence checkpoint còn hợp lệ.

Bằng chứng thô/screenshot/log được giữ local, không commit cùng source. Các test nguồn tương ứng nằm tại `spring-web/src/test/` và `tests/e2e/web/`; không cần artifact nội bộ để build ứng dụng. Ảnh browser desktop/mobile đã được Root quan sát; sau đó chỉ sửa nhãn lịch sử khoản thu để bao gồm cả gói ngày.

## Hiệu năng và phần chưa kiểm chứng

Số đo trước tích hợp được giữ như evidence lịch sử: 36 mẫu ghép vùng ảnh hưởng có median tải 137,15→148,45 ms và LCP 101,15→130,70 ms; fixture/warm-up khác nhau nên không suy cải thiện hay hồi quy nhân quả. Không có chứng cứ bottleneck để đổi sang Vite hoặc thay kiến trúc. Không đo lại benchmark toàn Web sau tích hợp; không khẳng định tăng tốc cho revision này.

**NOT VERIFIED:** live E2E, MySQL concurrency ở quy mô vận hành, settlement/payment-provider, ANPR/biometrics thật, thiết bị vật lý, screen reader/accessibility chuyên sâu, mọi biến thể BFCache và native background visibility. Browser mới không upload ảnh hoặc gửi registration POST; multipart thành công/rollback được kiểm tra bằng MockMvc/H2. Các báo cáo H2/mock/proxy không chứng minh DB/ANPR/financial workflow vận hành thật.

Không force push, rewrite checkpoint, tự merge PR hay deploy. CI GitHub cần đọc trạng thái thực tế sau push; kết quả local không được ghi thành CI PASS.
