from copy import deepcopy
from hashlib import sha256
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from lxml import etree

from fill_thesis_proposal_template import (
    EXPECTED_SOURCE_SHA256,
    SOURCE,
    W,
    first_paragraph,
    replace_paragraph,
)


OUTPUT = Path(r"D:\Code\KhoaLuan\De_cuong_chi_tiet_KLCN_Quan_ly_bai_do_xe_chung_cu_DAY_DU.docx")


def insert_requirement_after(anchor, text):
    paragraph = deepcopy(anchor)
    anchor.addnext(paragraph)
    replace_paragraph(paragraph, text)
    return paragraph


def keep_with_next(paragraph):
    ppr = paragraph.find(W + "pPr")
    if ppr is None:
        ppr = etree.Element(W + "pPr")
        paragraph.insert(0, ppr)
    if ppr.find(W + "keepNext") is None:
        etree.SubElement(ppr, W + "keepNext")


def rewrite_document_xml(xml_bytes):
    parser = etree.XMLParser(remove_blank_text=False)
    root = etree.fromstring(xml_bytes, parser)
    body = root.find(W + "body")
    paragraphs = body.findall(W + "p")
    tables = body.findall(W + "tbl")

    replacements = {
        5: (
            "Tên đề tài: Xây dựng hệ thống quản lý và kiểm soát bãi đỗ xe chung cư tích hợp nhận diện biển số, khuôn mặt và phân loại phương tiện",
            "Tên đề tài:",
        ),
        14: (
            "Mục tiêu: Khảo sát cơ cấu vận hành, quy định, biểu mẫu, thiết bị và vấn đề của bãi xe chung cư để xác định tác nhân, dữ liệu, yêu cầu và quy trình hiện tại/đề xuất. "
            "Phân tích và thiết kế hệ thống bằng Use Case, đặc tả, sơ đồ lớp, mô hình dữ liệu, giao diện và kiến trúc tích hợp. Xây dựng Web Spring Boot cho Ban quản lý, "
            "Desktop WinForms cho trạm gác, dịch vụ AI Python và MySQL dùng chung. Hệ thống thực hiện 08 nghiệp vụ về cư dân-hộ gia đình, phương tiện-thẻ-gói 30 ngày, "
            "xe cư dân vào/ra, khách và thu phí, phân loại xe-quyền sạc, xử lý sự cố, cảnh báo và báo cáo. Luồng phức tạp tại cổng kết hợp ANPR/OCR, khuôn mặt, kiểm tra sống, "
            "quyền dùng xe và xử lý ngoại lệ. Sản phẩm được kiểm thử chức năng, API, tích hợp, hiệu năng, an toàn dữ liệu và chất lượng AI; có hướng dẫn, báo cáo và minh chứng. "
            "Ba sinh viên làm việc theo phân công, tích hợp hằng tuần, tự học công nghệ và chịu trách nhiệm chung về sản phẩm.",
            "Mục tiêu:",
        ),
        16: (
            "Kết quả tối thiểu phải đạt: bộ tài liệu khảo sát và yêu cầu; mô hình UML và cơ sở dữ liệu; đặc tả REST API; giao diện Web/Desktop; 08 nghiệp vụ hoàn chỉnh; "
            "mô-đun ANPR, khuôn mặt và phân loại phương tiện; bộ dữ liệu thử nghiệm; kịch bản kiểm thử; báo cáo đánh giá; bản cài đặt chạy được trên máy trình diễn.",
            None,
        ),
        17: (
            "Phạm vi phân công dự kiến: SV1 phụ trách Web Spring Boot, REST API, bảo mật và cơ sở dữ liệu; SV2 phụ trách Desktop WinForms, camera và luồng vận hành cổng; "
            "SV3 phụ trách dịch vụ AI, dữ liệu thử nghiệm và đánh giá mô hình. Cả nhóm cùng khảo sát, phân tích, thiết kế, kiểm thử tích hợp, viết báo cáo và chuẩn bị thuyết trình. "
            "Mỗi chức năng chỉ được xem là hoàn thành khi có mã nguồn đã tích hợp, dữ liệu kiểm thử, kết quả chạy, nhật ký lỗi và tài liệu cập nhật.",
            None,
        ),
        19: (
            "Các nội dung cụ thể cần thực hiện: nội dung được sắp xếp theo đúng trình tự của bảng nhiệm vụ gồm khảo sát nghiệp vụ; lập kế hoạch và phân công; phân tích hệ thống; "
            "thiết kế hệ thống, dữ liệu và giao diện; xây dựng API; xây dựng chức năng cơ bản; cài đặt 08 nghiệp vụ; kiểm thử, triển khai; hoàn thiện báo cáo và slide.",
            "Các nội dung cụ thể cần thực hiện:",
        ),
        20: (
            "Yêu cầu 1 - Khảo sát nghiệp vụ và mô hình hóa hiện trạng. Đối tượng khảo sát gồm Ban quản lý, nhân viên trạm gác, cư dân và khách; phạm vi gồm cổng vào/ra, "
            "bãi xe, vị trí sạc và khu vực đăng ký thẻ. Thu thập cơ cấu tổ chức, thiết bị, giờ hoạt động, quy định số xe/căn hộ, giới hạn lượt, cách cấp thẻ, gói tháng, "
            "bảng giá theo loại xe/dung tích xi-lanh/thời gian, quy trình khách, xử lý mất thẻ, xe lạ, biển số hoặc khuôn mặt không rõ. Sử dụng bảng hỏi, phỏng vấn, quan sát "
            "và biểu mẫu mẫu; không tự khẳng định kết quả khi chưa có phản hồi thực tế. Đầu ra gồm sơ đồ quy trình hiện tại, vấn đề, nguyên nhân, quy trình đề xuất, danh sách dữ liệu/biểu mẫu "
            "và tiêu chí thành công có xác nhận của người hướng dẫn hoặc đơn vị khảo sát.",
            "Yêu cầu 1",
        ),
        21: (
            "Yêu cầu 2 - Lập kế hoạch và phân công. Chia hệ thống thành Web/API/CSDL, Desktop trạm gác, dịch vụ AI và kiểm thử-tài liệu; lập backlog, mốc tích hợp và phụ thuộc giữa các phần. "
            "SV1 chịu trách nhiệm chính Web/API/CSDL; SV2 chịu trách nhiệm chính Desktop/camera/luồng cổng; SV3 chịu trách nhiệm chính AI/dữ liệu/đánh giá; mỗi thành viên phải có phần phân tích, "
            "lập trình, kiểm thử và báo cáo. Quản lý mã nguồn bằng Git, nhánh chức năng, pull request và issue; họp ít nhất một lần/tuần. Đầu ra là kế hoạch 12 tuần, bảng phân công, nhật ký tiến độ, "
            "tiêu chí hoàn thành và phương án thay thế khi một mô-đun chậm hoặc không tích hợp được.",
            "Yêu cầu 2",
        ),
        22: (
            "Yêu cầu 3 - Phân tích hệ thống. Xác định tác nhân: Quản trị viên/Ban quản lý, Nhân viên trạm gác, Cư dân/Thành viên hộ gia đình, Khách và dịch vụ AI; hệ thống ngoài gồm camera, "
            "đầu đọc thẻ và thiết bị/barrier mô phỏng. Lập Use Case nghiệp vụ và hệ thống, đặc tả điều kiện trước/sau, luồng chính, luồng thay thế, ngoại lệ và quy tắc cho từng nghiệp vụ. "
            "Lập sơ đồ hoạt động/tuần tự cho đăng ký cư dân, xe cư dân vào/ra, khách vào/ra và xử lý cảnh báo. Xây dựng lớp phân tích gồm người dùng, căn hộ, cư dân, thành viên, phương tiện, "
            "thẻ, gói tháng, bảng giá, lượt xe, ảnh nhận diện, kết quả AI, thanh toán, phiên sạc, cảnh báo và nhật ký. Đầu ra có ma trận truy vết từ yêu cầu đến Use Case, lớp, API và kiểm thử.",
            "Yêu cầu 3",
        ),
        24: (
            "Yêu cầu về Ngôn ngữ: Java 21 và Spring Boot cho Web/REST API; C# và .NET WinForms cho Desktop; Python cho dịch vụ AI; HTML, CSS, JavaScript và Thymeleaf cho giao diện Web.",
            "Yêu cầu về Ngôn ngữ:",
        ),
        25: (
            "Yêu cầu về hệ quản trị: MySQL chạy trong Laragon. Cơ sở dữ liệu quan hệ phải có khóa chính/ngoại, ràng buộc duy nhất, trạng thái, chỉ mục theo biển số-thẻ-thời gian, giao dịch khi ghi lượt/thu phí, "
            "sao lưu và phục hồi. Ảnh lưu trong vùng tệp có kiểm soát; cơ sở dữ liệu lưu đường dẫn, hàm băm, siêu dữ liệu, độ tin cậy và mẫu đặc trưng cần thiết thay vì lưu dữ liệu dư thừa.",
            "Yêu cầu về hệ quản trị:",
        ),
        26: (
            "Yêu cầu khác: kiến trúc Web/Desktop/AI giao tiếp qua REST API JSON; Maven; JPA/Hibernate; HttpClient phía Desktop; FastAPI, OpenCV, YOLO, OCR và thư viện nhận diện khuôn mặt phía AI; "
            "Git/GitHub, UML, Postman và công cụ kiểm thử. Thiết bị có thể dùng camera IP/USB, video hoặc ảnh mô phỏng; barrier và đầu đọc thẻ được mô phỏng nếu không có phần cứng. Hệ thống hỗ trợ cấu hình địa chỉ dịch vụ, "
            "mất kết nối, retry có giới hạn, hàng đợi tạm, nhật ký lỗi và dữ liệu demo ẩn danh.",
            "Yêu cầu khác:",
        ),
        31: ("Các tài liệu chính dự kiến sử dụng trong quá trình thực hiện đề tài:", None),
        32: (
            "Quốc hội, “Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15”, 2025; OMG, “UML Specification”; Spring, “Spring Boot Reference”; "
            "Microsoft, “Windows Forms Documentation”; Oracle, “MySQL Reference Manual”.",
            None,
        ),
        33: (
            "Ultralytics, “YOLO Documentation”; R. Smith, “Tesseract OCR Engine”, ICDAR, 2007; J. Deng và cộng sự, “ArcFace”, CVPR, 2019; "
            "Z. Yu và cộng sự, “Central Difference Networks for Face Anti-Spoofing”, CVPR, 2020; OpenCV Documentation.",
            None,
        ),
    }

    for index, (text, prefix) in replacements.items():
        replace_paragraph(paragraphs[index], text, prefix)

    keep_with_next(paragraphs[38])
    keep_with_next(paragraphs[39])

    detailed_requirements = [
        "Yêu cầu 4 - Thiết kế hệ thống. Kiến trúc gồm lớp giao diện Web/Desktop, Spring Boot REST API, dịch vụ AI Python, MySQL và vùng lưu ảnh. Thiết kế lớp, package/module, sơ đồ triển khai, hợp đồng API, "
        "mã lỗi và cơ chế xác thực. Mô hình dữ liệu phải thể hiện Apartment, Resident, HouseholdMember, Vehicle, VehicleAuthorization, Card, MonthlyPass, PriceRule, ParkingSession, RecognitionResult, FaceTemplate, "
        "Payment, ChargingSession, Alert và AuditLog; quy định rõ quan hệ, khóa, trạng thái và thời hạn lưu. Giao diện Web có dashboard, người dùng, căn hộ-cư dân, xe-thẻ-gói tháng, bảng giá, lịch sử, doanh thu và cảnh báo; "
        "Desktop có camera vào/ra, kết quả AI, thông tin đối chiếu, nút xác nhận/chụp lại/mở cổng mô phỏng. Đầu ra gồm sơ đồ lớp thiết kế, ERD, wireframe và tài liệu API.",

        "Yêu cầu 5 - API và chức năng cơ bản. Cài đặt đăng nhập, đăng xuất, đổi mật khẩu, khóa tài khoản và phân quyền 02 vai trò: Quản trị viên/Ban quản lý và Nhân viên trạm gác. API cung cấp CRUD có kiểm tra dữ liệu, "
        "phân trang, tìm kiếm, lọc và mã lỗi thống nhất; nhóm endpoint dự kiến gồm /auth, /users, /apartments, /residents, /household-members, /vehicles, /cards, /passes, /prices, /gate, /sessions, /payments, /alerts và /reports. "
        "Mọi thao tác nhạy cảm phải kiểm tra quyền và ghi AuditLog. Desktop gọi API qua HttpClient, có timeout, kiểm tra trạng thái dịch vụ và thông báo rõ khi Spring API hoặc AI không hoạt động. Tiêu chí đạt: Postman chạy được luồng thành công và lỗi, "
        "dữ liệu giữa Web-Desktop đồng bộ, không tạo bản ghi trùng và lỗi không làm mất lượt xe đang xử lý.",

        "Yêu cầu 6 - Nghiệp vụ NV01: Đăng ký căn hộ, cư dân, thành viên gia đình, phương tiện và thẻ. Tác nhân chính: Ban quản lý; điều kiện: căn hộ hợp lệ, người đăng ký đồng ý mục đích xử lý dữ liệu và cung cấp hồ sơ cần thiết. "
        "Luồng chính: tạo căn hộ/hộ gia đình; nhập thông tin cư dân và thành viên được ủy quyền; đọc phần thông tin cần thiết từ căn cước; thu ảnh chân dung trên giấy tờ, ảnh chụp khi tạo hồ sơ và ảnh realtime có kiểm tra sống; thực hiện ba phép đối chiếu CCCD-ảnh đăng ký, "
        "ảnh đăng ký-realtime và CCCD-realtime; đăng ký xe, biển số, chủ xe, người được dùng xe, loại xe, nhiên liệu/điện và dung tích xi-lanh từ giấy đăng ký; cấp thẻ và gắn gói 30 ngày. Ngoại lệ: ảnh không đạt, thông tin trùng, biển số đã tồn tại, thành viên không thuộc hộ hoặc giấy tờ thiếu thì hồ sơ ở trạng thái chờ xác minh. "
        "Kết quả: hồ sơ ACTIVE chỉ khi các kiểm tra bắt buộc đạt hoặc được Ban quản lý duyệt có lý do; mọi thay đổi được lưu lịch sử.",

        "Yêu cầu 7 - Nghiệp vụ NV02: Quản lý thẻ, gói gửi xe 30 ngày, bảng giá và thanh toán. Tác nhân: Ban quản lý; điều kiện: cư dân và xe đã được duyệt. Luồng chính: cấu hình giá theo nhóm xe, khách/cư dân, lượt, khoảng thời gian hoặc gói; tạo gói có ngày bắt đầu, "
        "ngày hết hạn sau 30 ngày, số xe/căn hộ, quyền ra vào và quyền sạc; ghi nhận thanh toán; kích hoạt thẻ; gia hạn, tạm khóa, hủy hoặc thay thẻ. Công thức phí chọn quy tắc đúng theo loại xe và thời điểm, làm tròn và lưu chi tiết tính phí để đối soát. Ngoại lệ: thẻ trùng, gói chồng lấn, thanh toán thiếu, xe vượt hạn mức hoặc gói hết hạn thì không kích hoạt và phát cảnh báo. "
        "Kết quả: trạng thái thẻ/gói/thanh toán nhất quán; Web hiển thị danh sách sắp hết hạn và lịch sử gia hạn.",

        "Yêu cầu 8 - Nghiệp vụ NV03: Kiểm soát xe cư dân vào. Tác nhân: Nhân viên trạm gác và dịch vụ AI; điều kiện: camera/API/AI sẵn sàng hoặc có quy trình dự phòng. Luồng chính: camera lấy nhiều khung hình; YOLO khoanh biển số, OCR nhận ký tự và chuẩn hóa; mô hình xác định nhóm phương tiện; camera mặt phát hiện khuôn mặt, kiểm tra chất lượng/sống và tạo kết quả so khớp; nhân viên quét thẻ. "
        "Hệ thống tìm xe-thẻ-gói ACTIVE, kiểm tra biển số, loại xe, thời hạn, giới hạn, xe có đang ở trong bãi hay không và người lái có phải cư dân/thành viên được ủy quyền. Với cư dân, hệ thống đối chiếu realtime với mẫu ảnh đăng ký và mẫu tham chiếu từ căn cước đã được bảo vệ; nếu vợ/chồng/con dùng xe thì kiểm tra quan hệ hộ gia đình và quyền dùng chính xe đó. Khi tất cả điều kiện đạt, tạo ParkingSession OPEN, lưu thời gian/cổng/ảnh/kết quả AI và cho phép mở barrier mô phỏng. "
        "Ngoại lệ: sai biển, sai mặt, thẻ-gói hết hạn, xe đã ở trong bãi, độ tin cậy thấp hoặc mất dịch vụ thì không tự mở cổng; chuyển NV07. Tiêu chí đạt: một sự kiện chỉ tạo một lượt và phản hồi trong ngưỡng thời gian cấu hình.",

        "Yêu cầu 9 - Nghiệp vụ NV04: Kiểm soát xe cư dân ra. Tác nhân: Nhân viên trạm gác và AI; điều kiện: tồn tại ParkingSession OPEN. Luồng chính: chụp biển số, loại xe và khuôn mặt lúc ra; tìm lượt mở bằng biển số/thẻ; đối chiếu biển số vào-ra, ảnh khuôn mặt vào-ra, realtime với hồ sơ đăng ký, loại xe và người được ủy quyền; kiểm tra sự cố/công nợ nếu có. "
        "Nếu gói tháng còn hiệu lực thì phí lượt bằng 0 hoặc theo chính sách phụ phí đã cấu hình; lưu giờ ra, thời lượng, ảnh và kết quả, chuyển lượt CLOSED rồi cho phép mở cổng. Ngoại lệ: không tìm thấy lượt vào, biển số hoặc khuôn mặt không khớp, người khác lấy xe không thuộc danh sách hộ gia đình, thẻ mất hoặc dữ liệu vào thiếu thì khóa thao tác tự động và chuyển NV07. "
        "Kết quả: không đóng nhầm lượt, không cho một lượt đóng hai lần và có đầy đủ bằng chứng vào-ra.",

        "Yêu cầu 10 - Nghiệp vụ NV05: Khách vãng lai vào, ra và thu phí. Tác nhân: Nhân viên trạm gác và khách; điều kiện: xe không có hồ sơ/gói cư dân, camera hoạt động và còn khả năng tiếp nhận. Luồng chính: khi vào, khách không cần hồ sơ cư dân/căn cước; Desktop chụp biển số, khuôn mặt realtime, loại xe và ảnh toàn cảnh, cấp mã lượt/thẻ khách, tạo ParkingSession OPEN và ghi thời gian. Khi ra, hệ thống nhận diện lại, tìm lượt, đối chiếu biển số-khuôn mặt-loại xe giữa hai thời điểm, tính thời lượng, áp dụng PriceRule, hiển thị chi tiết phí để nhân viên xác nhận thanh toán và đóng lượt. "
        "Ngoại lệ: mất thẻ, biển số mờ/đổi, khuôn mặt bị che, người lấy xe khác hoặc không tìm thấy lượt thì yêu cầu giấy tờ/chứng minh quyền sở hữu theo quy định thực tế, lập cảnh báo và chỉ cho phép xử lý thủ công có lý do. Kết quả: mỗi khoản thu gắn với đúng lượt, phương thức thanh toán, người xác nhận và báo cáo doanh thu.",

        "Yêu cầu 11 - Nghiệp vụ NV06: Phân loại phương tiện và quyền sạc xe điện. Tác nhân: Ban quản lý, Nhân viên trạm gác và AI; điều kiện: có ảnh phương tiện hoặc hồ sơ đăng ký xe. Luồng chính: AI chỉ phân loại nhóm quan sát được gồm xe đạp, xe đạp điện, xe máy, xe máy điện, ô tô và ô tô điện; hệ thống không suy luận chính xác dung tích xi-lanh chỉ từ ảnh mà lấy từ giấy đăng ký xe và nhân viên xác minh. Khi đăng ký, lưu hãng/dòng xe, nhiên liệu, dung tích hoặc thông số điện; gán nhóm giá phù hợp. "
        "Đối với xe điện như VinFast hoặc hãng khác, hệ thống kiểm tra xe đã đăng ký, gói/quyền sạc còn hiệu lực, loại đầu sạc/khu vực được phép và vị trí còn trống trước khi tạo ChargingSession; MVP quản lý quyền và lịch sử sạc, không điều khiển điện năng nếu không có phần cứng. Ngoại lệ: AI khác hồ sơ, xe xăng vào khu sạc, hết quyền hoặc vị trí đầy thì cảnh báo. Kết quả: phân loại AI, thông tin hồ sơ và quyết định tính phí/sạc được lưu riêng để truy vết.",

        "Yêu cầu 12 - Nghiệp vụ NV07: Xử lý ảnh kém, sai lệch và sự cố tại cổng. Tác nhân: Nhân viên trạm gác và AI; điều kiện kích hoạt: ảnh dưới ngưỡng chất lượng, kết quả không khớp hoặc một dịch vụ mất kết nối. Luồng chính: trước OCR/so khớp, hệ thống chấm chất lượng ảnh theo sáng-tối, độ nét, góc, kích thước biển số/khuôn mặt và mức che khuất; lấy khung hình tốt nhất từ video. Giải pháp phần cứng gồm camera WDR/HDR/IR, đèn bố trí tránh ngược sáng, camera biển số và camera mặt riêng, góc chụp phụ; giải pháp phần mềm gồm cân bằng sáng, tăng tương phản, deskew, nhiều khung hình và ngưỡng tin cậy. "
        "Ngoại lệ và xử lý: nếu vẫn không đạt, Desktop hướng dẫn chụp lại; sau số lần cấu hình thì tạo Alert, giữ barrier đóng và cho nhân viên kiểm tra giấy tờ, ảnh vào, thông tin hộ gia đình. Kết quả: quyết định cho qua/từ chối phải có lý do, người thao tác, thời gian và bằng chứng; không được sửa trực tiếp kết quả AI để che sai lệch. Dữ liệu nhạy cảm được phân quyền, mã hóa khi truyền/lưu, sao lưu, đặt thời hạn lưu/xóa và dùng dữ liệu giả hoặc làm mờ khi trình diễn.",

        "Yêu cầu 13 - Nghiệp vụ NV08: Tra cứu, cảnh báo, thống kê và báo cáo doanh thu. Tác nhân: Quản trị viên/Ban quản lý; điều kiện: người dùng đã đăng nhập đúng quyền và dữ liệu giao dịch đã được ghi nhận. Luồng chính: Web cho phép lọc lịch sử theo biển số, thẻ, căn hộ, cư dân, khách, cổng, loại xe, trạng thái và khoảng thời gian; xem ảnh/kết quả AI theo quyền. Dashboard hiển thị số xe đang trong bãi, lượt vào/ra, lượt mở quá lâu, cảnh báo chưa xử lý, thẻ/gói sắp hết hạn, số xe theo loại và quyền sạc. "
        "Báo cáo doanh thu tổng hợp theo ngày/tháng, cư dân/khách, loại xe, bảng giá, phương thức và nhân viên; tổng báo cáo phải đối chiếu được với Payment và ParkingSession, không tính bản ghi hủy hoặc chưa thanh toán. Ngoại lệ: dữ liệu thiếu/không cân đối hoặc người dùng không đủ quyền thì không cho chốt/xuất báo cáo và tạo cảnh báo kiểm tra. Hỗ trợ xuất CSV/Excel/PDF nếu đủ thời gian; mọi lần xem/xuất dữ liệu nhạy cảm được ghi nhật ký. Kết quả và tiêu chí đạt: số liệu tổng bằng chi tiết và có bộ dữ liệu kiểm thử để chứng minh.",

        "Yêu cầu 14 - Kiểm thử, triển khai và báo cáo. Xây dựng test case từ ma trận truy vết: CRUD, phân quyền, gói-thẻ-bảng giá, 08 nghiệp vụ, lỗi API, mất AI, dữ liệu trùng, giao dịch đồng thời và sao lưu/phục hồi. Đánh giá ANPR bằng Precision, Recall, độ chính xác ký tự/biển; khuôn mặt bằng tỷ lệ đúng, FAR/FRR và kiểm tra sống; đo thời gian xử lý trung bình/P95 và tỷ lệ lượt hoàn tất. "
        "Thử nghiệm ảnh ngày-đêm, cháy sáng, mờ, nghiêng, che khuất, xe máy/ô tô/xe điện và người trong gia đình dùng chung xe. Triển khai MySQL trên Laragon, Spring Boot, AI service và Desktop bằng tập lệnh/hướng dẫn; cấu hình dữ liệu demo, tài khoản, camera/video, backup và log. Đầu ra cuối cùng gồm mã nguồn, database script, bộ dữ liệu thử, biên bản kiểm thử, hướng dẫn cài đặt/sử dụng, báo cáo khóa luận và PowerPoint.",
    ]

    anchor = paragraphs[22]
    for requirement in detailed_requirements:
        anchor = insert_requirement_after(anchor, requirement)

    schedule = [
        "Khảo sát hiện trạng và biểu mẫu; hoàn thiện bảng hỏi/phỏng vấn, sơ đồ quy trình hiện tại, vấn đề và mục tiêu. Cả nhóm khảo sát; SV3 tổng hợp minh chứng.",
        "Chốt phạm vi, yêu cầu chức năng/phi chức năng, 08 nghiệp vụ; lập backlog, kế hoạch, phân công và tiêu chí hoàn thành. SV1 quản lý backlog; cả nhóm duyệt.",
        "Lập Use Case nghiệp vụ/hệ thống và đặc tả; sơ đồ hoạt động, tuần tự, lớp phân tích và ma trận truy vết. SV2 chủ trì luồng cổng; cả nhóm hoàn thiện.",
        "Thiết kế kiến trúc, lớp thiết kế, ERD/MySQL, API, bảo mật và wireframe Web/Desktop. SV1 chủ trì API-CSDL; SV2 giao diện; SV3 hợp đồng AI.",
        "Xây dựng CSDL, Spring Boot API, đăng nhập, phân quyền, AuditLog và CRUD nền tảng; tạo WinForms khung và AI service khung. Mỗi SV tích hợp phần phụ trách.",
        "Cài đặt NV01-NV02: căn hộ, cư dân, thành viên, xe, quyền sử dụng, thẻ, gói 30 ngày, bảng giá và thanh toán. SV1 chính; SV2/SV3 kiểm thử tích hợp.",
        "Cài đặt Desktop và NV03-NV04: camera/ảnh-video, xe cư dân vào-ra, thẻ, đối chiếu, ParkingSession và barrier mô phỏng. SV2 chính; SV1 hỗ trợ API.",
        "Tích hợp ANPR và phân loại xe cho NV03-NV06; chuẩn hóa biển số, nhiều khung hình, độ tin cậy và bộ dữ liệu đánh giá. SV3 chính; SV2 tích hợp Desktop.",
        "Tích hợp khuôn mặt, chất lượng ảnh, kiểm tra sống và ba nguồn ảnh; hoàn thiện hộ gia đình dùng chung xe, khách NV05 và ngoại lệ NV07. SV3 chính.",
        "Hoàn thiện NV06-NV08: quyền sạc, cảnh báo, tra cứu, doanh thu, dashboard và xuất báo cáo; kiểm tra đối soát. SV1 chính; cả nhóm chạy end-to-end.",
        "Kiểm thử đơn vị/API/tích hợp/nghiệp vụ/hiệu năng/bảo mật; đánh giá AI trong các điều kiện ảnh; triển khai thử, backup-phục hồi và sửa lỗi. Cả nhóm.",
        "Hoàn chỉnh mã nguồn, database script, hướng dẫn, báo cáo, minh chứng, PowerPoint; chạy thử kịch bản demo, luyện thuyết trình và nộp sản phẩm. Cả nhóm.",
    ]
    rows = tables[1].findall(W + "tr")
    for row_index, work in enumerate(schedule, start=1):
        cells = rows[row_index].findall(W + "tc")
        replace_paragraph(first_paragraph(cells[0]), "")
        replace_paragraph(first_paragraph(cells[1]), work)

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
