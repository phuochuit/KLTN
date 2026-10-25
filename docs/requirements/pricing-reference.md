# Mức phí tham khảo và ví dụ tính

[NV02](nv02-cards-passes-pricing.md) · [NV05](nv05-visitor-parking.md) · [Quyết định](../decisions/README.md)

## Trạng thái

Nội dung số liệu được tổng hợp từ bản “Kết quả tổng hợp mức phí khảo sát” 7 trang. Chưa xác nhận đơn vị khảo sát, mẫu, thời điểm, phương pháp, consent hay phê duyệt áp dụng. Đây **không phải biểu phí pháp lý, khảo sát đại diện, production config hoặc test đã pass**. Bảng giá thực tế có thể thay đổi trong Web; P02/P03/P05/P08 còn mở.

## Gói cư dân 30 ngày

| Nhóm trong tài liệu tham khảo | Phí VND / một xe / kỳ |
| --- | --- |
| Xe đạp, xe đạp điện | 100000 |
| Xe máy, xe điện thông thường | 150000 |
| Xe máy phân khối lớn | 300000 |
| Ô tô 4–5 chỗ | 1500000 |
| Ô tô 6–7 chỗ | 1700000 |
| Ô tô 8–9 chỗ | 1800000 |

Ví dụ hai xe máy thường và một ô tô 4–5 chỗ: `2 × 150000 + 1500000 = 1800000`. Phí tính từng xe, không một gói dùng chung mọi xe trong hộ. Danh mục nguồn không chứng minh AI phân loại đủ nhóm hoặc quyền sạc.

## Khách xe máy, xe đạp/đạp điện

| Khoản | Xe máy | Xe đạp/đạp điện |
| --- | --- | --- |
| Ban ngày 06:00–17:59 | 5000/lượt | 2000/lượt |
| Ban đêm 18:00–05:59 | 8000/lượt | 3000/lượt |
| Qua 00:00 hoặc đạt chu kỳ 24 giờ | 10000/chu kỳ | 5000/chu kỳ |

Chu kỳ qua đêm tính `ceil(tổng thời gian / 24 giờ)`, tối thiểu một chu kỳ khi đã qua ngày. “Làm tròn” trong bảng gốc được các ví dụ thể hiện là **làm tròn lên**, không nearest rounding.

| Thời gian gửi xe máy | Cách tính tham khảo | Phí VND |
| --- | --- | --- |
| 08:00–12:00 cùng ngày | Một lượt ngày | 5000 |
| 08:00–20:00 cùng ngày | Một lượt đêm | 8000 |
| 18:00–23:00 cùng ngày | Một lượt đêm | 8000 |
| 01:00–05:00 cùng ngày | Một lượt đêm | 8000 |
| 22:00–02:00 hôm sau | Qua 00:00, một chu kỳ | 10000 |
| 08:00–07:00 hôm sau (23 giờ) | Một chu kỳ | 10000 |
| Đúng 24 giờ | Một chu kỳ | 10000 |
| 24 giờ 1 phút hoặc 26 giờ | Hai chu kỳ | 20000 |
| Đúng 48 giờ | Hai chu kỳ | 20000 |
| 48 giờ 1 phút hoặc 50 giờ | Ba chu kỳ | 30000 |

Xe đạp/đạp điện dùng cùng cách chia chu kỳ nhưng đơn giá ở bảng trên. Mốc đúng 18:00 và khoảng thời gian lẻ dưới phút cần tách thành policy/test boundary: code `calculateFee` dùng phút nguyên, ban ngày có thể gồm exit đúng 18:00; không mặc nhiên khớp từng giây với biểu tham khảo.

## Khách ô tô

- 4 giờ đầu: 40000 VND.
- Vượt 4 giờ: mỗi 2 giờ hoặc phần lẻ cộng 20000 VND.
- Mỗi mốc 00:00 đi qua tạo mức tối thiểu 100000 VND/đêm.
- Phí cuối = `max(phí thời gian, số đêm × 100000)`.

| Tình huống | Phí thời gian | Phí tối thiểu qua đêm | Phí cuối VND |
| --- | --- | --- | --- |
| 3 giờ cùng ngày | 40000 | 0 | 40000 |
| 5 giờ cùng ngày | 60000 | 0 | 60000 |
| 7 giờ cùng ngày | 80000 | 0 | 80000 |
| 23:00–02:00 hôm sau | 40000 | 100000 | 100000 |
| 20:00–07:00 hôm sau (11 giờ) | 120000 | 100000 | 120000 |
| 08:00 ngày 10–10:00 ngày 11 (26 giờ) | 260000 | 100000 | 260000 |
| 20:00 ngày 10–07:00 ngày 12 (35 giờ) | 360000 | 200000 | 360000 |

Trang cuối bản tham khảo chỉ có các tiêu đề “Flow nghiệp vụ” cư dân/khách và ngoại lệ, không có thêm rule cụ thể. Không suy ra mức phạt mất thẻ, miễn phí, surcharge hoặc quy trình mới từ các tiêu đề trống. Công thức implementation và khác biệt chính sách nằm trong [NV02](nv02-cards-passes-pricing.md); settlement/receipt ở [NV05](nv05-visitor-parking.md).
