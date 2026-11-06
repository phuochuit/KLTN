# Kiểm thử cấp hệ thống và tooling

Trang này là entry point độc lập cho runner/CI. Tài liệu nghiệp vụ, coverage và quy ước workspace đầy đủ thuộc bộ `docs/`, được xuất bản riêng; không cần thesis tooling để chạy các suite CI bên dưới.

**Đối chiếu current main `abd6183`, ngày2026-10-24:** đợt docs-only này không chạy app tests/build/startup/DB/camera/install. Report mới được cung cấp và đọc là tooling **30/30**, exit0, không failures/errors/skips, run `20261024T160113Z-tooling-013c226b` dưới `.artifacts/test-runs/`; không là app PASS. Kết quả108/128 Spring,12 Desktop,10 ANPR trong tài liệu khôi phục thuộc nhánh Security/feature + WIP, không dùng làm baseline main. Assertions hiện hành và lịch sử tách tại [testing strategy](../docs/testing/strategy.md).

## Suite hiện có

Test nội bộ vẫn ở `spring-web/src/test/`, `desktop-winform.Tests/` (giữ đúng casing Git), `anpr-service/tests/`. `tests/tooling/` kiểm runner và tool CLI, không kiểm app E2E. Main Desktop hiện chỉ có bốn **xUnit OfflineOutboxStore tests**, không có HTTP authentication tests/MSTest của feature branch. ANPR có helper recognizer/token tests và TestClient face contract dùng fakes, không đủ middleware/auth/AI accuracy coverage chỉ từ tên suite.

Từ root, PowerShell5.1+ trên Windows:

```powershell
# Chuẩn bị JAVA_HOME trỏ tới JDK21 đã cài; không copy đường dẫn máy người khác.
.\scripts\test\run-tests.ps1 -Suite spring
.\scripts\test\run-tests.ps1 -Suite desktop
.\scripts\test\run-tests.ps1 -Suite anpr
.\scripts\test\run-tests.ps1 -Suite all
.\scripts\test\run-tests.ps1 -Suite tooling
```

- `all` chỉ Spring/Desktop/ANPR, chạy tuần tự, tiếp tục thu kết quả suite sau khi suite trước lỗi và trả exit khác0 nếu có lỗi/blocked.
- Spring cần JDK21 (`JAVA_HOME`) và Maven tại `.cache/toolchains/apache-maven-3.9.11/` hoặc PATH; runner không tự đổi Java hoặc tải Maven.
- Desktop cần global .NET SDK hỗ trợ target8 và Windows runtime; `dotnet test --configuration Release` bao gồm build. Runner ghi xUnit outbox component boundary, không UI/live Spring.
- ANPR cần venv và `anpr-service/requirements-test.txt`: include runtime requirements, pin pytest8.4.2/httpx0.28.1. Không phải transitive lock hoặc supply-chain audit; runner không cài dependencies. Chưa cài/chạy app suite trong đợt này; helper/fake-face tests không đánh giá model/camera.
- Tooling PowerShell không cần Pester, không nằm trong `all`. Runner còn có lựa chọn `thesis` cho bộ công cụ tài liệu được xuất bản riêng; lựa chọn này cần CLI/tests/venv riêng, không thuộc prerequisites hoặc CI matrix này.
- Direct command vẫn hợp lệ cho focused tests nhưng phải thu report mới và đặt cache/temp tương đương. Không chạy trực tiếp song song cùng suite với runner.
- Root `pytest.ini` chỉ chọn ANPR tests và pythonpath tương ứng, không phải lệnh chạy mọi stack; dùng runner `all` cho ba module. Pytest config của ANPR/demo tách cache theo suite.

Report ở `.artifacts/test-runs/<run-id>/`: XML/TRX, combined stdout/stderr, tool version và summary. Exit0 + report có test thực thi và không failure mới được PASS; missing/malformed report, zero/all-skipped không PASS. Mặc định không có screenshots vì không chạy UI.

