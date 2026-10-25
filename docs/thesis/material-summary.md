# Tổng hợp tư liệu và sản phẩm khóa luận

[Yêu cầu học thuật](requirements.md) · [Nghiệp vụ](../requirements/README.md) · [Quyết định](../decisions/README.md)

## Mục đích và mức bảo toàn

Trang này giữ nội dung cần đọc của tư liệu đề cương/giá/gaps bằng Markdown, không yêu cầu mở bản gốc. Đây là tổng hợp theo chủ đề, **không bản sao pháp lý hoặc chứng nhận mọi phiên bản tương đương**. Không chép thông tin sinh viên, mã số, số điện thoại, email, chữ ký hoặc ảnh cá nhân. Tên “final/verified/delivery” không xác lập authority.

Kiểm kê ngày 2026-10-23 có 142 tệp: 133 PNG, ba PDF, một DOCX, một JSON và bốn Markdown. Trong đó126PNG là render đề cương thuộc mười nhóm; bảyPNG còn lại là render bảng giá. Fingerprints được lưu local để bảo vệ bản gốc; không đưa scratch/OCR vào tài liệu sản phẩm.

| Nhóm tư liệu | Nội dung đã giữ / trang sở hữu | Mức đọc và giới hạn |
| --- | --- | --- |
| DOCX đề cương | Tám NV, hai nền tảng đồng bộ, ≥4 nghiệp vụ và 1–2 phức tạp, nhóm3/12tuần, sản phẩm/rubric/lịch dưới đây | Đã đọc phần văn bản và bảng XML; không xác nhận chữ ký, approval, layout hoặc tương đương render |
| Hai PDF không sạc, mỗi bản18 trang | NV01–08, ba ảnh, payment-before-close, recovery, báo cáo; [NV](../requirements/README.md) và [decisions](../decisions/README.md) giữ mâu thuẫn Bước9 vào/ra | Đã đọc PDF; authority vẫn mở |
| Nhóm ảnh18 trang có sạc | [NV06](../requirements/nv06-vehicle-classification.md) giữ quyền/connector/validity/ChargingSession/simulation; NV01 và NV08 ghi tác động | Một số trang đã đọc trực quan; OCR toàn nhóm, chưa đối chiếu từng chữ/từng trang |
| Bảy nhóm render ngắn | Sáu nhóm9–10 ảnh có bộ tám NV và nhánh sạc theo OCR; nhóm13 ảnh có các page/final-page và mô tả sáu quy trình | OCR local en-US của toàn bộ126 PNG chỉ dùng phát hiện khác biệt. Không đủ đảm bảo tiếng Việt, bảng số, tương đương hay không còn nội dung riêng |
| Bảng giá PDF7 trang và ảnh liên quan | [Mức phí tham khảo](../requirements/pricing-reference.md): mức tháng, ngày/đêm/chu kỳ, ô tô và ví dụ | Đã đọc PDF; chưa xác nhận provenance/sampling/approval hoặc equivalence ảnh |
| Ghi chép gaps | [Status](../implementation-status.md): mười nhóm thiết bị/AI/camera/privacy/API/slots/tài chính/accounts/adverse tests/SDK | Đã đọc; claims cũ được đối chiếu controls mới, không chứng nhận triển khai |
| Ghi chép sinh tài liệu | Quy tắc template và render QA ở cuối trang | Đã đọc; đường dẫn máy cũ/hash/template không hợp đồng ứng dụng |
| README/metadata JSON | Phân nhóm/phiên bản, không authority hoặc policy độc lập | Metadata không thay kiểm nội dung; không chép đường dẫn máy/PII vào canonical docs |
| Bản trích quy định học thuật | [Requirements](requirements.md): minima cử nhân/kỹ sư và track nghiên cứu | Đã đọc bản trích; chưa xác minh nguồn gốc/tính áp dụng hiện hành |

**Lưu trữ bản gốc:** chủ dự án đã yêu cầu xóa thư mục tư liệu khỏi repository sau kiểm tra. Bản sao đầy đủ142tệp được giữ ngoài repository và kiểm SHA-256 trước khi xóa; vị trí khôi phục ghi trong bàn giao local. Docs giữ nội dung tổng hợp, không tuyên bố OCR đã chứng minh mọi biến thể ảnh tương đương hoặc chuyển đủ từng chi tiết. Việc loại bỏ tư liệu không giải quyết authority, chính sách hay các giới hạn kiểm chứng trên.

Metadata QA accessibility ghi counts high/medium/low đều0 và findings rỗng cho một đường dẫn DOCX máy cũ; không nêu engine/protocol hoặc chứng minh WCAG, UI accessibility hay bản DOCX hiện tại đã đạt. Nhóm render ngắn còn phân biệt enrollment ba ảnh với vận hành hằng ngày dùng ảnh đăng ký/ảnh vào, không quét CCCD mỗi lượt; P04 cần chốt thay vì âm thầm coi mọi bản có cùng identity policy.

## Tư liệu kế thừa từ `main` và nơi đọc thay thế

