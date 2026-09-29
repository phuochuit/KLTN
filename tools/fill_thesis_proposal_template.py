from copy import deepcopy
from hashlib import sha256
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from lxml import etree


SOURCE = Path(r"D:\Code\KLTN\Mau De cuong chi tiet KLCN_Huong ung dung_04_07_2026.docx")
OUTPUT = Path(r"D:\Code\KhoaLuan\De_cuong_chi_tiet_KLCN_Quan_ly_bai_do_xe_chung_cu.docx")
EXPECTED_SOURCE_SHA256 = "c1b157aacc738281beb45f0d7e04ef02d472c340c926933d679688b2b70ed745"

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W = f"{{{W_NS}}}"
XML_SPACE = "{http://www.w3.org/XML/1998/namespace}space"


def source_run_properties(paragraph, prefer_bold=False):
    runs = paragraph.findall(W + "r")
    if not runs:
        return None
    if prefer_bold:
        for run in runs:
            rpr = run.find(W + "rPr")
            if rpr is not None and rpr.find(W + "b") is not None:
                return deepcopy(rpr)
    else:
        for run in runs:
            rpr = run.find(W + "rPr")
            if rpr is None or rpr.find(W + "b") is None:
                return deepcopy(rpr) if rpr is not None else None
    rpr = runs[0].find(W + "rPr")
    return deepcopy(rpr) if rpr is not None else None


def add_run(paragraph, text, rpr=None):
    run = etree.SubElement(paragraph, W + "r")
    if rpr is not None:
        run.append(deepcopy(rpr))
    lines = text.split("\n")
    for index, line in enumerate(lines):
        if index:
            etree.SubElement(run, W + "br")
        node = etree.SubElement(run, W + "t")
        node.set(XML_SPACE, "preserve")
        node.text = line


def replace_paragraph(paragraph, text, bold_prefix=None):
    ppr = paragraph.find(W + "pPr")
    saved_ppr = deepcopy(ppr) if ppr is not None else None
    normal_rpr = source_run_properties(paragraph, prefer_bold=False)
    bold_rpr = source_run_properties(paragraph, prefer_bold=True)
    for child in list(paragraph):
        paragraph.remove(child)
    if saved_ppr is not None:
        paragraph.append(saved_ppr)
    if bold_prefix and text.startswith(bold_prefix):
        add_run(paragraph, bold_prefix, bold_rpr)
        add_run(paragraph, text[len(bold_prefix):], normal_rpr)
    else:
        add_run(paragraph, text, normal_rpr)


def first_paragraph(cell):
    paragraphs = cell.findall(W + "p")
    paragraph = paragraphs[0] if paragraphs else None
    if paragraph is None:
        paragraph = etree.SubElement(cell, W + "p")
    for extra in paragraphs[1:]:
        cell.remove(extra)
    return paragraph


