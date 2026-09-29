from copy import deepcopy
from hashlib import sha256
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from lxml import etree

from fill_thesis_proposal_template import (
    EXPECTED_SOURCE_SHA256,
    SOURCE,
    W,
    add_run,
    first_paragraph,
    replace_paragraph,
    source_run_properties,
)


OUTPUT = Path(r"D:\Code\KhoaLuan\De_cuong_chi_tiet_KLCN_Quan_ly_bai_do_xe_15_20_TRANG.docx")


def bold_properties(rpr):
    rpr = deepcopy(rpr) if rpr is not None else etree.Element(W + "rPr")
    if rpr.find(W + "b") is None:
        etree.SubElement(rpr, W + "b")
    if rpr.find(W + "bCs") is None:
        etree.SubElement(rpr, W + "bCs")
    return rpr


def replace_labeled(paragraph, label, body=""):
    ppr = paragraph.find(W + "pPr")
    saved_ppr = deepcopy(ppr) if ppr is not None else None
    normal_rpr = source_run_properties(paragraph, prefer_bold=False)
    for child in list(paragraph):
        paragraph.remove(child)
    if saved_ppr is not None:
        paragraph.append(saved_ppr)
    add_run(paragraph, label, bold_properties(normal_rpr))
    if body:
        add_run(paragraph, " " + body, normal_rpr)


def insert_labeled_after(anchor, template, label, body=""):
    paragraph = deepcopy(template)
    anchor.addnext(paragraph)
    replace_labeled(paragraph, label, body)
    return paragraph


def build_block(heading_paragraph, title, items, detail_template):
    replace_labeled(heading_paragraph, title)
    anchor = heading_paragraph
    for label, body in items:
        anchor = insert_labeled_after(anchor, detail_template, label, body)
    return anchor


def keep_with_next(paragraph):
    ppr = paragraph.find(W + "pPr")
    if ppr is None:
        ppr = etree.Element(W + "pPr")
        paragraph.insert(0, ppr)
    if ppr.find(W + "keepNext") is None:
        etree.SubElement(ppr, W + "keepNext")


def append_cell_paragraph(cell, template, label, body):
    paragraph = deepcopy(template)
    cell.append(paragraph)
    replace_labeled(paragraph, label, body)