Cache/temp tái tạo ở `.cache/`; mỗi lượt có report/temp riêng, không tái sử dụng hoặc tự xóa report cũ. Không đưa local config, credentials, operational media, `.cache/` hoặc `.artifacts/` vào Git. Giữ native `target/`, `bin/`, `obj/` của từng module.

**Ranh giới Spring trong PR:** test resources đặt H2 driver/dialect và JWT off, nhưng không tự bảo đảm mọi class có URL/media cô lập. `LoginTestIsolation` cấp H2 in-memory/create-drop và media root riêng cho các lớp đã đăng ký, gồm ParkingFlow và DailyPackageValidity; dynamic properties ghi đè cả các annotation path cũ của ParkingFlow. Demo account bootstrap bị tắt trong fixture này; DesktopJwtIntegrationTest có synthetic keys riêng. Các class khác vẫn cần kiểm annotation/config hiệu lực trước khi chạy. Runner chỉ tách TEMP/TMP/java.io.tmpdir, không biến môi trường thật thành test DB. Không copy create-drop sang DB thật hoặc coi source isolation là bằng chứng live E2E. Không có Flyway dependency/migration suite trên main đã tích hợp.

## GitHub Actions

[Workspace](../.github/workflows/workspace.yml) tự chạy khi PR/push main: stdlib docs checker, tracked-path hygiene, event-diff whitespace, PowerShell/Python tooling syntax, docs-checker unittest và runner self-test. Không application build/test, DB/camera, thesis generation hoặc pip install; Python3.12 được chuẩn bị riêng.

[Verify](../.github/workflows/verify.yml) giữ bốn job Windows Spring/Desktop/ANPR/tooling và Desktop Release build, chỉ `workflow_dispatch` theo duyệt của chủ dự án, không tự chạy khi push/PR. Chuẩn bị JDK21/.NET8/Python3.12 và cài ANPR test manifest khi được dispatch. Chưa chạy pipeline hoặc application suite trong đợt này, không chứng nhận prerequisites/runtime chỉ vì YAML hợp lệ. Maven cần có trong PATH; main integration media/datasource vẫn cần ranh giới riêng đã nêu phía trên.

Token workflow chỉ có `contents: read`, checkout không lưu credentials; không cấp production secrets hoặc DB thật. Artifact chỉ gồm summary/XML/TRX của lượt mới, không upload toàn workspace/local config/media/log thô. Kiểm tra đường dẫn nhạy cảm và tracked diff là hygiene cơ bản, không thay secret scan toàn lịch sử hoặc code review. CI trên GitHub chưa được chạy chỉ vì workflow có trong source; required branch checks cần cấu hình/quyền riêng.

Workspace CI kiểm whitespace theo merge-base/head của PR hoặc before/head của push, không chỉ worktree chưa commit. Tác giả/reviewer cũng kiểm `git diff --check <base> <head>` trước xuất bản; không thay bằng lệnh không range.

TRX được kiểm cả counters và `ResultSummary.outcome`: chỉ `Completed`/`Passed` được chấp nhận; timeout, not-runnable, inconclusive, disconnected và aborted-run không được coi là PASS. `counts.ReportFailed` biểu thị summary không hợp lệ/không thành công, độc lập với số test failures.

Không xóa `desktop-winform.Tests/` khi dọn output: runner vẫn dùng project này. `scripts/check-workspace.ps1` phát hiện các thư mục test cũ và file `.log` trực tiếp trong `spring-web/target/`; giữ build/report chuẩn theo workspace policy và bảo toàn log trước khi clean build. Path guard không xác nhận mọi main integration media đã isolate hoặc toàn workspace sạch secrets.

## Focused Spring test

Từ root trong PowerShell, với `JAVA_HOME` là JDK21; chỉ chạy khi có phê duyệt tests riêng, không chạy đồng thời với runner Spring. Ví dụ unit `PlateNormalizerTest` dưới dùng cùng Maven resolver/report/temp của runner, nhưng **không tạo `summary.json`**. Main không đọc `parking.test.work-directory` của feature tests nên không truyền property đó như isolation guarantee. Nếu thay bằng integration test, phải kiểm datasource/media riêng trước; không tái sử dụng run directory.