def rewrite_document_xml(xml_bytes):
    parser = etree.XMLParser(remove_blank_text=False)
    root = etree.fromstring(xml_bytes, parser)
    body = root.find(W + "body")
    paragraphs = body.findall(W + "p")
    tables = body.findall(W + "tbl")

    replacements = {
        5: (
            "Tên đề tài: Xây dựng hệ thống quản lý và kiểm soát bãi đỗ xe chung cư "
            "tích hợp nhận diện đa phương thức",
            "Tên đề tài:",
        ),
        14: (
            "Mục tiêu: Khảo sát hiện trạng, quy định và nhu cầu quản lý bãi đỗ xe tại chung cư; "
            "xác định tác nhân, dữ liệu, biểu mẫu, rủi ro và các quy trình nghiệp vụ. Phân tích, "
            "mô hình hóa và thiết kế hệ thống quản lý cư dân, hộ gia đình, phương tiện, thẻ, vé, "
            "gói gửi xe tháng, khách vãng lai, bảng giá, lượt xe vào/ra, quyền sạc xe điện, cảnh báo "
            "và báo cáo doanh thu. Xây dựng một ứng dụng hoàn chỉnh, đồng bộ dữ liệu trên Web và "
            "Desktop; tích hợp dịch vụ AI nhận diện biển số, khuôn mặt và phân loại phương tiện. "
            "Đánh giá hệ thống bằng kiểm thử chức năng, tích hợp, hiệu năng và các chỉ số nhận diện. "
            "Hoàn thiện tài liệu phân tích, thiết kế, hướng dẫn sử dụng và báo cáo khóa luận. Nhóm "
            "thực hiện đúng kế hoạch, phân công rõ trách nhiệm, tích hợp kết quả hằng tuần, chủ động "
            "tự học công nghệ và tuân thủ nguyên tắc bảo vệ dữ liệu cá nhân.",
            "Mục tiêu:",
        ),
        16: (
            "Khảo sát và mô hình hóa tối thiểu 06 nghiệp vụ thực tế: (1) quản lý cư dân, căn hộ và "
            "người được hộ gia đình ủy quyền; (2) quản lý phương tiện, thẻ và gói gửi xe tháng; "
            "(3) kiểm soát xe cư dân vào/ra bằng biển số, khuôn mặt, thẻ và hồ sơ đăng ký; "
            "(4) kiểm soát khách vãng lai và tính phí; (5) phân loại phương tiện, quản lý quyền sạc "
            "xe điện; (6) cảnh báo, tra cứu lịch sử, thống kê và báo cáo doanh thu.",
            None,
        ),
        17: (
            "Xây dựng Web Spring Boot cho Ban quản lý và ứng dụng WinForms cho nhân viên trạm gác; "
            "hai nền tảng sử dụng chung cơ sở dữ liệu MySQL qua REST API. Nghiệp vụ phức tạp là "
            "đối chiếu đa phương thức tại cổng: ANPR, xác thực khuôn mặt, kiểm tra người được phép "
            "sử dụng xe, trạng thái thẻ/gói tháng và xử lý ngoại lệ có sự xác nhận của nhân viên.",
            None,
        ),
        19: (
            "Các nội dung cụ thể cần thực hiện: khảo sát nghiệp vụ; đặc tả yêu cầu; lập kế hoạch và "
            "phân công; xây dựng Use Case nghiệp vụ và hệ thống; sơ đồ lớp phân tích, lớp thiết kế, "
            "mô hình dữ liệu; thiết kế giao diện; xây dựng API và chức năng trên hai nền tảng; tích "
            "hợp AI; kiểm thử, triển khai thử nghiệm; đánh giá kết quả và hoàn thiện báo cáo.",
            "Các nội dung cụ thể cần thực hiện:",
        ),
        20: (
            "Yêu cầu 1 - Quản lý người dùng, cư dân, phương tiện và dịch vụ: (1) Đăng nhập, đổi mật khẩu và phân quyền 02 vai trò: "
            "Quản trị viên/Ban quản lý và Nhân viên trạm gác; (2) Quản lý căn hộ, cư dân, thành viên hộ gia đình và danh sách người "
            "được phép sử dụng từng phương tiện; (3) Khi đăng ký cư dân/thẻ tháng, lưu thông tin cần thiết từ căn cước, ảnh chân dung trên giấy tờ, "
            "ảnh chụp trực tiếp có kiểm tra sống và quan hệ với căn hộ; không yêu cầu quét căn cước trong mỗi lượt xe hằng ngày. "
            "(4) Quản lý phương tiện, giấy đăng ký xe, biển số, nhóm xe, nhiên liệu/điện, dung tích xi-lanh do hồ sơ cung cấp, thẻ, "
            "gói 30 ngày, ngày hiệu lực, số xe tối đa, giới hạn lượt và quyền sử dụng điểm sạc; (5) Quản lý bảng giá theo loại xe, "
            "khách/cư dân, lượt/thời gian/gói tháng, trạng thái thanh toán và gia hạn.",
            "Yêu cầu 1",
        ),
        21: (
            "Yêu cầu 2 - Kiểm soát phương tiện vào/ra và đối chiếu đa phương thức: (1) Xe cư dân: đối chiếu biển số, thẻ, trạng thái gói tháng, loại xe, người lái với ảnh đăng ký và danh sách "
            "thành viên hộ gia đình được ủy quyền; lưu ảnh biển số, khuôn mặt, thời gian, cổng và kết quả kiểm tra. "
            "(2) Khi tạo hồ sơ: hỗ trợ đối chiếu ba nguồn ảnh gồm ảnh trên căn cước, ảnh chụp khi đăng ký và ảnh realtime; "
            "khi sử dụng hằng ngày, đối chiếu ảnh realtime với mẫu đã đăng ký và ảnh của lượt vào. "
            "(3) Xe khách: ghi nhận biển số và khuôn mặt realtime lúc vào; lúc ra đối chiếu với lượt vào, tính phí và đóng lượt. "
            "(4) Cảnh báo khi biển số, khuôn mặt, thẻ, phương tiện hoặc người lái không khớp; không tự mở cổng khi độ tin cậy thấp; "
            "cho phép nhân viên chụp lại, kiểm tra giấy tờ và xác nhận thủ công có ghi nhật ký.",
            "Yêu cầu 2",
        ),
        22: (
            "Yêu cầu 3 - AI, chất lượng ảnh, báo cáo và an toàn dữ liệu: (1) ANPR gồm hai bước: YOLO phát hiện/khoanh vùng biển số và OCR nhận dạng ký tự; chuẩn hóa định dạng biển số Việt Nam, "
            "trả về ảnh cắt và độ tin cậy. "
            "(2) Phát hiện khuôn mặt, đánh giá chất lượng, kiểm tra sống cơ bản và so khớp 1:1; chỉ lưu mẫu đặc trưng cần thiết theo chính sách. "
            "(3) Phân loại các nhóm: xe đạp, xe đạp điện, xe máy, xe máy điện, ô tô và ô tô điện. Dung tích xi-lanh không suy luận chỉ từ ảnh, "
            "mà lấy từ giấy đăng ký xe và được nhân viên xác minh. "
            "(4) Xử lý cháy sáng, thiếu sáng, mờ hoặc che khuất bằng camera WDR/HDR/IR, vị trí camera phù hợp, nhiều khung hình, chỉ số chất lượng, "
            "chụp lại và quy trình thủ công dự phòng. "
            "(5) Web hỗ trợ tra cứu lịch sử, số xe trong bãi, gói sắp hết hạn, sự cố, doanh thu ngày/tháng và xuất báo cáo; lưu nhật ký thao tác. "
            "(6) Có thông báo mục đích xử lý, phân quyền truy cập, mã hóa khi truyền/lưu, sao lưu, thời hạn lưu ảnh và cơ chế xóa theo quy định; "
            "dữ liệu căn cước dùng dữ liệu giả/làm mờ trong demo. "
            "(7) Đánh giá bằng Precision, Recall, độ chính xác OCR, tỷ lệ so khớp đúng, FAR/FRR, thời gian xử lý trung bình và tỷ lệ giao dịch hoàn tất.",
            "Yêu cầu 3",
        ),
        24: (
            "Yêu cầu về Ngôn ngữ: Java và Spring Boot cho Web/API; C# và .NET WinForms cho Desktop; "
            "Python cho dịch vụ AI; HTML, CSS, JavaScript và Thymeleaf cho giao diện Web.",
            "Yêu cầu về Ngôn ngữ:",
        ),
        25: (
            "Yêu cầu về hệ quản trị: MySQL triển khai trong môi trường Laragon; thiết kế cơ sở dữ liệu quan hệ có khóa, "
            "ràng buộc toàn vẹn, chỉ mục, giao dịch, sao lưu và phục hồi. Ảnh được lưu tại vùng lưu trữ có kiểm soát, "
            "cơ sở dữ liệu chỉ lưu đường dẫn, siêu dữ liệu và mẫu đặc trưng cần thiết.",
            "Yêu cầu về hệ quản trị:",
        ),
        26: (
            "Yêu cầu khác: REST API/JSON; Maven; Entity Framework hoặc HttpClient phía Desktop; FastAPI/OpenCV/YOLO/OCR "
            "và thư viện nhận diện khuôn mặt phía AI; Git để quản lý phiên bản; UML; Postman; camera IP/USB hoặc video mô phỏng. "
            "Hệ thống chạy thử trên Windows, hỗ trợ cấu hình địa chỉ dịch vụ, xử lý mất kết nối và ghi nhật ký lỗi.",
            "Yêu cầu khác:",
        ),
        31: (
            "Các tài liệu chính dự kiến sử dụng trong quá trình thực hiện đề tài:",
            None,
        ),
        32: (
            "Văn bản pháp lý và tài liệu nền tảng: Quốc hội, “Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15”, 2025; "
            "Spring, “Spring Boot Reference Documentation”, https://docs.spring.io/spring-boot/; Microsoft, “Windows Forms documentation”, "
            "https://learn.microsoft.com/dotnet/desktop/winforms/; Oracle, “MySQL 8.4 Reference Manual”, https://dev.mysql.com/doc/refman/8.4/en/.",
            None,
        ),
        33: (
            "Tài liệu nhận diện và xử lý ảnh: Ultralytics, “Ultralytics YOLO Documentation”, https://docs.ultralytics.com/; "
            "R. Smith, “An Overview of the Tesseract OCR Engine”, ICDAR, 2007; J. Deng et al., “ArcFace: Additive Angular Margin Loss for Deep Face Recognition”, "
            "CVPR, 2019; Z. Yu et al., “Searching Central Difference Convolutional Networks for Face Anti-Spoofing”, CVPR, 2020; "
            "OpenCV, “OpenCV Documentation”, https://docs.opencv.org/.",
            None,
        ),
    }

    for index, (text, prefix) in replacements.items():
        replace_paragraph(paragraphs[index], text, prefix)

    schedule = [
        ("01", "Khảo sát bãi xe chung cư; tổng hợp quy định, biểu mẫu, tác nhân, vấn đề và tiêu chí thành công; lập kế hoạch, phân công nhóm."),
        ("02", "Đặc tả yêu cầu chức năng/phi chức năng; mô tả 06 quy trình nghiệp vụ; xác định phạm vi MVP, dữ liệu cá nhân và ngoại lệ vận hành."),
        ("03", "Lập Use Case nghiệp vụ, Use Case hệ thống và đặc tả; xây dựng sơ đồ hoạt động/tuần tự cho luồng cư dân, khách và cảnh báo."),
        ("04", "Thiết kế kiến trúc Web–Desktop–AI–MySQL; sơ đồ lớp phân tích/thiết kế; mô hình dữ liệu, API, phân quyền và chính sách lưu trữ."),
        ("05", "Thiết kế giao diện và nguyên mẫu; xây dựng cơ sở dữ liệu, xác thực, tài khoản, vai trò, căn hộ, cư dân và thành viên hộ gia đình."),
        ("06", "Xây dựng quản lý phương tiện, thẻ, gói 30 ngày, bảng giá, gia hạn, quyền sạc xe điện và các quy tắc giới hạn."),
        ("07", "Xây dựng Desktop trạm gác: kết nối camera/ảnh/video, ghi nhận lượt vào/ra, đối chiếu dữ liệu và xử lý xác nhận thủ công."),
        ("08", "Tích hợp ANPR: phát hiện biển số bằng YOLO, OCR, chuẩn hóa kết quả, đo độ tin cậy và xử lý nhiều khung hình/chụp lại."),
        ("09", "Tích hợp khuôn mặt: phát hiện, kiểm tra chất lượng/sống cơ bản, tạo mẫu đăng ký, so khớp cư dân/hộ gia đình và vào–ra của khách."),
        ("10", "Hoàn thiện Web tra cứu lịch sử, cảnh báo, số xe trong bãi, gói sắp hết hạn, doanh thu ngày/tháng và xuất báo cáo."),
        ("11", "Kiểm thử đơn vị, API, tích hợp và nghiệp vụ; đánh giá ANPR/khuôn mặt, hiệu năng, tình huống cháy sáng/che khuất; sửa lỗi và triển khai thử."),
        ("12", "Hoàn chỉnh báo cáo, tài liệu hướng dẫn và minh chứng kiểm thử; thiết kế PowerPoint, luyện thuyết trình, đóng gói và nộp sản phẩm."),
    ]
    rows = tables[1].findall(W + "tr")
    for row_index, (week, work) in enumerate(schedule, start=1):
        cells = rows[row_index].findall(W + "tc")
        replace_paragraph(first_paragraph(cells[0]), "")
        replace_paragraph(first_paragraph(cells[1]), work)

    return etree.tostring(root, xml_declaration=True, encoding="UTF-8", standalone="yes")


def clone_with_replacement(source, output):
    with ZipFile(source, "r") as zin:
        document_xml = rewrite_document_xml(zin.read("word/document.xml"))
        with ZipFile(output, "w") as zout:
            for item in zin.infolist():
                data = document_xml if item.filename == "word/document.xml" else zin.read(item.filename)
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