BLOCKS = [
    (
        "Nhiệm vụ 1 - Khảo sát nghiệp vụ và mô hình hóa hiện trạng",
        [
            ("Mục đích:", "Hiểu cách bãi xe chung cư đang vận hành, xác định đúng vấn đề và tránh xây dựng chức năng chỉ dựa trên giả định."),
            ("Đối tượng khảo sát:", "Ban quản lý, nhân viên trạm gác, cư dân/chủ căn hộ, thành viên hộ gia đình, khách và nhân sự phụ trách vị trí sạc."),
            ("Phạm vi quan sát:", "Cổng vào, cổng ra, quầy đăng ký thẻ, khu đỗ xe máy, khu ô tô, khu xe điện, vị trí sạc và khu xử lý sự cố."),
            ("Phương pháp:", "Quan sát trực tiếp, phỏng vấn bán cấu trúc, bảng hỏi, thu thập biểu mẫu mẫu và mô phỏng tình huống nếu không được tiếp cận dữ liệu thật."),
            ("Nội dung cơ cấu tổ chức:", "Xác định ai cấp thẻ, ai duyệt cư dân, ai sửa bảng giá, ai mở barrier, ai xử lý mất thẻ và ai đối soát doanh thu."),
            ("Nội dung quy định:", "Số xe tối đa theo căn hộ, giới hạn lượt/ngày, giờ hoạt động, điều kiện cấp gói tháng, quy định khách và quy định xe điện/sạc."),
            ("Nội dung bảng giá:", "Khảo sát cách tính theo loại xe, dung tích xi-lanh từ giấy đăng ký, thời gian gửi, ngày/đêm, cư dân/khách và gói 30 ngày."),
            ("Nội dung nhận diện:", "Khảo sát vị trí camera, khoảng cách, góc chụp, ánh sáng, tốc độ xe, tỷ lệ biển số/khuôn mặt bị mờ, che hoặc cháy sáng."),
            ("Nội dung dữ liệu cá nhân:", "Xác định loại thông tin căn cước thực sự cần, mục đích sử dụng ảnh, thời hạn lưu, người được xem và quy trình xóa/sửa dữ liệu."),
            ("Biểu mẫu cần thu thập:", "Phiếu đăng ký xe, phiếu cấp/thay thẻ, bảng giá, biên nhận, mẫu xử lý mất thẻ, sổ giao ca, báo cáo lượt xe và báo cáo doanh thu."),
            ("Quy trình hiện tại cần vẽ:", "Đăng ký cư dân; cấp thẻ/gói tháng; xe cư dân vào-ra; khách vào-ra; mất thẻ; xe lạ; ảnh không rõ; xe điện sử dụng vị trí sạc."),
            ("Vấn đề cần đánh giá:", "Ùn cổng, nhập liệu lặp, đối chiếu thủ công, mở cổng nhầm, không xác định người lấy xe, sai giá, thất thoát và khó truy vết."),
            ("Đầu ra:", "Biên bản khảo sát, bảng tổng hợp câu trả lời, quy trình AS-IS, danh sách vấn đề-nguyên nhân và quy trình TO-BE đề xuất."),
            ("Tiêu chí hoàn thành:", "Mỗi yêu cầu trong đề cương phải liên kết được với ít nhất một bằng chứng khảo sát hoặc một giả định được ghi rõ để xác minh sau."),
        ],
    ),
    (
        "Nhiệm vụ 2 - Lập kế hoạch và phân công cho nhóm 3 sinh viên",
        [
            ("Phạm vi dự án:", "Web Spring Boot, REST API, MySQL, Desktop WinForms, dịch vụ AI Python, dữ liệu thử, kiểm thử, triển khai và báo cáo."),
            ("SV1 - Phụ trách chính:", "Phân tích dữ liệu, ERD/MySQL, Spring Boot API, xác thực-phân quyền, Web quản trị, bảng giá, thanh toán và báo cáo."),
            ("SV2 - Phụ trách chính:", "WinForms trạm gác, camera/ảnh-video, luồng vào-ra, gọi API, xử lý ngoại lệ, barrier/đầu đọc thẻ mô phỏng và trải nghiệm vận hành."),
            ("SV3 - Phụ trách chính:", "ANPR, OCR, khuôn mặt, kiểm tra sống, phân loại phương tiện, dữ liệu thử, đánh giá mô hình và tích hợp AI service."),
            ("Trách nhiệm chung:", "Cả nhóm cùng khảo sát, Use Case, thiết kế, review mã, kiểm thử end-to-end, tài liệu, báo cáo và chuẩn bị thuyết trình."),
            ("Quản lý công việc:", "Tạo backlog theo Epic-User Story-Task; mỗi task có người phụ trách, hạn hoàn thành, đầu vào, đầu ra và tiêu chí chấp nhận."),
            ("Quản lý mã nguồn:", "Dùng Git; nhánh riêng cho chức năng; commit có ý nghĩa; pull request phải được ít nhất một thành viên khác review trước khi tích hợp."),
            ("Mốc tích hợp:", "Cuối mỗi tuần phải có phiên bản chung chạy được; không để Web, Desktop và AI phát triển tách rời đến cuối dự án."),
            ("Quản lý phụ thuộc:", "Thống nhất sớm schema dữ liệu và hợp đồng API; dùng mock API/mock AI khi mô-đun thật chưa hoàn thành."),
            ("Quản lý rủi ro:", "Có phương án khi thiếu camera, thiếu dữ liệu thật, mô hình chậm, nhận diện sai, máy yếu, Laragon lỗi hoặc thành viên trễ tiến độ."),
            ("Họp nhóm:", "Ít nhất một buổi nội bộ và một buổi với giảng viên mỗi tuần; ghi lại quyết định, việc tồn và thay đổi phạm vi."),
            ("Definition of Done:", "Chức năng chỉ hoàn thành khi đã tích hợp, có dữ liệu thử, test thành công/thất bại, log lỗi và tài liệu cập nhật."),
            ("Đầu ra:", "WBS, kế hoạch 12 tuần, bảng phân công, backlog, ma trận trách nhiệm, nhật ký họp và báo cáo tiến độ hằng tuần."),
        ],
    ),
    (
        "Nhiệm vụ 3 - Phân tích hệ thống",
        [
            ("Tác nhân chính:", "Quản trị viên/Ban quản lý, Nhân viên trạm gác, Cư dân/Thành viên hộ gia đình, Khách và Dịch vụ AI."),
            ("Hệ thống ngoài:", "Camera biển số, camera khuôn mặt, đầu đọc thẻ, barrier mô phỏng, vùng lưu ảnh và công cụ xuất báo cáo."),
            ("Ranh giới hệ thống:", "Hệ thống hỗ trợ ra quyết định và lưu bằng chứng; nhân viên vẫn là người xử lý cuối cùng khi kết quả không chắc chắn."),
            ("Use Case nghiệp vụ:", "Mô tả mục tiêu vận hành không phụ thuộc công nghệ cho đăng ký, vào-ra, thu phí, sạc, cảnh báo và báo cáo."),
            ("Use Case hệ thống:", "Tách chức năng cụ thể trên Web, Desktop, API và AI; chỉ rõ tác nhân khởi tạo và hệ thống tham gia."),
            ("Nội dung đặc tả:", "Mục tiêu, tác nhân, kích hoạt, điều kiện trước, dữ liệu vào, luồng chính, luồng thay thế, ngoại lệ, điều kiện sau và quy tắc."),
            ("Sơ đồ hoạt động:", "Vẽ luồng quyết định đối với cư dân hợp lệ, người nhà dùng xe, khách, sai thẻ, sai biển, sai mặt, ảnh kém và mất kết nối."),
            ("Sơ đồ tuần tự:", "Thể hiện tương tác Camera-Desktop-Spring API-AI-MySQL-Nhân viên cho luồng vào và luồng ra."),
            ("Lớp phân tích:", "User, Role, Apartment, Resident, HouseholdMember, Vehicle, VehicleAuthorization, Card, MonthlyPass, PriceRule, ParkingSession, Payment, Alert và AuditLog."),
            ("Lớp AI:", "MediaCapture, PlateDetection, OcrResult, FaceTemplate, FaceMatchResult, LivenessResult, VehicleClassification và QualityAssessment."),
            ("Yêu cầu phi chức năng:", "Thời gian phản hồi, độ chính xác, khả năng hoạt động khi mất mạng, bảo mật, sao lưu, khả năng truy vết và dễ sử dụng tại trạm gác."),
            ("Ma trận truy vết:", "Mỗi yêu cầu liên kết tới Use Case, lớp dữ liệu, API, màn hình, test case và người phụ trách."),
            ("Đầu ra:", "Tài liệu yêu cầu, hai sơ đồ Use Case, đặc tả đầy đủ, sơ đồ hoạt động/tuần tự, lớp phân tích và ma trận truy vết."),
        ],
    ),
    (
        "Nhiệm vụ 4 - Thiết kế hệ thống, dữ liệu và giao diện",
        [
            ("Kiến trúc tổng thể:", "Web/WinForms gọi Spring Boot REST API; Spring Boot truy cập MySQL và gọi AI service; ảnh lưu trong vùng tệp có kiểm soát."),
            ("Thiết kế mô-đun Web:", "Xác thực, cư dân-căn hộ, phương tiện-thẻ-gói, bảng giá, lịch sử, thanh toán, cảnh báo, báo cáo và cấu hình."),
            ("Thiết kế mô-đun Desktop:", "Camera vào-ra, thông tin thẻ, kết quả AI, bảng đối chiếu, cảnh báo, nút chụp lại, xác nhận và mở cổng mô phỏng."),
            ("Thiết kế mô-đun AI:", "Nhận media, kiểm tra chất lượng, phát hiện biển, OCR, phát hiện mặt, liveness, so khớp, phân loại xe và trả độ tin cậy."),
            ("Nhóm bảng danh mục:", "users, roles, apartments, residents, household_members, vehicles, vehicle_authorizations, cards, monthly_passes và price_rules."),
            ("Nhóm bảng giao dịch:", "parking_sessions, recognition_results, media_files, payments, charging_sessions, alerts và audit_logs."),
            ("Ràng buộc dữ liệu:", "Biển số chuẩn hóa duy nhất theo xe đang hoạt động; thẻ không trùng; một xe chỉ có tối đa một lượt OPEN; trạng thái chuyển hợp lệ."),
            ("Chỉ mục:", "Tối ưu tra cứu theo biển số, mã thẻ, apartment_id, resident_id, trạng thái lượt và thời gian vào/ra."),
            ("Lưu ảnh:", "Tổ chức thư mục theo ngày/lượt; tên tệp không chứa thông tin nhạy cảm; lưu hash, loại ảnh, thời gian, camera và quyền truy cập."),
            ("Thiết kế API:", "Quy ước URL, HTTP method, JSON, phân trang, lọc, mã lỗi, correlation ID, timeout và versioning."),
            ("Giao diện Web:", "Menu rõ theo nghiệp vụ; form có kiểm tra; danh sách có lọc; dashboard có số liệu và liên kết xuống dữ liệu chi tiết."),
            ("Giao diện Desktop:", "Ưu tiên camera và quyết định vận hành; màu trạng thái nhất quán; nút nguy hiểm phải xác nhận; lỗi phải hướng dẫn bước tiếp theo."),
            ("Thiết kế bảo mật:", "Mật khẩu băm, token/session, RBAC, giới hạn API, mã hóa truyền, ẩn dữ liệu nhạy cảm và AuditLog."),
            ("Thiết kế dự phòng:", "Health check, retry giới hạn, cache/hàng đợi cục bộ cho lượt chưa gửi, đồng bộ lại và ngăn tạo trùng."),
            ("Đầu ra:", "Sơ đồ kiến trúc, lớp thiết kế, ERD, data dictionary, tài liệu API, wireframe và quy tắc giao diện."),
        ],
    ),
    (
        "Nhiệm vụ 5 - Xây dựng API và chức năng dùng chung",
        [
            ("Xác thực:", "Đăng nhập, đăng xuất, đổi mật khẩu, hết phiên, khóa tài khoản sau số lần sai và khôi phục theo quy trình quản trị."),
            ("Phân quyền:", "Ban quản lý được cấu hình và báo cáo; nhân viên trạm gác chỉ xem dữ liệu cần thiết và thao tác lượt/cảnh báo."),
            ("CRUD nền tảng:", "Người dùng, vai trò, căn hộ, cư dân, thành viên, xe, thẻ, gói, bảng giá và cấu hình camera/cổng."),
            ("Kiểm tra dữ liệu:", "Bắt buộc trường cần thiết, chuẩn hóa biển số-số điện thoại, kiểm tra ngày, trạng thái, giá không âm và bản ghi trùng."),
            ("Nhóm API:", "/auth, /users, /apartments, /residents, /household-members, /vehicles, /cards, /passes, /prices, /gate, /sessions, /payments, /alerts và /reports."),
            ("Phản hồi API:", "Trả mã HTTP đúng, mã lỗi nghiệp vụ, thông báo cho người dùng, chi tiết kỹ thuật trong log và correlation ID để truy vết."),
            ("Giao dịch:", "Tạo/đóng lượt, tính phí, thanh toán và cập nhật chỗ trống phải dùng transaction để tránh dữ liệu nửa chừng."),
            ("Tích hợp AI:", "Spring/desktop gửi tệp hoặc đường dẫn an toàn; nhận kết quả, model version, thời gian xử lý và độ tin cậy."),
            ("Tích hợp Desktop:", "HttpClient có timeout, hủy yêu cầu, retry giới hạn, hiển thị trạng thái API/AI và không khóa giao diện khi xử lý."),
            ("AuditLog:", "Ghi ai-thời gian-thiết bị-hành động-dữ liệu trước/sau đối với phê duyệt, thay giá, mở cổng thủ công, xóa ảnh và xuất báo cáo."),
            ("Health check:", "Kiểm tra Spring, MySQL, AI, vùng ảnh và camera; Desktop hiển thị rõ thành phần nào đang lỗi."),
            ("Tiêu chí hoàn thành:", "Postman và giao diện chạy đủ luồng đúng/sai; Web-Desktop thấy cùng dữ liệu; không tạo trùng khi gửi lại yêu cầu."),
        ],
    ),
    (
        "Nghiệp vụ NV01 - Đăng ký căn hộ, cư dân, thành viên gia đình, phương tiện và thẻ",
        [
            ("Mục đích:", "Tạo hồ sơ nền để xác định ai được phép sử dụng phương tiện nào và được áp dụng quyền lợi cư dân nào."),
            ("Tác nhân chính:", "Quản trị viên/Ban quản lý."),
            ("Tác nhân liên quan:", "Cư dân/chủ căn hộ, thành viên hộ gia đình và dịch vụ AI khuôn mặt."),
            ("Dữ liệu đầu vào:", "Thông tin căn hộ, thông tin cần thiết từ căn cước, quan hệ gia đình, ảnh giấy tờ, ảnh đăng ký, ảnh realtime và giấy đăng ký xe."),
            ("Điều kiện trước:", "Căn hộ có thật; người đăng ký được thông báo mục đích xử lý dữ liệu và đồng ý theo quy định."),
            ("Bước 1:", "Ban quản lý tìm căn hộ; nếu chưa có thì tạo mã tòa-tầng-căn, chủ hộ và trạng thái sử dụng."),
            ("Bước 2:", "Nhập cư dân chính; chuẩn hóa thông tin; kiểm tra trùng theo định danh nội bộ và thông tin giấy tờ."),
            ("Bước 3:", "Thêm vợ/chồng/con hoặc thành viên khác; ghi quan hệ, thời hạn cư trú và trạng thái được phép sử dụng xe."),
            ("Bước 4:", "Chụp/nhập ảnh chân dung trên căn cước; chỉ lấy trường cần thiết, không công khai toàn bộ giấy tờ trên giao diện."),
            ("Bước 5:", "Chụp ảnh khi tạo hồ sơ và ảnh realtime; kiểm tra có đúng một khuôn mặt, đủ sáng, đủ nét và có dấu hiệu sống."),
            ("Bước 6:", "Thực hiện ba so sánh: CCCD-ảnh đăng ký, ảnh đăng ký-realtime và CCCD-realtime; lưu điểm và ngưỡng quyết định."),
            ("Bước 7:", "Nhập xe: biển số, chủ xe, loại giấy tờ, hãng/dòng, nhiên liệu/điện, dung tích xi-lanh hoặc thông số điện."),
            ("Bước 8:", "Gán danh sách thành viên được dùng từng xe; việc thuộc cùng hộ không tự động cho quyền với mọi xe."),
            ("Bước 9:", "Cấp thẻ, liên kết xe và lựa chọn gói 30 ngày phù hợp; hiển thị ngày bắt đầu-hết hạn và quyền sạc."),
            ("Ngoại lệ 1:", "Ảnh không đạt hoặc ba phép so sánh mâu thuẫn: yêu cầu chụp lại; nếu vẫn không đạt thì chuyển chờ xác minh."),
            ("Ngoại lệ 2:", "Biển số/thẻ đã tồn tại hoặc giấy tờ không khớp: không kích hoạt, tạo cảnh báo trùng và yêu cầu kiểm tra."),
            ("Ngoại lệ 3:", "Người nhà chưa chứng minh quan hệ hoặc quyền sử dụng xe: lưu hồ sơ nhưng không cấp quyền qua cổng."),
            ("Dữ liệu lưu:", "Hồ sơ, liên kết hộ-xe-người, mẫu đặc trưng khuôn mặt, điểm so khớp, tệp ảnh, người duyệt và lịch sử thay đổi."),
            ("Kết quả:", "Hồ sơ ACTIVE khi đạt kiểm tra hoặc được duyệt thủ công có lý do; thẻ/gói chỉ hoạt động sau khi thanh toán."),
            ("Tiêu chí chấp nhận:", "Có thể truy ngược từ thẻ/biển số đến căn hộ, xe và danh sách người được dùng; không tạo bản ghi trùng."),
        ],
    ),
    (
        "Nghiệp vụ NV02 - Quản lý thẻ, gói gửi xe 30 ngày, bảng giá và thanh toán",
        [
            ("Mục đích:", "Quản lý quyền ra vào theo thời hạn và tính phí nhất quán, có thể đối soát."),
            ("Tác nhân:", "Ban quản lý; nhân viên trạm gác chỉ xem trạng thái cần thiết."),
            ("Dữ liệu đầu vào:", "Xe/cư dân đã duyệt, mã thẻ, nhóm giá, ngày bắt đầu, thời hạn 30 ngày, số tiền và phương thức thanh toán."),
            ("Điều kiện trước:", "Xe thuộc căn hộ hợp lệ và không vượt giới hạn số xe theo chính sách."),
            ("Bước 1:", "Tạo PriceRule theo cư dân/khách, loại xe, dung tích từ giấy đăng ký, khung giờ, lượt hoặc gói."),
            ("Bước 2:", "Khi đăng ký gói, hệ thống chọn đúng nhóm xe và hiển thị công thức, số tiền, ngày hiệu lực, ngày hết hạn."),
            ("Bước 3:", "Ghi nhận thanh toán; chỉ chuyển gói ACTIVE khi số tiền hợp lệ hoặc có phê duyệt công nợ."),
            ("Bước 4:", "Kích hoạt thẻ; bảo đảm một mã thẻ chỉ thuộc một hồ sơ hoạt động tại cùng thời điểm."),
            ("Bước 5:", "Gia hạn tạo kỳ mới nối tiếp kỳ cũ; không ghi đè lịch sử và không làm mất quyền chưa sử dụng."),
            ("Bước 6:", "Cho phép tạm khóa do mất thẻ, vi phạm, xe sửa chữa hoặc yêu cầu cư dân; lưu lý do và người thao tác."),
            ("Bước 7:", "Thay thẻ phải vô hiệu thẻ cũ trước khi thẻ mới hoạt động."),
            ("Ngoại lệ 1:", "Gói chồng lấn, thẻ trùng, số tiền âm/sai hoặc xe vượt hạn mức: từ chối và chỉ rõ trường lỗi."),
            ("Ngoại lệ 2:", "Gói hết hạn tại cổng: không tự mở; cho phép nhân viên hướng dẫn gia hạn hoặc xử lý theo chính sách khách."),
            ("Quy tắc phí:", "Kết quả tính phí phải lưu PriceRule, tham số, thời lượng, mức làm tròn, giảm trừ và tổng cuối."),
            ("Dữ liệu lưu:", "Lịch sử thẻ, các kỳ gói, hóa đơn/biên nhận, giao dịch, người thu và trạng thái đối soát."),
            ("Kết quả:", "Thẻ-gói-thanh toán có trạng thái nhất quán; Web liệt kê gói sắp hết hạn và thẻ đang khóa."),
            ("Tiêu chí chấp nhận:", "Gia hạn, khóa, thay thẻ và hoàn/hủy không làm sai lịch sử; tổng tiền chi tiết khớp báo cáo."),
        ],
    ),
    (
        "Nghiệp vụ NV03 - Kiểm soát xe cư dân vào bãi",
        [
            ("Mục đích:", "Cho xe hợp lệ vào nhanh nhưng không bỏ qua kiểm tra người lái, xe, thẻ và gói."),
            ("Tác nhân chính:", "Nhân viên trạm gác."),
            ("Tác nhân hệ thống:", "Camera biển số, camera khuôn mặt, đầu đọc thẻ, Desktop, Spring API, AI và MySQL."),
            ("Dữ liệu đầu vào:", "Khung hình/video, mã thẻ, biển số OCR, loại xe AI, khuôn mặt realtime và thời gian/cổng."),
            ("Điều kiện trước:", "Cổng ở chế độ nhận xe; chưa có lượt khác đang xử lý cho cùng làn."),
            ("Bước 1:", "Cảm biến/nút mô phỏng kích hoạt; Desktop lấy nhiều khung hình từ camera biển số và camera mặt."),
            ("Bước 2:", "Quality gate chọn khung tốt; nếu mờ/cháy/che thì yêu cầu chụp lại trước khi nhận dạng."),
            ("Bước 3:", "YOLO phát hiện biển số; OCR nhận ký tự; chuẩn hóa bỏ ký tự thừa và đưa về định dạng tra cứu."),
            ("Bước 4:", "AI phân loại nhóm phương tiện; kết quả chỉ dùng hỗ trợ, sau đó so với loại xe trong hồ sơ."),
            ("Bước 5:", "Phát hiện khuôn mặt, kiểm tra chất lượng và sống; tạo điểm so khớp với các mẫu được phép."),
            ("Bước 6:", "Nhân viên quét thẻ; API tìm Card, Vehicle, MonthlyPass, Apartment và VehicleAuthorization."),
            ("Bước 7:", "Kiểm tra thẻ ACTIVE, gói còn hạn, biển số đúng, loại xe phù hợp, xe chưa ở trong bãi và không bị khóa."),
            ("Bước 8:", "Kiểm tra người lái là chủ xe hoặc thành viên hộ gia đình được gán quyền với chính xe đó."),
            ("Bước 9:", "Đối với cư dân/người nhà, so realtime với ảnh đăng ký và mẫu tham chiếu CCCD đã được bảo vệ; không yêu cầu xuất trình CCCD mỗi lần."),
            ("Bước 10:", "Desktop hiển thị bảng ĐẠT/KHÔNG ĐẠT cho biển số, mặt, thẻ, gói, loại xe và quyền người lái."),
            ("Bước 11:", "Khi tất cả kiểm tra bắt buộc đạt, tạo ParkingSession trạng thái OPEN trong transaction."),
            ("Bước 12:", "Lưu ảnh toàn cảnh, ảnh biển, ảnh mặt, kết quả AI, model version, thời gian, cổng và nhân viên."),
            ("Bước 13:", "Gửi lệnh mở barrier mô phỏng; khóa nút xác nhận để sự kiện không tạo lượt lần hai."),
            ("Ngoại lệ 1:", "Sai biển, sai mặt, sai loại xe, thẻ/gói hết hạn hoặc người không được ủy quyền: giữ cổng đóng và tạo Alert."),
            ("Ngoại lệ 2:", "API/AI mất kết nối: chuyển chế độ dự phòng NV07; không tự xem kết quả cũ là hợp lệ."),
            ("Ngoại lệ 3:", "Xe đã có ParkingSession OPEN: cảnh báo lượt trùng và yêu cầu kiểm tra lịch sử."),
            ("Kết quả:", "Một lượt OPEN duy nhất, đủ bằng chứng và quyết định mở/từ chối cổng."),
            ("Tiêu chí chấp nhận:", "Phản hồi trong ngưỡng cấu hình; gửi lại yêu cầu không tạo trùng; mọi kiểm tra hiển thị rõ cho nhân viên."),
        ],
    ),
    (
        "Nghiệp vụ NV04 - Kiểm soát xe cư dân ra khỏi bãi",
        [
            ("Mục đích:", "Đảm bảo đúng xe và đúng người được phép lấy xe; đóng đúng lượt vào."),
            ("Tác nhân:", "Nhân viên trạm gác, camera, Desktop, API và AI."),
            ("Dữ liệu đầu vào:", "Biển số, khuôn mặt và loại xe lúc ra; mã thẻ; ParkingSession OPEN và bằng chứng lúc vào."),
            ("Điều kiện trước:", "Xe đang có một lượt OPEN hoặc có quy trình xử lý không tìm thấy lượt."),
            ("Bước 1:", "Chụp nhiều khung hình lúc ra; chọn ảnh đạt chất lượng."),
            ("Bước 2:", "Nhận dạng biển số, khuôn mặt, loại xe và đọc thẻ."),
            ("Bước 3:", "Tìm ParkingSession OPEN theo biển số chuẩn hóa; dùng thẻ/thời gian/cổng để thu hẹp khi có nhiều ứng viên."),
            ("Bước 4:", "So biển số vào-ra; so loại xe vào-ra và với hồ sơ đăng ký."),
            ("Bước 5:", "So khuôn mặt ra với khuôn mặt lúc vào, ảnh đăng ký và danh sách người được ủy quyền."),
            ("Bước 6:", "Kiểm tra gói còn hiệu lực, công nợ, cảnh báo chưa xử lý và tình trạng thẻ."),
            ("Bước 7:", "Nếu gói tháng hợp lệ, phí lượt bằng 0 hoặc theo phụ phí cấu hình; vẫn lưu chi tiết tính phí."),
            ("Bước 8:", "Desktop hiển thị ảnh vào-ra cạnh nhau và từng điều kiện ĐẠT/KHÔNG ĐẠT."),
            ("Bước 9:", "Nhân viên xác nhận; API ghi giờ ra, thời lượng, kết quả AI và chuyển lượt CLOSED trong transaction."),
            ("Bước 10:", "Mở barrier mô phỏng và đánh dấu sự kiện đã xử lý để không đóng lượt lần hai."),
            ("Ngoại lệ 1:", "Người lấy xe khác nhưng thuộc hộ và có quyền: cho phép khi so khớp đạt, đồng thời ghi người thực tế lấy xe."),
            ("Ngoại lệ 2:", "Người lấy xe không thuộc danh sách, mặt/biển không khớp hoặc thẻ mất: chuyển NV07, không tự mở."),
            ("Ngoại lệ 3:", "Không tìm thấy lượt vào: tìm theo khoảng thời gian/ảnh; nếu vẫn không có thì lập biên bản xử lý thủ công."),
            ("Dữ liệu lưu:", "Ảnh ra, điểm so khớp, thời lượng, phí, người xác nhận, lý do thủ công và trạng thái cuối."),
            ("Kết quả:", "Đóng đúng lượt, có bằng chứng vào-ra và cập nhật sức chứa bãi."),
            ("Tiêu chí chấp nhận:", "Không đóng nhầm/đóng hai lần; ảnh vào-ra truy cập được theo quyền; dữ liệu doanh thu khớp lượt."),
        ],
    ),
    (
        "Nghiệp vụ NV05 - Khách vãng lai vào, ra và thu phí",
        [
            ("Mục đích:", "Phục vụ khách không có hồ sơ cư dân nhưng vẫn đủ bằng chứng để đối chiếu khi ra."),
            ("Tác nhân:", "Khách và Nhân viên trạm gác."),
            ("Dữ liệu đầu vào:", "Ảnh biển số, khuôn mặt realtime, loại xe, ảnh toàn cảnh, mã thẻ/mã lượt khách và thời gian."),
            ("Điều kiện trước:", "Xe không được nhận diện là xe cư dân ACTIVE hoặc nhân viên chọn loại lượt KHÁCH."),
            ("Bước vào 1:", "Chụp biển số, mặt và toàn cảnh; kiểm tra chất lượng, nhận dạng và cho nhân viên sửa kết quả OCR nếu có lý do."),
            ("Bước vào 2:", "Ghi loại xe; không yêu cầu căn cước hoặc tạo mẫu cư dân dài hạn."),
            ("Bước vào 3:", "Cấp mã lượt/thẻ khách; tạo ParkingSession OPEN loại VISITOR và lưu bằng chứng."),
            ("Bước vào 4:", "Thông báo quy định, bảng giá và cách xử lý mất thẻ cho khách."),
            ("Bước vào 5:", "Mở barrier sau khi nhân viên xác nhận dữ liệu tối thiểu."),
            ("Bước ra 1:", "Chụp lại biển số, mặt và loại xe; tìm lượt bằng biển số/thẻ khách."),
            ("Bước ra 2:", "So biển số, khuôn mặt và loại xe giữa vào-ra; hiển thị ảnh cạnh nhau."),
            ("Bước ra 3:", "Tính thời lượng; chọn PriceRule; hiển thị từng thành phần phí và tổng."),
            ("Bước ra 4:", "Nhân viên ghi phương thức thanh toán, số tiền nhận và mã giao dịch nếu có."),
            ("Bước ra 5:", "Đóng lượt, tạo Payment/biên nhận và mở barrier."),
            ("Ngoại lệ 1:", "Mất thẻ: tìm bằng biển số/ảnh; áp dụng quy trình và phụ phí đã khảo sát, không tự đặt mức phạt."),
            ("Ngoại lệ 2:", "Đổi/mờ biển, mặt bị che hoặc người khác lấy xe: yêu cầu chứng minh quyền sở hữu, lập Alert và xác nhận thủ công."),
            ("Ngoại lệ 3:", "Không tìm thấy lượt: không thu phí tùy ý; chuyển quản lý kiểm tra log/ảnh và lập biên bản."),
            ("Dữ liệu lưu:", "Lượt, ảnh vào-ra, kết quả đối chiếu, chi tiết phí, thanh toán, người thu và ngoại lệ."),
            ("Kết quả:", "Khách ra đúng xe; khoản thu gắn với đúng lượt và xuất hiện trong báo cáo."),
            ("Tiêu chí chấp nhận:", "Tổng phí tái tính được từ dữ liệu; không thể đóng lượt khi chưa xác nhận thanh toán hoặc miễn phí có lý do."),
        ],
    ),
    (
        "Nghiệp vụ NV06 - Phân loại phương tiện và quản lý quyền sạc xe điện",
        [
            ("Mục đích:", "Xác định nhóm xe để áp dụng quy định/giá và kiểm tra quyền sử dụng khu vực sạc."),
            ("Tác nhân:", "Ban quản lý, Nhân viên trạm gác và dịch vụ AI."),
            ("Nhóm AI hỗ trợ:", "Xe đạp, xe đạp điện, xe máy, xe máy điện, ô tô và ô tô điện."),
            ("Giới hạn kỹ thuật:", "Không suy luận chính xác dung tích xi-lanh chỉ bằng ảnh; lấy dung tích từ giấy đăng ký và nhân viên xác minh."),
            ("Dữ liệu hồ sơ:", "Hãng, dòng, biển số, loại phương tiện, nhiên liệu, dung tích, công suất/thông số điện và ảnh xe."),
            ("Bước đăng ký 1:", "AI đề xuất nhóm xe từ ảnh; nhân viên đối chiếu giấy tờ và xác nhận giá trị chính thức."),
            ("Bước đăng ký 2:", "Hệ thống ánh xạ nhóm xe chính thức tới PriceRule và chính sách số xe/căn hộ."),
            ("Bước đăng ký 3:", "Nếu xe điện, lưu quyền sạc, loại đầu sạc/khu được phép và thời hạn quyền."),
            ("Bước tại cổng:", "So nhóm AI realtime với hồ sơ; khác biệt vượt ngưỡng tạo cảnh báo nhưng không tự sửa hồ sơ."),
            ("Bước sạc 1:", "Kiểm tra xe đã đăng ký, gói/quyền sạc còn hiệu lực và không bị khóa."),
            ("Bước sạc 2:", "Kiểm tra loại vị trí, khả năng tương thích và còn chỗ; MVP có thể mô phỏng trạng thái vị trí."),
            ("Bước sạc 3:", "Tạo ChargingSession với xe, cư dân, vị trí, giờ bắt đầu và người xác nhận."),
            ("Bước sạc 4:", "Khi kết thúc, ghi giờ kết thúc và trạng thái; điện năng/chi phí chỉ ghi nếu có dữ liệu phần cứng đáng tin cậy."),
            ("Ngoại lệ 1:", "Xe xăng vào khu sạc, xe điện chưa đăng ký, hết quyền hoặc vị trí đầy: từ chối và cảnh báo."),
            ("Ngoại lệ 2:", "AI phân loại sai: nhân viên chọn theo giấy tờ; lưu cả dự đoán AI và giá trị xác minh để đánh giá."),
            ("Dữ liệu lưu:", "Dự đoán, độ tin cậy, loại chính thức, người xác minh, quyền sạc và lịch sử phiên sạc."),
            ("Kết quả:", "Áp đúng nhóm giá; xe điện hợp lệ mới được tạo phiên sạc."),
            ("Tiêu chí chấp nhận:", "Mọi quyết định khác AI đều truy vết được; không quảng cáo điều khiển sạc thật khi chỉ mô phỏng."),
        ],
    ),
    (
        "Nghiệp vụ NV07 - Xử lý ảnh kém, sai lệch, mất kết nối và quyết định thủ công",
        [
            ("Mục đích:", "Duy trì an toàn khi AI không chắc chắn hoặc hạ tầng gặp lỗi."),
            ("Tác nhân:", "Nhân viên trạm gác; Ban quản lý duyệt trường hợp nghiêm trọng."),
            ("Điều kiện kích hoạt:", "Ảnh dưới ngưỡng, kết quả mâu thuẫn, thẻ/gói bất thường, API/AI/camera mất kết nối hoặc không tìm thấy lượt."),
            ("Chỉ số chất lượng:", "Độ sáng, tương phản, độ nét, góc, kích thước vùng biển/mặt, số khuôn mặt và tỷ lệ che khuất."),
            ("Giải pháp camera:", "WDR/HDR/IR, đèn tránh ngược sáng, camera biển và mặt riêng, cố định khoảng cách/tốc độ, thêm góc phụ."),
            ("Bước 1:", "Desktop hiển thị nguyên nhân cụ thể: quá tối, cháy, mờ, không thấy biển, nhiều mặt hoặc mặt bị che."),
            ("Bước 2:", "Tự chọn khung tốt nhất; có thể cân bằng sáng, tăng tương phản, deskew và thử OCR trên một số khung giới hạn."),
            ("Bước 3:", "Nếu chưa đạt, hướng dẫn xe dừng đúng vị trí, bỏ vật che hợp lệ và chụp lại."),
            ("Bước 4:", "Sau số lần cấu hình, dừng tự động; tạo Alert và giữ barrier đóng."),
            ("Bước 5:", "Nhân viên xem ảnh vào, hồ sơ, thẻ, quan hệ hộ gia đình và giấy tờ theo quy trình khảo sát."),
            ("Bước 6:", "Chọn CHO QUA hoặc TỪ CHỐI; bắt buộc nhập lý do và xác nhận lại thao tác."),
            ("Bước 7:", "Lưu quyết định thủ công riêng; không sửa kết quả AI thành ĐẠT để che sai lệch."),
            ("Mất API:", "Lưu tạm sự kiện có mã idempotency; chỉ cho xử lý theo quyền dự phòng; đồng bộ lại và phát hiện trùng khi kết nối trở lại."),
            ("Mất AI:", "Không dùng độ tin cậy giả; cho phép quy trình thủ công có bằng chứng hoặc tạm ngừng làn theo chính sách."),
            ("Mất camera:", "Chuyển camera dự phòng/ảnh thủ công; ghi thiết bị lỗi và thời gian gián đoạn."),
            ("Bảo vệ dữ liệu:", "Phân quyền ảnh, mã hóa truyền/lưu, giới hạn tải xuống, thời hạn lưu/xóa và dùng dữ liệu giả/làm mờ khi demo."),
            ("Dữ liệu lưu:", "Alert, ảnh, kết quả chất lượng, lần thử, thành phần lỗi, người quyết định, lý do và thời gian."),
            ("Kết quả:", "Sự cố được đóng hoặc chuyển Ban quản lý; mọi lần mở cổng thủ công đều truy vết."),
            ("Tiêu chí chấp nhận:", "Không có đường tắt mở cổng không log; sự kiện đồng bộ lại không tạo lượt/thanh toán trùng."),
        ],
    ),
    (
        "Nghiệp vụ NV08 - Tra cứu, cảnh báo, thống kê và báo cáo doanh thu",
        [
            ("Mục đích:", "Giúp Ban quản lý theo dõi vận hành, đối soát tiền và điều tra sự cố."),
            ("Tác nhân:", "Quản trị viên/Ban quản lý; nhân viên trạm gác chỉ xem phạm vi được cấp."),
            ("Điều kiện trước:", "Người dùng đăng nhập đúng quyền; dữ liệu lượt, thanh toán và cảnh báo đã được ghi."),
            ("Tra cứu lịch sử:", "Lọc theo biển số, thẻ, căn hộ, cư dân/khách, cổng, loại xe, trạng thái, cảnh báo và khoảng thời gian."),
            ("Chi tiết lượt:", "Hiển thị ảnh vào-ra, kết quả AI, bảng đối chiếu, phí, thanh toán, người thao tác và dòng thời gian."),
            ("Dashboard vận hành:", "Số xe trong bãi, sức chứa, lượt vào-ra, lượt OPEN lâu, cảnh báo chưa xử lý, API/AI/camera và vị trí sạc."),
            ("Dashboard đăng ký:", "Thẻ/gói sắp hết hạn, xe theo căn hộ, xe theo loại, xe điện, hồ sơ chờ duyệt và hồ sơ ảnh cần chụp lại."),
            ("Báo cáo doanh thu:", "Theo ngày/tháng, cư dân/khách, loại xe, PriceRule, cổng, phương thức và nhân viên thu."),
            ("Quy tắc tổng hợp:", "Không tính giao dịch hủy/chưa thanh toán; doanh thu phải đối chiếu được Payment-ParkingSession-PriceRule."),
            ("Báo cáo ngoại lệ:", "Mất thẻ, mở cổng thủ công, sai biển/mặt, ảnh kém, lượt không đóng, giảm/miễn phí và chênh lệch đối soát."),
            ("Xuất dữ liệu:", "CSV/Excel/PDF nếu đủ thời gian; file phải ghi khoảng lọc, thời điểm xuất và người xuất."),
            ("Phân quyền:", "Không hiển thị/tải ảnh hoặc thông tin căn cước cho vai trò không cần thiết; ghi log xem/xuất dữ liệu nhạy cảm."),
            ("Ngoại lệ:", "Dữ liệu thiếu/không cân đối thì không cho chốt báo cáo; tạo cảnh báo và liên kết tới bản ghi gây lệch."),
            ("Kết quả:", "Số tổng và chi tiết khớp; người quản lý đi từ biểu đồ xuống đúng giao dịch."),
            ("Tiêu chí chấp nhận:", "Bộ dữ liệu kiểm thử có tổng biết trước; báo cáo trả đúng kết quả và đúng quyền."),
        ],
    ),
    (
        "Nhiệm vụ 14 - Kiểm thử, triển khai, tài liệu và báo cáo",
        [
            ("Cơ sở kiểm thử:", "Dùng ma trận truy vết để bảo đảm mọi yêu cầu và nhánh ngoại lệ có test case."),
            ("Kiểm thử đơn vị:", "Service tính phí, chuẩn hóa biển, chuyển trạng thái lượt, kiểm tra gói, quyền hộ gia đình và tổng hợp báo cáo."),
            ("Kiểm thử API:", "Xác thực, phân quyền, validation, mã lỗi, idempotency, phân trang, lọc, upload ảnh và transaction."),
            ("Kiểm thử tích hợp:", "Web-API-MySQL, Desktop-API-AI, lưu ảnh, timeout, retry, mất kết nối và đồng bộ lại."),
            ("Kiểm thử nghiệp vụ:", "Chạy trọn NV01-NV08 với cả luồng đúng, sai và xử lý thủ công."),
            ("Kiểm thử ANPR:", "Precision/Recall phát hiện, độ chính xác ký tự/biển, ngày-đêm, nghiêng, mờ, cháy và che khuất."),
            ("Kiểm thử khuôn mặt:", "Tỷ lệ so đúng, FAR/FRR, liveness, người trong gia đình, khẩu trang/kính và ánh sáng khác nhau."),
            ("Kiểm thử hiệu năng:", "Thời gian trung bình/P95 cho nhận diện, API cổng và báo cáo; số lượt đồng thời phù hợp phạm vi demo."),
            ("Kiểm thử bảo mật:", "Truy cập sai quyền, IDOR, upload tệp, dữ liệu nhạy cảm trong log, mật khẩu, token và file xuất."),
            ("Kiểm thử dữ liệu:", "Giao dịch đồng thời, lượt trùng, thẻ trùng, gói chồng, báo cáo lệch, backup và restore."),
            ("Triển khai:", "MySQL trên Laragon; Spring Boot; AI service; WinForms; cấu hình cổng/camera, thư mục ảnh, tài khoản và dữ liệu demo."),
            ("Kịch bản demo:", "Đăng ký cư dân-người nhà-xe; cư dân vào-ra; người nhà lấy xe; khách; xe điện; ảnh kém; sai mặt; báo cáo doanh thu."),
            ("Dữ liệu demo:", "Dùng dữ liệu giả/được đồng ý; làm mờ giấy tờ; không đưa dữ liệu cá nhân thật lên mã nguồn hoặc slide."),
            ("Tài liệu bàn giao:", "Mã nguồn, database script, cấu hình mẫu, hướng dẫn cài đặt/sử dụng, API, test report, dataset description và log minh chứng."),
            ("Báo cáo khóa luận:", "Trình bày khảo sát, yêu cầu, phân tích, thiết kế, cài đặt, kiểm thử, đánh giá, hạn chế và hướng phát triển."),
            ("Tiêu chí hoàn thành:", "Máy trình diễn cài mới theo hướng dẫn và chạy được kịch bản; kết quả trong báo cáo khớp sản phẩm thực tế."),
        ],
    ),
]


