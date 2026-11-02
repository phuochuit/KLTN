# Tổ chức workspace và vòng đời file

[Mục lục](../README.md) · [Testing](../testing/strategy.md) · [Vận hành](../operations/setup-and-troubleshooting.md) · [ADR-002](../decisions/0006-workspace-and-test-artifacts.md)

## Mục đích

Đây là quy ước canonical cho người và agent, không thay kiến trúc ứng dụng Desktop → Spring → ANPR. Phân loại theo vai trò/vòng đời, không theo tên file có dấu chấm hay theo việc agent tạo ra nó.

**Quy ước được duyệt ≠ inventory đã triển khai:** layout dưới giữ kiến trúc workspace của chủ dự án. Đối chiếu main (`abd6183`) chỉ xác nhận các source/config được dẫn; thư mục output/local hoặc câu chuyện chuyển đổi lịch sử không chứng minh chúng còn tồn tại, đã tracked hoặc suite đã chạy. Backup dưới `.artifacts/` không thay source module hiện hành.

| Nơi lưu | Chứa gì | Git / vòng đời |
| --- | --- | --- |
| Source module, `tests/`, `scripts/`, `tools/` | Code/config/fixtures synthetic dùng chính thức | Có thể version control; thay đổi cần kiểm chứng |
| `spring-web/src/main/`, `desktop-winform/`, `anpr-service/app/`, `face-verification-demo/` | Source Web, Desktop, AI và demo riêng | Giữ theo module; không nhân bản source vào artifacts, root helper hoặc test harness |
| `scripts/database/` | SQL thủ công và công cụ chuyển media có kiểm soát | Source vận hành, không output/cache; đọc không cấp quyền chạy DB/migration |
| `.cache/downloads/`, `.cache/toolchains/` | Gói tải về và công cụ có thể phục hồi, hiện có Maven3.9.11 | Local ignored; xóa khi không dùng, phải chuẩn bị lại nếu không có tool trong PATH |
| `.cache/dotnet-cli/` | `DOTNET_CLI_HOME`, không phải nơi cài SDK | Local ignored; không lưu secrets/config quan trọng ở đây |
| `.cache/pytest/<suite>/`, `.cache/test-temp/<run-id>/`, `.cache/pip/` | Cache, file tạm riêng lượt chạy, package cache | Local ignored; có thể tái tạo, không phải bằng chứng kiểm thử |
| `.cache/locks/` | File handle khóa suite để tránh hai runner dùng chung build output | Không xóa khi runner còn chạy; handle được OS nhả khi process kết thúc |
| `.artifacts/test-runs/<run-id>/` | `summary.json`, reports XML/TRX, logs, screenshots khi có | Local ignored; không tự xóa/ghi đè; backup riêng nếu cần giữ lâu dài |
| `.artifacts/tool-runs/<run-id>/` | Output tạo/chuyển đổi/phân tích tài liệu | Local ignored; không tự coi là requirement hoặc test PASS |
| `.artifacts/agent-work/<task-id>/` | Script thử nghiệm, bản nháp, inventory và handoff local | Không thay source chính thức hoặc task plan/TODO; không tự xóa |
| `evidence/` nếu được chủ động bổ sung | Tư liệu được chọn giữ, provenance/privacy đã xem xét | Không tự promote output thô vào đây; bộ tư liệu gốc cũ đã được chủ dự án yêu cầu bỏ, xem [tổng hợp](../thesis/material-summary.md) |

## Các ngoại lệ có chủ đích

