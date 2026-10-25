# Công cụ DOCX lịch sử

Các script được giữ để bảo toàn khả năng nghiên cứu/tái tạo khi có đúng input. Nội dung cũ có thể nhắc WPF/PostgreSQL/charging khác dự án hiện tại; **không** dùng generator làm nguồn acceptance hoặc thay docs canonical. Đợt workspace chỉ sửa CLI/path, không restyle hay sửa policy nội dung.

## Môi trường riêng

Python3.10+, không cài global hoặc vào ANPR venv:

```powershell
python -m venv tools/thesis/.venv
.\tools\thesis\.venv\Scripts\python.exe -m pip install --only-binary=:all: --cache-dir .cache/pip -r tools/thesis/requirements.txt
```

Manifest pin python-docx1.2.0, lxml6.1.3, typing_extensions4.16.0; wheel-only để không chạy source build. Không có claim audit toàn bộ dependency hoặc offline reproducibility. Thiếu wheel phù hợp thì dừng, không tự fallback build/global install.

## Chạy

```powershell
.\tools\thesis\.venv\Scripts\python.exe -B tools/thesis/remove_ev_charging_from_proposal.py --help
.\tools\thesis\.venv\Scripts\python.exe -B tools/thesis/remove_ev_charging_from_proposal.py --input '<DOCX đã chọn>'
.\tools\thesis\.venv\Scripts\python.exe -B tools/thesis/create_project_plan_docx.py
```

- Mọi script nhận `--output`; mặc định file mới trong `.artifacts/tool-runs/<UTC-random>/` dựa repository của script, không dựa cwd.
- Trừ `create_project_plan_docx.py` không cần input, các script yêu cầu `--input`. Không tự khôi phục DOCX cũ từ evidence hoặc tự lấy file ngoài repo.
- Output đã tồn tại hoặc trùng source bị từ chối; writer dùng exclusive-create để không ghi đè cả khi file xuất hiện sau validation. CLI không sandbox filesystem; tool lỗi giữa chừng có thể để lại artifact chưa hoàn chỉnh, không tự coi file có mặt là thành công.
- Ba `fill_thesis_proposal_*` giữ kiểm SHA-256 của template gốc. Một DOCX bất kỳ không phải input tương đương; không hạ/bỏ hash check để ép chạy.
- `remove_ev_charging_from_proposal.py` giữ giả định cấu trúc bảng gốc; fixture synthetic chỉ kiểm đường dẫn/không sửa source, không chứng minh đúng nội dung mọi DOCX hoặc chất lượng layout.

Kiểm CLI/path/import và DOCX synthetic nhỏ, không chạy generator trên tư liệu thật:

```powershell
.\tools\thesis\.venv\Scripts\python.exe -B -m unittest discover -s tests/tooling -p test_thesis_cli.py
# Hoặc lưu JUnit XML/log/summary tự động:
.\scripts\test\run-tests.ps1 -Suite thesis
```

Nguồn công cụ/report options: [python-docx](https://python-docx.readthedocs.io/en/latest/), [lxml](https://lxml.de/), [Surefire reportsDirectory](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#reportsDirectory), [pytest cache](https://docs.pytest.org/en/stable/how-to/cache.html), [dotnet VSTest options](https://learn.microsoft.com/en-us/dotnet/core/tools/dotnet-test-vstest). Không thay version plugin ứng dụng trong đợt này.
