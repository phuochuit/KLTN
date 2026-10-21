# Giai đoạn 5 - Đóng gói, vận hành và nghiệm thu

## 1. Phạm vi đã bàn giao

- Cấu hình camera USB/RTSP/HTTP(S) theo trạm, URI được kiểm tra và không ghi vào lỗi/audit.
- `IBarrierController` và bộ mô phỏng; điểm mở rộng adapter thiết bị thật.
- Sổ giao dịch phí lượt/gói, biên nhận, đối soát ca và báo cáo.
- Outbox Desktop atomic, UUID idempotency phía Spring và phục hồi sau mất mạng.
- Quy tắc ô tô/xe hai bánh cho tự động, ô cố định, đỗ nhờ và điều phối thủ công.
- JWT Desktop mặc định fail-closed; Spring gọi FastAPI bằng service token.

## 2. Đóng gói

Chạy `scripts\operations\package-release.cmd`. Gói nằm mặc định tại
`%LocalAppData%\ParkingANPR\releases`. Gói không chứa secret, model AI, ảnh tải lên hay dữ liệu sinh trắc học.
Kiểm tra `SHA256SUMS.txt` trước khi chuyển máy.

## 3. Backup và phục hồi

Chạy `scripts\operations\backup-system.cmd`. Thêm `-IncludePrivateData` chỉ khi nơi lưu backup đã mã hóa
và hạn chế quyền truy cập. Luôn giữ `SHA256SUMS.txt` cùng backup.

Phục hồi database phải thực hiện trên database trống/đã được phê duyệt, dùng MySQL client với TLS,
sau đó chạy `scripts\operations\health-check.cmd` và toàn bộ smoke test. Private data phải được khôi phục
đúng `PARKING_PRIVATE_STORAGE_PATH`; tuyệt đối không đặt trong thư mục public/static.

## 4. Monitoring

Chạy `scripts\operations\health-check.cmd` bằng Task Scheduler mỗi 1-5 phút. Exit code `0` là khỏe,
`1` là có dịch vụ lỗi. Thu thập stdout JSON vào hệ thống log/giám sát của đơn vị triển khai.

## 5. Checklist nghiệm thu tại hiện trường

- [ ] Xe máy và ô tô vào/ra bình thường; barrier chỉ mở sau response thành công.
- [ ] Mất mạng sau khi bấm xác nhận: Desktop báo đã lưu, không mở barrier, kết nối lại chỉ tạo một lượt.
- [ ] Khởi động lại Desktop khi còn outbox: thao tác được giữ và gửi lại sau đăng nhập.
- [ ] Xe máy không thể vào ô ô tô và ngược lại; kiểm tra cả gán cố định, đỗ nhờ và điều phối.
- [ ] Tiền mặt, chuyển khoản, POS; mã giao dịch ngoài bắt buộc và biên nhận đúng số tiền server.
- [ ] Chốt đối soát đúng ca/người thu; giao dịch đã chốt không chốt lần hai.
- [ ] Camera ngày, đêm, ngược sáng, mưa, biển bẩn/che và RTSP mất kết nối.
- [ ] Khuôn mặt MATCH/REVIEW/NO_MATCH; chỉnh tay yêu cầu đúng ca, quyền và lý do.
- [ ] Replay evidence bị từ chối; dữ liệu private không truy cập được qua URL public.
- [ ] Thu hồi phiên Desktop có hiệu lực; API không JWT trả 401.
- [ ] Barrier mất kết nối/cảm biến lỗi hiển thị cảnh báo và không báo thành công giả.
- [ ] Mất điện giữa giao dịch; sau khởi động không thu phí hoặc tạo lượt hai lần.
- [ ] Backup thử nghiệm phục hồi được trên môi trường cách ly và checksum khớp.

## 6. Phụ thuộc thiết bị thật

Adapter relay/PLC, cảm biến vòng từ, RFID, máy in và POS chỉ hoàn thiện sau khi có hãng, model,
SDK/protocol và môi trường thử. Không được thay bộ mô phỏng bằng gọi thiết bị trực tiếp trong form;
mọi driver phải triển khai qua interface tương ứng và có timeout/trạng thái lỗi.
