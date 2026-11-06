# ADR-002: Tách cache, artifact và source kiểm thử

## Trạng thái

Accepted — chủ dự án duyệt phương án và cho phép triển khai trong hội thoại ngày 2026-10-23. API/E2E vẫn có checkpoint xác nhận môi trường/harness, không tự thành coverage đã thực hiện.

## Bối cảnh

Root có cache pytest, CLI-home .NET và file tạm/log test lẫn nhau. `tools/` chứa cả Maven tải về và source sinh tài liệu; `tmp/` có cả bản trích cần giữ. Cần kiểm soát vị trí file qua nhiều lượt agent và giữ báo cáo sau clean build.

## Quyết định

- `.cache/` chứa dữ liệu có thể tái tạo; `.artifacts/` chứa output/test report/scratch cần giữ.
- Giữ test nội bộ theo chuẩn Maven/MSTest/pytest; `tests/` sở hữu kiểm thử cấp hệ thống và tooling.
- `scripts/` cho entry points vận hành; `tools/` cho source công cụ, không chứa bundled toolchain hoặc output mới.
- Canonical policy ở [workspace layout](../architecture/workspace-layout.md), luật ngắn ở local `AGENTS.md`, runner/check bảo vệ các workflow chuẩn.

## Lựa chọn không dùng

- Một `.local/` chung: không thể hiện sự khác nhau về vòng đời.
- Temp ngoài repo làm nơi lưu chính: khó kiểm soát/giữ kết quả theo nhu cầu chủ dự án.
- Chuyển tất cả test/build output vào một nơi: phá convention công cụ, không cần để giải quyết vấn đề.
- Tự xóa file theo tên `tmp`, `cache`, `final`: có thể mất nguồn, công việc hoặc bằng chứng.

## Hệ quả

Phải cập nhật caller khi chuyển Maven/tools, lưu kết quả độc lập mỗi run và backup artifacts riêng. Cần prerequisites rõ cho live testing; mock/H2 không full E2E. Không thay policy nghiệp vụ, runtime media, Git authority hoặc permissions của agent.