SCHEDULE = [
    ("Khảo sát hiện trạng, biểu mẫu, camera, bảng giá và quy trình.", "Biên bản khảo sát; AS-IS; danh sách vấn đề và giả định.", "Cả nhóm; SV3 tổng hợp minh chứng."),
    ("Chốt phạm vi, 08 nghiệp vụ, yêu cầu chức năng/phi chức năng và backlog.", "SRS bản 1; WBS; ma trận trách nhiệm; kế hoạch tích hợp.", "SV1 quản lý backlog; cả nhóm duyệt."),
    ("Lập Use Case nghiệp vụ/hệ thống, đặc tả, hoạt động, tuần tự và lớp phân tích.", "Bộ UML phân tích; đặc tả; ma trận truy vết bản 1.", "SV2 chủ trì luồng cổng; cả nhóm hoàn thiện."),
    ("Thiết kế kiến trúc, lớp, ERD/MySQL, API, bảo mật và wireframe.", "Sơ đồ thiết kế; data dictionary; API contract; giao diện mẫu.", "SV1 API-CSDL; SV2 UI; SV3 AI contract."),
    ("Xây nền CSDL, Spring API, xác thực, phân quyền, AuditLog; khung WinForms/AI.", "Phiên bản tích hợp đăng nhập và CRUD nền tảng.", "Mỗi SV hoàn thành mô-đun chính và review chéo."),
    ("Cài NV01-NV02: căn hộ, cư dân, người nhà, xe, quyền dùng, thẻ, gói và phí.", "Web/API/CSDL chạy được đăng ký-gia hạn-thanh toán.", "SV1 chính; SV2/SV3 kiểm thử tích hợp."),
    ("Cài Desktop và NV03-NV04: cư dân vào-ra, thẻ, lượt, đối chiếu và barrier.", "WinForms gọi API; kịch bản cư dân/người nhà chạy end-to-end.", "SV2 chính; SV1 hỗ trợ API."),
    ("Tích hợp ANPR, OCR và phân loại xe cho NV03-NV06.", "AI service; bộ dữ liệu thử; báo cáo chỉ số ban đầu.", "SV3 chính; SV2 tích hợp Desktop."),
    ("Tích hợp khuôn mặt, liveness, ba nguồn ảnh, khách NV05 và ngoại lệ NV07.", "Luồng khuôn mặt-người nhà-khách-ảnh kém chạy được.", "SV3 chính; SV1/SV2 hỗ trợ dữ liệu và UI."),
    ("Hoàn thiện NV06-NV08: quyền sạc, cảnh báo, tra cứu, doanh thu và dashboard.", "Báo cáo đối soát; cảnh báo; lịch sử sạc mô phỏng.", "SV1 chính; cả nhóm chạy end-to-end."),
    ("Kiểm thử chức năng, API, tích hợp, AI, hiệu năng, bảo mật và backup-restore.", "Test report; danh sách lỗi; kết quả đánh giá và bản triển khai thử.", "Cả nhóm; mỗi SV sửa lỗi mô-đun phụ trách."),
    ("Đóng gói, hoàn thiện báo cáo, hướng dẫn, slide và luyện demo.", "Bản nộp cuối; PowerPoint; video/ảnh minh chứng; kịch bản bảo vệ.", "Cả nhóm."),
]