- `spring-web/target/`, Desktop `bin/`, `obj/` giữ chuẩn build. Runner lưu report trực tiếp vào artifact để clean build không xóa mất bằng chứng.
- `.venv/`, `node_modules/` giữ theo hệ sinh thái. Ignore không untrack file từng được commit.
- Model, OCR resources, operational DB, resident private-media và gate evidence nghiệp vụ **không** thuộc cache test. Không xóa/chuyển chúng trong cleanup workspace.
- Main [application.properties](../../spring-web/src/main/resources/application.properties) là config tracked: MySQL placeholders/Hibernate update/JWT mặc định true/private-storage/AI. Không có application.yml hay Flyway migration directory trong main; `application-laragon.properties` local có thể override và chứa cấu hình nhạy cảm, không chép vào docs/report. Không áp chính sách local-ignored properties của nhánh cũ lên file tracked hiện tại.
- Main dùng `${parking.private-storage-path:private-data}/residents` và `/evidence` cho dữ liệu riêng tư; `${parking.upload-dir:uploads}/residents` chỉ là nguồn đọc legacy. Đây là dữ liệu nghiệp vụ cần bảo vệ/backup, không `.cache/` hoặc test artifact. Đường dẫn hiệu lực phụ thuộc working directory/config, chưa được kiểm runtime.
- Desktop [OfflineOutboxStore](../../desktop-winform/OfflineOutboxStore.cs) và [GateStationSettingsStore](../../desktop-winform/GateStationSettingsStore.cs) dùng LocalApplicationData/ParkingGateDesktop cho `offline-outbox.json`/`station-settings.json`; đây là **operational state ngoài repo**, không scratch mặc định. Không chuyển queue/settings vào `.artifacts/`, không publish nội dung request/camera URI có thể nhạy cảm. Không có bằng chứng encryption/backup/resync chỉ từ vị trí file.
- `AGENTS.md` và `.opencode/skills/` chỉ được đưa lên Git/PR khi người dùng duyệt riêng cả phạm vi và thao tác, sau khi rà secrets/PII, đường dẫn máy và provenance. Phần còn lại của `.opencode/`, `.codegraph/`, `tasks/`, backups và internal design context vẫn local-only; duyệt commit ứng dụng không cấp quyền chia sẻ tooling.
- `.cache/dotnet-cli-legacy`, `.cache/pytest/legacy-root`, `.cache/test-temp/legacy-*` là trạng thái cũ được giữ khi chuyển đổi, không phải đường dẫn chạy mặc định.
- `survey_analysis/` là ngoại lệ lịch sử cần xét installation/provenance trước tái lập. Main không track file nào dưới `survey_analysis/` (bao gồm `node_modules`); README local còn kể dependency tree tracked của workspace/nhánh trước, **không phải inventory main hiện tại**. Không khôi phục script/dependencies hoặc tự cài package để làm đúng mô tả cũ; nếu còn local user work, giữ nguyên và xác nhận trước cleanup. Chính sách output chính thức vẫn là `.artifacts/tool-runs/`.

## Test source và trách nhiệm

| Nơi | Ranh giới |
| --- | --- |
| `spring-web/src/test/` | Unit, Spring/MockMvc/H2 integration và API/security nội bộ |
| `desktop-winform.Tests/` | xUnit outbox component tests hiện tại; HTTP doubles/MSTest thuộc feature history, không UI automation |
| `anpr-service/tests/` | pytest: helper, FastAPI TestClient/fake recognizer |
| `face-verification-demo/tests/` | App demo riêng, không chứng minh gate integration |
| `tests/tooling/` | Kiểm thử runner và path/CLI công cụ |
| `tests/api/`, `tests/e2e/web/`, `tests/e2e/desktop/` | Vị trí dành cho suite cấp hệ thống khi được triển khai; chưa có live harness. Không tạo thư mục rỗng để giả định coverage |

Chạy theo [tests/README](../../tests/README.md). `all` chỉ chạy Spring/Desktop/ANPR, không startup app, mở camera, seed DB hoặc tự cài dependencies. API/E2E cần xác nhận DB/media test độc lập và harness trước. Playwright dành cho browser, không WinForms.

Các lựa chọn runner/CI mô tả khả năng từ source, không kết quả chạy. Đối chiếu docs-only không chạy build/test/dependency/service/SQL; historical suite totals hoặc artifact backups không được promote thành main PASS.

`desktop-winform.tests/` là project test tham chiếu ứng dụng Desktop, không phải bản sao hoặc output của `tests/`. Không xóa project để dọn workspace; chỉ `bin/`, `obj/` là build output có thể tái tạo. `tests/tooling/` kiểm công cụ, `scripts/test/` điều phối chạy, còn `.artifacts/test-runs/` giữ kết quả: bốn vai trò khác nhau.

## Mỗi lượt chạy

