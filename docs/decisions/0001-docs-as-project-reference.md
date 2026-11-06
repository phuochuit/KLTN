# ADR-001: Dùng docs làm tài liệu tham khảo chính của dự án

## Trạng thái
Đã duyệt — chủ dự án duyệt kế hoạch triển khai trong hội thoại ngày 2026-10-23.

## Bối cảnh
Nghiệp vụ và tài liệu kỹ thuật đang phân tán giữa Markdown, DOCX, PDF, ảnh render và ghi chép audit. Người đọc/AI phải mở nhiều bản, khó biết quy tắc hiện hành và có thể nhầm mô tả đề xuất với implementation đã kiểm chứng.

## Quyết định
- `docs/README.md` là điểm vào; nghiệp vụ, quyết định, kiến trúc, ERD/data dictionary, API, kiểm thử và vận hành phải đọc độc lập được trong docs.
- Mỗi chủ đề có một trang sở hữu; giữ source links để đối chiếu code nhưng không phụ thuộc PDF/generator/tệp tạm để hiểu quy tắc.
- Phân biệt yêu cầu, quyết định đã duyệt, code hiện tại và kết quả kiểm chứng. Chuyển nội dung không tự giải quyết mâu thuẫn nghiệp vụ.
- Chuyển tư liệu cần giữ trước; kiểm coverage/liên kết và xác nhận backup độc lập trước khi loại bỏ kho bản gốc. Bước xóa cần xác nhận cuối riêng.
- Không chép credentials, PII hoặc toàn bộ ảnh render vào docs; không đổi API/schema/stack trong nhiệm vụ tài liệu.

## Phương án đã cân nhắc
- Giữ liên kết tới nhiều bản gốc: ít biên tập nhưng tiếp tục khó đọc và dễ chọn sai authority.
- Chép toàn bộ PDF/ảnh sang docs: chỉ đổi chỗ lưu, không tạo tài liệu tự đủ nghĩa.
- Tổng hợp Markdown theo chủ đề: được chọn vì dễ đọc, tìm kiếm, kiểm liên kết, review diff và cập nhật cùng code.

## Hệ quả
Agent phải đánh giá docs impact khi thay đổi hệ thống, cập nhật trang sở hữu và báo kiểm chứng đã/chưa chạy. Bản gốc có thể giữ ngoài repository để tra lịch sử; việc tài liệu được duyệt không chứng nhận hệ thống đã hoàn thiện hoặc production-ready.
