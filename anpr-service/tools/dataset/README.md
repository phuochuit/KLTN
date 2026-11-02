# Bộ dữ liệu đánh giá AI

Thư mục này chỉ chứa **manifest và hướng dẫn**. Ảnh/video thật không được commit
vào Git vì là dữ liệu sinh trắc học và biển số thật.

## Cấu trúc

```text
tools/dataset/manifest.example.csv   Mẫu manifest
tools/ai_evaluation.py               Script tính chỉ số
```

Dữ liệu thật đặt ngoài repo (ví dụ `D:\ai-dataset\khoaluan`) và trỏ tới bằng
`image_path` trong manifest.

## Quy ước manifest

| Cột | Ý nghĩa |
|---|---|
| `sample_id` | ID duy nhất, dùng để ghép với file dự đoán |
| `kind` | `vehicle`, `liveness` hoặc `face` |
| `image_path` | Đường dẫn tương đối tới thư mục dữ liệu |
| `expected_plate` | Biển số đúng, có thể có dấu `-`/`.`/khoảng trắng |
| `expected_vehicle` | `CAR` hoặc `MOTORBIKE` |
| `expected_liveness` | `LIVE`, `SPOOF`, `REVIEW` |
| `expected_decision` | `PASS`/`REJECT`/`REVIEW` cho khuôn mặt |
| `condition` | Nhãn điều kiện để chia nhóm khi phân tích |

## Phạm vi tối thiểu

- Ban ngày / ban đêm.
- Thuận sáng / ngược sáng.
- Biển rõ / mờ / nghiêng / bẩn / bị che.
- Gần / xa.
- Xe máy / ô tô.
- Khuôn mặt thật / ảnh in / ảnh trên điện thoại / video phát lại.

Mỗi nhóm nên có tối thiểu 10 mẫu để tỷ lệ có ý nghĩa.

## Quy trình

1. Thu mẫu, đặt tên theo `sample_id` và điền `manifest.csv`.
2. Chạy nhận dạng, xuất `predictions.csv`:

```text
sample_id,plate_text,vehicle_type,liveness_decision,face_decision,elapsed_ms
V-0001,51H88888,CAR,,,412
L-0003,,,SPOOF,,980
F-0002,,,,PASS,350
```

3. Tính chỉ số:

```cmd
cd anpr-service
.venv\Scripts\python.exe tools\ai_evaluation.py ^
  --manifest D:\ai-dataset\khoaluan\manifest.csv ^
  --predictions D:\ai-dataset\khoaluan\predictions.csv ^
  --out D:\ai-dataset\khoaluan\reports
```

4. Kết quả:

```text
reports\ai-report.md
reports\ai-metrics.csv
```

## Cách đọc chỉ số

| Chỉ số | Ý nghĩa |
|---|---|
| Plate detection rate | Tỷ lệ ảnh có phát hiện biển |
| OCR exact match | Tỷ lệ đọc đúng hoàn toàn sau chuẩn hóa |
| Vehicle type accuracy | Tỷ lệ phân loại xe đúng |
| Liveness accuracy | Tỷ lệ LIVE/SPOOF/REVIEW đúng |
| Face FAR | Tỷ lệ cho qua người không đúng (càng thấp càng tốt) |
| Face FRR | Tỷ lệ từ chối người đúng |
| P95 latency | 95% request nhanh hơn mức này |

## Lưu ý bảo mật

- Không commit ảnh CCCD, ảnh khuôn mặt hay biển số thật.
- Không đưa đường dẫn tuyệt đối chứa tên người vào manifest commit.
- Ảnh CCCD chỉ nằm trong `spring-web/private-data/` và không publish qua URL.
- Khi chia sẻ kết quả, chỉ chia sẻ `ai-report.md` và `ai-metrics.csv`.
