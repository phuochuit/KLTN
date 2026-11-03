# LumiPark Desktop WPF

WPF trên .NET 8 dùng chung DesktopCore với WinForms; đã tích hợp workspace, payment, evidence/zoom/hiệu chỉnh, sơ đồ/điều phối, health/journal, cài đặt, tổng kết ca và barrier mô phỏng. Quality gate độc lập: Core 76/76, WPF 43/43, WinForms 12/12; cả hai build Release đạt. WinForms và launcher mặc định vẫn là baseline. Xem [bàn giao parity và bằng chứng](../docs/review/desktop-wpf-migration.md); [báo cáo prototype](../docs/review/desktop-wpf-prototype.md) là tài liệu lịch sử.

## Build và kiểm thử

Chạy từ repository root:

```powershell
dotnet build desktop-wpf/LumiPark.Desktop.csproj -c Release
dotnet test desktop-wpf.tests/LumiPark.Desktop.Tests.csproj -c Release
dotnet test desktop-core.tests/DesktopCore.Tests.csproj -c Release
```

Gate local cuối có báo cáo TRX mới với test được thực thi, không failure/skipped. Workflow `Desktop WPF` bổ sung kiểm chứng tự động; xem kết quả GitHub CI thực tế trên PR.

Chạy mặc định mở đăng nhập thật, không tự điền tài khoản hoặc dữ liệu mẫu. Chỉ đăng nhập khi đã biết rõ máy chủ/môi trường đích:

```powershell
dotnet run --project desktop-wpf/LumiPark.Desktop.csproj -c Release
```

Demo dùng HTTP handler trong bộ nhớ và không chuyển tiếp tới hệ thống thật. Dùng fixture riêng dưới `.artifacts` cho mỗi phiên:

```powershell
dotnet run --project desktop-wpf/LumiPark.Desktop.csproj -c Release -- --demo --screen=workspace --fixture-dir=D:/DoAn/KhoaLuan/KLTN/.artifacts/agent-work/desktop-wpf-migration/manual-fixture
```

`--width=900 --height=650` đặt kích thước logical, không thay DPI Windows. `--diagnostics=<đường-dẫn-json>` ghi DPI, kích thước, bộ nhớ và thời gian tới `ContentRendered`; đây không phải benchmark cold-start hoặc so sánh hiệu năng.

## An toàn và giới hạn

- Auth và nghiệp vụ đi qua `ParkingApiClient`; server quyết định quyền, phí, gói và bằng chứng.
- WPF entry/exit/dispatch là single-attempt. Kết quả không xác định giữ `Unknown` trong journal metadata riêng và khóa mutation tiếp theo; không tự replay hoặc có thao tác xóa/resolve. Refresh bản đồ không đối soát. Authoritative recovery hiện **BLOCKED** vì chưa có API/quy trình có thẩm quyền theo mã thao tác.
- Journal không chứa request body, ảnh hoặc token. Queue WinForms chỉ được đọc để phát hiện pending/review; WPF không flush queue.
- Contract xác nhận xe ra dùng biển số/mã thẻ; server chọn phiên và tính lại phí. Client ticket không ràng buộc phiên/phí server, và không ngăn thay đổi đồng thời ở trạm khác.
- Demo media và response là dữ liệu tổng hợp. Không có tuyên bố camera/AI thật, settlement, stream network hoặc phần cứng barrier đã được xác minh.
- Native QA ở DPI 125% đã kiểm tra compact/camera, sơ đồ, chọn xe bằng bàn phím, health và Unknown qua restart. Các hành trình khác và giới hạn cụ thể được ghi trong tài liệu bàn giao; DPI khác, Narrator, High Contrast và nhiều màn hình chưa được xác minh.

Màu sắc lấy từ `spring-web/src/main/frontend/styles/tokens.css`; font Be Vietnam Pro được nhúng. Nguồn và giấy phép font ở [Assets/Fonts/README.md](Assets/Fonts/README.md).