- Run ID: UTC `yyyyMMddTHHmmssZ-<suite>-<random>`, không tái sử dụng thư mục lượt trước.
- Report mới là điều kiện để PASS, ngoài exit code0; không có test hoặc tất cả skipped không đạt. Report cũ không được dùng thay report lượt mới.
- Với TRX, summary phải có outcome `Completed` hoặc `Passed`, counters không có lỗi/timeout hoặc các trạng thái bất thường như not-runnable/inconclusive/disconnected. Summary bị hủy, thiếu outcome hoặc chưa hoàn tất không PASS dù counters có test thành công và exit0.
- `summary.json` ghi command/cwd/tool version log, thời gian, exit/result/counts, paths và ranh giới thật/giả. `interrupted` không có `finished_at` là marker chưa finalize, không phải PASS. Hard kill không bảo đảm finally/log đầy đủ.
- Runner khóa cùng suite; không chạy Maven/.NET trực tiếp song song với runner vì lệnh ngoài không dùng khóa này.
- Nếu hard-kill runner, child process có thể còn chạy dù OS đã nhả handle khóa của parent; phải xác nhận child đã dừng trước chạy lại, không tự kill process để vượt khóa.
- Biến TEMP/TMP/CLI-home chỉ thay trong phạm vi process và được khôi phục; không sửa môi trường toàn máy.
- Không dump environment, request body, Authorization, mật khẩu/keys hoặc dữ liệu cá nhân vào report. Logs có thể chứa metadata máy và dữ liệu synthetic; vẫn cần review/redact trước chia sẻ.

## Agent tạo file thế nào?

1. File sẽ dùng chính thức → đúng module hoặc `tests/`, `scripts/`, `tools/`.
2. File có thể tái tạo và không cần lưu làm bằng chứng → `.cache/`.
3. File cần giữ từ một lượt chạy/nhiệm vụ → `.artifacts/` đúng nhóm.
4. Không chắc nguồn hoặc có user work → giữ lại, ghi nhận và hỏi trước khi loại bỏ.

Không tạo root `tmp/`, `.test-tmp/`, `.dotnet-cli-home/`, `.pytest_cache/`, `qa_*` cho công việc mới. Scratch ngoài repo không là mặc định. Hạn chế path này áp dụng file do dự án/agent kiểm soát; không hứa mọi cache nội bộ của OS/thư viện đều đã được chuyển.

## Dọn dẹp, khôi phục và chuyển đổi

- Không có automatic pruning hoặc lệnh recursive cleanup chung. Dọn cache cần xác nhận tiến trình đã dừng và phạm vi cụ thể; không dọn cả `.cache/` khi tools đang dùng.
- `.artifacts/` không là backup chỉ vì nằm trong repo; Git ignore không bảo toàn dữ liệu. Giữ/backup theo nhu cầu chủ dự án, không tự đặt TTL xóa.
- **Lịch sử, không main inventory:** đợt chuẩn hóa trước ghi nhận `tmp/`/logs ở `.artifacts/agent-work/workspace-standardization/legacy/` và archive Spring media/temp ở `.artifacts/agent-work/workspace-review-followup/archive-*/`, kèm manifests. Không dùng các đường dẫn backup đó làm source hiện hành hoặc kết quả test mới, không tự khôi phục chúng. Nếu gặp `check_db.sql` có truy vấn PII, không dùng làm smoke test hay chạy DB tự động.
- Workspace guard từ source phát hiện các tên cũ `test-gate-evidence`, `test-revenue-gate-evidence`, `test-uploads`, `test-revenue-uploads`, `test-tmp`, `test-tmp-*` hoặc file `.log` trực tiếp trong `spring-web/target/`. Không cấm native build directories, `surefire-reports/` hoặc `test-work/`; guard không tự di chuyển/xóa file.
- Không khôi phục toàn bộ workspace bằng reset/clean. Khi cần rollback đường dẫn, đối chiếu manifest/hash, không ghi đè phiên bản mới; phải cập nhật caller/config cùng lúc.
- Nhánh tài liệu `docs/workspace-architecture` có `scripts/check-workspace.ps1` để phát hiện một số vi phạm đã biết; file này **không có trong `main`**, và dù có cũng không phải sandbox, secret scanner hay chứng nhận kiến trúc toàn bộ source.