```powershell
. .\scripts\tool-paths.ps1
$root = (Get-Location).Path
$maven = Resolve-ParkingMaven $root # bundled Maven, fallback mvn.cmd trong PATH
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-spring-focused-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runDir = Join-Path $root ".artifacts/test-runs/$runId"
$reports = Join-Path $runDir 'reports'
$temp = Join-Path $root ".cache/test-temp/$runId"
New-Item -ItemType Directory -Path $reports, $temp | Out-Null
$savedTemp, $savedTmp = $env:TEMP, $env:TMP
Push-Location (Join-Path $root 'spring-web')
try {
    $env:TEMP = $env:TMP = $temp
    & $maven --version 2>&1 | Tee-Object -FilePath "$runDir/version.log"
    if ($LASTEXITCODE -ne 0) { throw 'Maven version probe failed.' }
    if ((Get-Content "$runDir/version.log" -Raw) -notmatch 'Java version: 21[\.,]') { throw 'JDK21 required.' }
    & $maven '-Dtest=PlateNormalizerTest' test "-Dparking.test.reports-directory=$reports" "-Djava.io.tmpdir=$temp" '-DfailIfNoTests=true' 2>&1 | Tee-Object -FilePath "$runDir/test.log"
    $testExitCode = $LASTEXITCODE
    if ($testExitCode -ne 0) { throw "Focused test failed: exit $testExitCode; see $runDir" }
} finally {
    $env:TEMP, $env:TMP = $savedTemp, $savedTmp
    Pop-Location
}
```

Chỉ kết luận PASS sau khi kiểm exit code và XML mới trong `$reports`: có test thực thi, không failures/errors, không toàn skipped. Giữ logs/report kể cả khi lỗi; đây vẫn là kiểm thử nội bộ, không live E2E.

## API/E2E chưa triển khai

Vị trí được duyệt: `tests/api/`, `tests/e2e/web/`, `tests/e2e/desktop/`, fixtures synthetic ở `tests/fixtures/`; chỉ tạo suite/helper khi có implementation thật.

Trước live run phải xác nhận URL, DB/schema/media test độc lập, tài khoản synthetic/quyền, credential channel và seed/cleanup. Không tự dùng datasource hiện tại, reset DB, mở camera hoặc gọi mutation route. Spring thật + ANPR giả cần ghi nhãn như vậy, không full E2E. Web harness và Windows UI Automation chưa chọn; Playwright không test WinForms. Desktop outbox tests hiện có không thay cho client↔Spring network test; HTTP doubles của feature branch chỉ là lịch sử và cũng không live network proof.

## Package-policy integration regression coverage

The approved Web integration adds/extends Spring coverage in `DailyPackageValidityIntegrationTest` and `ParkingFlowIntegrationTest` for inclusive DAILY validity, fixed 30-day MONTHLY validity, ACTIVE-only activation, legacy `PER_VISIT` rejection for new purchases, and optional package activation in the combined registration POST. Root owns execution; these tests are **not yet reported as run** by this handoff. Focused execution should include both classes plus `CsvExportIntegrationTest` (upstream CSV preservation) and frontend model/asset checks for cards, pricing, and registration. Use the existing isolated test runner and fresh report directory; do not infer PASS from test source.


## Final review LumiPark Web

[Kết quả và giới hạn](../docs/review/web-final-review-integration.md) ghi số liệu mới nhất theo từng lớp, không gộp các lượt trùng thành test mới. Không lấy report H2/mock thành live E2E. Legacy `login.mjs`/`management-core.mjs` hiện bắt buộc ownership của listener đúng process tree LoginBrowserHarnessTest; thiếu/stop/sai marker thì từ chối trước browser. Chạy regression guard độc lập từ root: `node --test tests/e2e/web/isolated-harness.test.mjs` (không khởi động ứng dụng hoặc mutation nghiệp vụ). Các helper opt-in không thuộc default frontend test glob; những API browser ngoài quyền hiện hành không được sử dụng chỉ vì có trong source lịch sử.