- `De_cuong_chi_tiet_KLCN_QL_BaiDoXe.docx` ở root trùng SHA-256 với DOCX đã lưu trong lịch sử trước khi tổng hợp (`894cb76a052cfa82ec38c7e93e4ee50c061520f6118bd6077b49058b86415799`). Đối chiếu XML xác nhận có NV01–NV08 và hai bảng; nội dung đọc tại trang này, [yêu cầu học thuật](requirements.md) và các trang NV, không cần đưa lại DOCX vào root. Hash không tự chứng minh layout/approval hoặc coverage từng chi tiết.
- `PROJECT_GAPS.md` trên `main` đã có cập nhật khác bản ghi chép cũ. [Trạng thái implementation](../implementation-status.md#ghi-nhận-mới-hơn-từ-main) giữ riêng các hạng mục được nguồn báo đã có và tám nhóm còn thiếu; không thay bảng NV hoặc kết quả kiểm chứng lịch sử bằng các claims này.
- `artifact.md` là hợp đồng thao tác DOCX: template/hash, phần được sửa, giữ layout và kiểm render. Các ràng buộc cần đọc nằm tại mục “Ràng buộc trình bày” bên dưới; đây không phải contract API/ứng dụng. Đường dẫn máy cũ không trở thành cấu hình dự án.
- `.docx_qa_a11y.json` là metadata QA; không dùng findings rỗng làm chứng nhận accessibility. Không đưa đường dẫn máy hoặc dữ liệu cá nhân trong metadata vào tài liệu canonical.

Các bản kế thừa được bảo toàn local có manifest hash trước khi bỏ khỏi root; không phục hồi lại kho tư liệu vào source chỉ vì `main` còn track bản cũ. Chúng không trở thành bằng chứng kiểm thử ứng dụng hoặc nguồn phê duyệt nghiệp vụ.

## Mục tiêu, khảo sát và thiết kế được đề xuất

Xây hệ thống bãi xe chung cư trên Web và WinForms, dùng chung Spring API/MySQL/dịch vụ AI; xử lý người trong hộ dùng chung xe có quyền theo xe, khách, xe điện, ảnh kém và doanh thu. Khảo sát phải ghi cơ cấu tổ chức, quy trình/biểu mẫu AS-IS, camera/thiết bị/giá, vấn đề, giả định và TO-BE; không dùng bảng giá vô danh làm kết quả khảo sát đại diện.

Thiết kế cần use case nghiệp vụ/hệ thống và đặc tả, activity/sequence, lớp phân tích/thiết kế, ERD/data dictionary, API/errors, wireframe, phân quyền/audit/privacy, integration/test plan. Khái niệm đề xuất không tự trở thành entity hiện hành: [dictionary](../architecture/data-dictionary.md) đối chiếu main abd6183 có20 entity và hai bảng nối, gồm PaymentTransaction/GateEvidenceAsset/ParkingOperation; con số14 thuộc snapshot trước. Nhánh sạc chờ P01. Một bản cũ nêu sáu quy trình không thay tám NV; P08 cần chốt proposal áp dụng.

## Sản phẩm tối thiểu và phân công

- Hồ sơ khảo sát, SRS, UML, ERD/data dictionary, API contract và traceability.
- Source Web, WinForms, AI service, scripts/config mẫu không secret và bản đóng gói có hướng dẫn.
- Dữ liệu thử hợp lệ, test cases/report, AI metrics/protocol, lỗi/gaps và deployment/backup-restore evidence thực tế.
- Báo cáo đúng mẫu, hướng dẫn, slide, kịch bản bảo vệ và ảnh/video minh chứng không làm lộ PII.
- SV1 phụ trách Web/API/CSDL/backlog; SV2 phụ trách Desktop/cổng/UI và luồng tích hợp; SV3 phụ trách AI/dataset/metrics và tổng hợp minh chứng. Đây là vai trò kế hoạch, không ghi nhận đóng góp đã hoàn tất của cá nhân.
- Cuối mỗi tuần có bản chung chạy được; tối thiểu một buổi nội bộ và một buổi với giảng viên/tuần, ghi quyết định và tích hợp phần việc trước báo cáo. Review chéo, issue/backlog/WBS và ma trận trách nhiệm phải theo dõi được.

## Kế hoạch12 tuần được đề xuất

| Tuần | Công việc / sản phẩm dự kiến | Chủ trì |
| --- | --- | --- |
| 1 | Khảo sát hiện trạng, biểu mẫu/camera/giá; biên bản, AS-IS, vấn đề/giả định | Cả nhóm; SV3 tổng hợp |
| 2 | Chốt scope/tám NV/NFR; SRS1, WBS, backlog, trách nhiệm/tích hợp | SV1; cả nhóm duyệt |
| 3 | Use case/đặc tả/activity/sequence/lớp phân tích; traceability1 | SV2; cả nhóm |
| 4 | Kiến trúc/lớp/ERD/API/security/wireframe; dictionary/contracts | SV1 API/DB; SV2 UI; SV3 AI |
| 5 | Nền DB/API/auth/RBAC/audit, khung Desktop/AI; bản tích hợp CRUD/login | Mỗi người/module, review chéo |
| 6 | NV01–02 hồ sơ/quyền/thẻ/gói/phí; đăng ký/gia hạn/thanh toán tích hợp | SV1; SV2/SV3 test |
| 7 | NV03–04 Desktop/cư dân/người nhà/thẻ/lượt/barrier; E2E dự kiến | SV2; SV1 API |
| 8 | ANPR/OCR/phân loại NV03–06; dataset, metrics ban đầu | SV3; SV2 tích hợp |
| 9 | Khuôn mặt/liveness/ba ảnh, khách NV05 và ngoại lệ NV07 | SV3; cả nhóm hỗ trợ |
| 10 | NV06–08 phân loại/cảnh báo/tra cứu/doanh thu/dashboard, đối soát | SV1; cả nhóm E2E |
| 11 | Test chức năng/API/tích hợp/AI/performance/security/backup-restore; sửa lỗi, triển khai thử | Cả nhóm |
| 12 | Đóng gói/báo cáo/hướng dẫn/slide/ảnh-video và luyện demo | Cả nhóm |

Nhánh đề cương có sạc thay sản phẩm tuần10 bằng quyền/lịch sử sạc mô phỏng; chỉ áp dụng nếu P01 được duyệt. Tất cả các dòng là **kế hoạch**, không kết quả đã đạt. Lịch12tuần không phủ nhận track kỹ sư14tuần trong bản trích quy định.

## Rubric trong bảng DOCX

Giữ số điểm và nhóm CLO để người đọc hiểu nghĩa vụ; chưa xác nhận đây là rubric được duyệt hoặc điểm đã đạt. Bảng gốc còn placeholders “Chức năng01..N”; cần thang chi tiết được xác nhận, không tự gán mỗi NV0.5điểm.

| Nhóm | CLO | Điểm trong bảng |
| --- | --- | --- |
| Lý do/mục tiêu; khảo sát cơ cấu/quy trình/biểu mẫu | CLO1.1 | 0.25 + 0.50 |
| Kế hoạch/phân công | CLO6 | 0.25 |
| Use case nghiệp vụ; hệ thống; lớp phân tích | CLO1.2 | 0.25 mỗi mục |
| Lớp thiết kế; mô hình dữ liệu | CLO2.1 | 0.25 mỗi mục |
| Thiết kế UI | CLO2.2 | 0.50 |
| API; phân tích chức năng các nền tảng | CLO1.1 | 0.25 mỗi mục |
| Login/logout/password/RBAC; chức năng bài toán | CLO3 | 0.50 + 3.50; phần chức năng chia mục tối đa0.50 |
| Kiểm thử/triển khai | CLO4 | 0.75 |
| Nội dung báo cáo; hình thức báo cáo | CLO5.1 | 0.50 mỗi mục |
| Thái độ/tác phong | CLO6 | 0.50 |
| Báo cáo/slide | CLO5.2 | 0.50 |

Tổng cơ bản10.0. Bảng còn ghi khuyến khích NCKH: cấp Khoa0.5, cấp Trường/tạp chí cấp Trường trở lên1.0; tổng chung không quá10. Không cộng dồn thành điểm vượt10 hoặc coi dùng AI là đạt NCKH. Cần xác nhận cách áp dụng với giảng viên.

## Ràng buộc trình bày và thư mục tham khảo

Ghi chép tạo đề cương cũ yêu cầu giữ template/page size/margins/styles/header/footer/page fields/rubric/signature layout; chỉ điền các slot được phép, để trống thông tin chưa cấp, mục tiêu15–20trang và kiểm render không clipping. Đây là ràng buộc **sản phẩm đề cương lịch sử**, không yêu cầu ép Markdown hoặc ứng dụng thành15–20trang; chưa kiểm lại layout DOCX trong đợt này.

Script lịch sử đã được chuyển sang `tools/thesis/remove_ev_charging_from_proposal.py`, nhận `--input` tường minh và output mới mặc định dưới `.artifacts/tool-runs/`; không tự khôi phục DOCX gốc hoặc ghi theo đường dẫn máy cũ. Xem [hướng dẫn công cụ](../../tools/thesis/README.md). Không dùng generator trong startup/build ứng dụng hoặc để xác định policy. Kiểm CLI/fixture synthetic không chứng minh render/layout hay nội dung tài liệu gốc; docs và ứng dụng không cần chạy generator để hoạt động.

Các nguồn tham khảo dự kiến: UML2.5.1 (OMG); Spring Boot reference; Microsoft WinForms; MySQL8.4; Ultralytics YOLO; OpenCV; Smith2007 về Tesseract; Deng và cộng sự2019 ArcFace; Yu và cộng sự2020 anti-spoofing; văn bản91/2025/QH15 về dữ liệu cá nhân. Việc được liệt kê không chứng minh code dùng Tesseract/ArcFace/anti-spoofing hoặc tuân thủ pháp luật; source hiện dùng EasyOCR/YuNet/SFace. Survey link chưa đủ provenance/consent nên không coi là kết quả xuất bản. References học thuật cần kiểm thư mục/phiên bản/citation trước nộp.