def rewrite_document_xml(xml_bytes):
    parser = etree.XMLParser(remove_blank_text=False)
    root = etree.fromstring(xml_bytes, parser)
    body = root.find(W + "body")
    paragraphs = body.findall(W + "p")
    tables = body.findall(W + "tbl")

    replace_labeled(
        paragraphs[5],
        "Tên đề tài:",
        "Xây dựng hệ thống quản lý và kiểm soát bãi đỗ xe chung cư tích hợp nhận diện biển số, khuôn mặt và phân loại phương tiện",
    )
    replace_labeled(
        paragraphs[14],
        "Mục tiêu:",
        "Khảo sát, phân tích, thiết kế và xây dựng hệ thống quản lý bãi xe chung cư trên Web và Desktop, dùng chung Spring Boot API, MySQL và dịch vụ AI. "
        "Hệ thống phải mô hình hóa rõ 08 nghiệp vụ, xử lý được người trong hộ gia đình dùng chung xe, khách vãng lai, xe điện, ảnh kém và báo cáo doanh thu; "
        "đồng thời có kiểm thử, triển khai, tài liệu và minh chứng phù hợp nhóm 3 sinh viên trong 12 tuần.",
    )
    replace_labeled(
        paragraphs[16],
        "Sản phẩm tối thiểu:",
        "Tài liệu khảo sát; UML; ERD; API; Web; WinForms; AI service; MySQL; dữ liệu thử; test report; hướng dẫn; báo cáo và PowerPoint.",
    )
    replace_labeled(
        paragraphs[17],
        "Nghiệp vụ đặc trưng:",
        "Đối chiếu biển số-thẻ-loại xe-khuôn mặt-quyền thành viên-gói gửi xe ở cổng, có ngưỡng tin cậy và quy trình thủ công dự phòng.",
    )
    replace_labeled(
        paragraphs[19],
        "Cấu trúc nội dung:",
        "Các nhiệm vụ dưới đây đi đúng thứ tự bảng chấm điểm; mỗi nghiệp vụ được tách thành các trường và các bước riêng để có thể dùng trực tiếp khi lập Use Case và test case.",
    )

    heading_template = deepcopy(paragraphs[22])
    detail_template = deepcopy(paragraphs[24])

    build_block(paragraphs[20], BLOCKS[0][0], BLOCKS[0][1], detail_template)
    build_block(paragraphs[21], BLOCKS[1][0], BLOCKS[1][1], detail_template)
    anchor = build_block(paragraphs[22], BLOCKS[2][0], BLOCKS[2][1], detail_template)

    for title, items in BLOCKS[3:]:
        heading = deepcopy(heading_template)
        anchor.addnext(heading)
        anchor = build_block(heading, title, items, detail_template)

    replace_labeled(paragraphs[24], "Yêu cầu về Ngôn ngữ:", "Java 21/Spring Boot; C#/.NET WinForms; Python; HTML, CSS, JavaScript và Thymeleaf.")
    replace_labeled(paragraphs[25], "Yêu cầu về hệ quản trị:", "MySQL chạy trong Laragon; có khóa, ràng buộc, chỉ mục, transaction, backup-restore và vùng lưu ảnh có kiểm soát.")
    replace_labeled(paragraphs[26], "Yêu cầu khác:", "REST API JSON; JPA/Hibernate; HttpClient; FastAPI/OpenCV/YOLO/OCR/nhận diện khuôn mặt; Git, UML, Postman; camera hoặc media mô phỏng.")
    replace_paragraph(paragraphs[31], "Các tài liệu chính dự kiến sử dụng trong quá trình thực hiện đề tài:")
    replace_paragraph(paragraphs[32], "Quốc hội, Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15, 2025; OMG UML Specification; Spring Boot Reference; Windows Forms Documentation; MySQL Reference Manual.")
    replace_paragraph(paragraphs[33], "Ultralytics YOLO Documentation; R. Smith, Tesseract OCR Engine, 2007; J. Deng và cộng sự, ArcFace, 2019; Z. Yu và cộng sự, Face Anti-Spoofing, 2020; OpenCV Documentation.")

    keep_with_next(paragraphs[38])
    keep_with_next(paragraphs[39])

    rows = tables[1].findall(W + "tr")
    for row_index, (work, product, owner) in enumerate(SCHEDULE, start=1):
        cells = rows[row_index].findall(W + "tc")
        replace_paragraph(first_paragraph(cells[0]), "")
        cell = cells[1]
        base = first_paragraph(cell)
        replace_labeled(base, "Công việc:", work)
        append_cell_paragraph(cell, base, "Sản phẩm:", product)
        append_cell_paragraph(cell, base, "Phụ trách:", owner)

    return etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone="yes")


