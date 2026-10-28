# Hiệu chỉnh ngưỡng khuôn mặt

Trạng thái: **quy trình và công cụ đã sẵn sàng; số liệu còn chờ ảnh/video camera thật của bãi xe.**
Giá trị trong tài liệu này là mặc định của mã nguồn, chưa phải kết quả đo trên hiện trường.

## Ngưỡng đang dùng

| Biến | Mặc định | Nơi khai báo | Ý nghĩa |
| --- | --- | --- | --- |
| `FACE_MATCH_THRESHOLD` | `0.363` | `anpr-service/app/face_engine.py` | Điểm cosine tối thiểu để kết luận `PASS` |
| `FACE_REVIEW_THRESHOLD` | `0.300` | `anpr-service/app/face_engine.py` | Biên dưới của dải `REVIEW` |
| `FACE_DETECTION_THRESHOLD` | `0.60` | `anpr-service/app/face_engine.py` | Độ tin cậy tối thiểu của YuNet khi phát hiện mặt |
| `LIVENESS_LIVE_MIN` | `0.55` | `anpr-service/app/main.py` | Điểm motion-blink tối thiểu để coi là người thật |
| `LIVENESS_SPOOF_MAX` | `0.25` | `anpr-service/app/main.py` | Dưới mức này kết luận `SPOOF` |

Ba dải quyết định trong `FaceEngine.compare`:

```text
score >= FACE_MATCH_THRESHOLD            -> PASS   (mở barrier)
FACE_REVIEW_THRESHOLD <= score < MATCH   -> REVIEW (nhân viên xác nhận qua ManualOverrideDialog)
score < FACE_REVIEW_THRESHOLD            -> REJECT (không mở barrier)
```

Ảnh tĩnh hoặc chuỗi khung hình không đủ để chấm chuyển động trả `NOT_EVALUATED`, không được coi là đã xác thực liveness.

## Quy trình hiệu chỉnh

1. **Thu mẫu.** Tối thiểu 30 người, mỗi người 5–8 ảnh, chụp ở đúng vị trí camera dùng thật: sáng trong nhà, ngược sáng, tối, có/không đeo kính, độ nghiêng đầu khác nhau. Mỗi người một thư mục riêng.
2. **Đặt dữ liệu ngoài git.**

   ```text
   .artifacts/face-samples/
     nguyen-van-a/01.jpg 02.jpg 03.jpg
     tran-thi-b/01.jpg 02.jpg
     ...
   ```

   `.artifacts/` đã nằm trong `.gitignore`; không đưa ảnh chân dung thật vào kho mã nguồn.
3. **Chạy công cụ đo.**

   ```cmd
   anpr-service\.venv\Scripts\python.exe tools\face-threshold-report.py ^
       --input .artifacts\face-samples ^
       --output .artifacts\face-threshold-report.md
   ```

   Công cụ dùng đúng YuNet + SFace của ANPR service, nên điểm đo được chuyển thẳng thành ngưỡng cấu hình. Ảnh không phát hiện được khuôn mặt sẽ bị bỏ qua và đếm riêng.
4. **Đọc báo cáo.** File `.md` và `.json` cùng tên ghi:
   - phân bố điểm genuine (cùng người) và impostor (khác người): min, P05, mean, P95, max;
   - ngưỡng `MATCH` cho balanced accuracy cao nhất;
   - ngưỡng `REVIEW` giữ FPR không vượt `--target-far` (mặc định 1%).
5. **Áp dụng.** Đặt biến môi trường trước khi chạy `scripts\run-anpr.cmd` (hoặc trong `.env.local` của máy ANPR), rồi khởi động lại ANPR service vì ngưỡng được đọc lúc nạp module.

   ```text
   FACE_MATCH_THRESHOLD=0.380
   FACE_REVIEW_THRESHOLD=0.320
   ```

6. **Ghi lại.** Lưu ngày, thiết bị, điều kiện sáng và file báo cáo cạnh nhau. Khi đổi camera hoặc đổi mùa ánh sáng, thu lại mẫu và so sánh với lần trước.

## Cách đọc kết quả

- Nếu dải `REVIEW` hẹp hơn `0.02`, dữ liệu chưa tách biệt đủ: thêm người, thêm điều kiện sáng, hoặc kiểm tra lại ảnh đăng ký có đúng người hay không.
- Nếu `P05` của genuine thấp hơn `P95` của impostor, hai tập chồng lấn: không thể chọn ngưỡng nào vừa không bỏ sót người thật vừa không cho người lạ vào. Cần cải thiện chất lượng ảnh đăng ký trước khi chỉnh ngưỡng.
- Tăng `MATCH` làm giảm khả năng người lạ vào nhưng tăng số ca phải xác nhận tay; đây là đánh đổi nghiệp vụ, không chỉ là tham số kỹ thuật.

## Liveness

Ngưỡng liveness hiện dựa trên chuyển động giữa các khung hình và thay đổi vùng mắt (`motion-blink/1.1`). Cách hiệu chỉnh:

1. Quay hai bộ video ngắn ở đúng camera: người thật đi qua (≥30 clip) và ảnh in/điện thoại đưa trước camera (≥30 clip).
2. Chấm từng clip qua `POST /face/verify-camera` và ghi lại `liveness.score`.
3. Chọn `LIVENESS_LIVE_MIN` cao hơn điểm cao nhất của bộ giả và `LIVENESS_SPOOF_MAX` thấp hơn điểm thấp nhất của bộ thật; nếu hai khoảng chồng nhau thì cần thêm tín hiệu (chiều sâu, hồng ngoại) chứ không chỉ đổi số.

Cơ chế hiện tại **không** chống được video phát lại chất lượng cao hoặc deepfake; đó là giới hạn đã ghi trong [PROJECT_GAPS](../../PROJECT_GAPS.md).

## Giới hạn của báo cáo

- Số liệu chỉ đúng trên mẫu đã thu, không phải cam kết cho mọi camera hay mọi người.
- Đây không thay thế checklist nghiệm thu hiện trường (ban đêm, mưa, biển số bẩn, mất mạng).
- Kết quả đo phải lưu cùng phiên bản mô hình; đổi file `.onnx` thì phải đo lại.