def rewrite_footer_xml(xml_bytes):
    parser = etree.XMLParser(remove_blank_text=False)
    root = etree.fromstring(xml_bytes, parser)
    page_paragraph = root.find(".//" + W + "p")
    ppr = page_paragraph.find(W + "pPr")
    indent = ppr.find(W + "ind")
    if indent is None:
        indent = etree.SubElement(ppr, W + "ind")
    indent.set(W + "right", "720")
    return etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone="yes")


def clone_with_replacement(source, output):
    with ZipFile(source, "r") as zin:
        document_xml = rewrite_document_xml(zin.read("word/document.xml"))
        footer_xml = rewrite_footer_xml(zin.read("word/footer1.xml"))
        with ZipFile(output, "w") as zout:
            for item in zin.infolist():
                if item.filename == "word/document.xml":
                    data = document_xml
                elif item.filename == "word/footer1.xml":
                    data = footer_xml
                else:
                    data = zin.read(item.filename)
                copied = ZipInfo(item.filename, date_time=item.date_time)
                copied.compress_type = item.compress_type if item.compress_type is not None else ZIP_DEFLATED
                copied.comment = item.comment
                copied.extra = item.extra
                copied.internal_attr = item.internal_attr
                copied.external_attr = item.external_attr
                copied.create_system = item.create_system
                copied.flag_bits = item.flag_bits
                zout.writestr(copied, data)


def main():
    actual_hash = sha256(SOURCE.read_bytes()).hexdigest()
    if actual_hash != EXPECTED_SOURCE_SHA256:
        raise RuntimeError(f"Reference changed: {actual_hash}")
    clone_with_replacement(SOURCE, OUTPUT)
    print(OUTPUT)


if __name__ == "__main__":
    main()
