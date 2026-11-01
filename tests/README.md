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

**Ranh giới Spring main:** test resource đặt H2 driver/dialect và JWT off; từng integration class khai báo in-memory URL/create-drop, DesktopJwtIntegrationTest bật JWT bằng synthetic keys. ParkingFlowIntegrationTest còn tắt evidence và dùng fixed `target/test-uploads`/`target/test-private`; các class khác cũng có media defaults/relative paths. Runner tách TEMP/TMP/java.io.tmpdir nhưng không sửa các paths này; per-class/JVM media isolation của feature branch chưa có trên main. Phải xác nhận effective test datasource/media và không dùng operational storage trước khi được phép chạy; đừng copy create-drop tới DB thật. Không có Flyway dependency/migration test suite trên main.

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

## API/E2E và browser harness

### LumiPark Login focused/browser harness

Spring tests/build cần Node22+ và frontend dependencies: từ `spring-web` chạy `npm ci --cache ../.cache/npm` trước Maven. `npm test` chạy3 JS tests bằng Node test doubles; không thay browser QA. Maven process-classes build Tailwind CSS/Lucide subset, không tự cài dependencies. Workflow Verify job Spring chuẩn bị Node24.21.0/npm11.19.0 và npm ci; triggers/permissions không đổi.

Focused `LoginPageIntegrationTest` kiểm native bindings/CSRF/assets, feedback, authentication/forced-change/logout/throttle bằng real Spring security + H2, mock ANPR; kết hợp `SecurityAuthorizationIntegrationTest` để kiểm hồi quy trang cũ. Dùng report/temp mới như focused example phía trên. Không bật MockMvc request printing vì có credentials và token trong request.

`LoginBrowserHarnessTest` là opt-in, mặc định skipped để không treo runner. Khi được duyệt, chạy một mình bằng `-Dtest=LoginBrowserHarnessTest -Dparking.test.login-browser.enabled=true -Dparking.test.login-browser.run-directory=<absolute-fresh-run-directory>` cùng report/temp riêng. Directory phải tồn tại dưới `.artifacts/test-runs/` hoặc `.cache/test-temp/`, không có server.port/stop. Harness bind loopback/random port, H2 in-memory riêng, media/cache riêng, không bootstrap account thật; tắt JWT chỉ trong test context và mock ANPR. Đây không phải production config hay gate E2E.

Khi sẵn sàng, file `server.port` chỉ chứa port; truy cập `http://127.0.0.1:<port>/login`. Synthetic fixture identity/credential định nghĩa trong test source, không dùng tài khoản thật; không ghi password, cookies, CSRF hoặc request body vào screenshots/logs. Tạo marker `stop` trong chính run-directory để kết thúc; tự dừng sau15 phút. Không chạy Maven clean/suite khác song song. Xác minh server/build và hash static response trước QA; health không đủ chứng minh nguồn mới.

Browser checks opt-in: từ root chạy `node tests/e2e/web/login.mjs <absolute-or-relative-run-directory>`. Dùng Playwright devDependency đã lock trong spring-web và Microsoft Edge cài sẵn (channel msedge); không tự tải browser. Runner chỉ đọc port từ run-directory dưới .artifacts/test-runs và fixture credential từ test source, không nhận URL tùy ý. Tắt trace/credential screenshots, errors chỉ lưu type/path đã bỏ query, screenshots chỉ lấy form trống/feedback. Lưu browser-checks.json và browser-errors-sanitized.json; legacy destination tự request /favicon.ico được báo riêng, không che lỗi assets mới.

Group 4A focused browser review dùng cùng harness cô lập: `node tests/e2e/web/management-core.mjs <fresh-run-directory> [stage]`. Stage tạo thư mục con mới, không ghi đè; kiểm năm trang ở desktop/mobile/320px, hồ sơ/editor, quota, draft, synthetic save và các nhánh lỗi được script khai báo, audit/settings/password. QR response và camera là mock synthetic; ảnh fixture H2 không chứng minh camera/QR thật, MySQL hoặc quyền runtime production. `checks.json` chỉ xuất hiện khi script hoàn tất; đối chiếu browser exit, harness exit và XML mới, không suy visual approval từ assertion PASS.

Login layout checks đo scroll dimensions và CTA bounds ở1920×1080,1536×864,1440×900,1366×768,1280×720,1024×768 cho default/error/throttled/passwordChanged; output `viewport-measurements.json` và screenshot đúng viewport, không chỉ full-page. Resize cùng page qua17 desktop widths960→1600/bước40px, đo form/padding và lưu `fluid-resize.json`: không width plateau toàn khoảng, không width jump bất thường trong desktop mode; cap đọc544px và chuyển một cột dưới960px là chủ đích. Mobile320/375/390/430px kiểm header→panel24–40px và input/CTA≥48px, lưu `mobile-positions.json`. Không dùng body overflow hidden để giả không cuộn. Mobile320px/landscape812×375 vẫn được cuộn và phải focus tới CTA được; có forced-colors/opaque/reduced-motion QA, không phải WCAG certification hoặc bàn phím ảo thật. Chất lượng thị giác cần xem ảnh trước/sau cùng viewport, không suy từ test PASS. Khi đổi template trong vòng lặp render cần restart harness (Thymeleaf cache mặc định); có thể dùng `-Dspring.thymeleaf.cache=false` **chỉ trên lệnh test harness**, rồi copy đúng template/assets mới vào build output, không đổi runtime config hoặc chạy Maven clean song song.

Chỉ ghi Playwright PASS cho scenarios thực chạy. H2/MockMvc không thay live browser; browser trên H2/mock ANPR cũng không chứng minh target DB hoặc AI integrations. Chưa có suite browser automation tổng quát cho toàn Web.

### Dashboard & Overview browser checks

Dashboard dùng cùng opt-in `LoginBrowserHarnessTest` ở trên (Spring loopback/H2, tài khoản synthetic, ANPR mock), không dùng DB runtime. Chạy từ root:

```powershell
node tests/e2e/web/dashboard.mjs <fresh-run-directory-with-server.port> final
```

Script tạo thư mục `final/` mới và từ chối ghi đè. Có thể dùng stage `baseline`, `shell`, `charts` hoặc `refined` để **render/quan sát trong quá trình dựng UI**; chỉ stage `final` chạy đầy đủ assertion tương tác. Nếu cần lượt final mới với cùng harness, tạo thư mục con mới dưới run, copy duy nhất `server.port` rồi truyền thư mục đó; report phải ghi rõ harness gốc. Không lấy kết quả từ stage preview làm PASS.

Đầu tiên suite đăng nhập thật, gọi API đọc mới và lưu `real-api-empty.png` từ H2. Sau đó route mock31 giao dịch **chỉ trong browser** để kiểm populated/paging/error states; không seed DB thật. Ảnh `viewport-*.png` là visual fixture, không dữ liệu vận hành. Các nhóm kiểm hiện hành sở hữu tại `dashboard-ux.mjs`: phân trang/tìm kiếm/source, URL/reload/Back/Forward, tổng toàn kỳ độc lập trang, không scroll-jump khi đổi trang, drill-down có CTA, range/reset, lỗi/thử lại, summary pending/failed và request supersession, sidebar/dropdown/drawer, forced colors/reduced motion, logout và no-JS. Output `checks.json` và screenshots; counts/source khác với suite Dashboard cũ được giữ trong report lịch sử. Không tự suy từ test count ra visual acceptance hoặc full WCAG.

Dashboard UX/data hiện hành: `node tests/e2e/web/dashboard-ux.mjs <run-directory-with-server.port> final`; `dashboard.mjs` là entry tương thích chuyển tới suite này. Stage preview phục vụ quan sát trước khi chạy final. Suite gọi real isolated overview/summary/transactions trước, sau đó dùng browser-only fixture31 giao dịch để kiểm trang/tìm kiếm/source/URL/history/error/race và layout; không claim fixture là data vận hành. Report mới trong stage riêng, không ghi đè. Có ảnh dropdown thực đang mở, popup ở đáy mobile và sidebar ngắn, không chỉ ảnh trạng thái đóng. Pagination phải giữ tổng toàn kỳ và không làm trang nhảy do bảng bị collapse.

`DashboardQueryIntegrationTest` kiểm DB-side pagination/totals/order/literal search/validation/permissions và midnight-boundary consistency giữa trend/summary/transactions. `DashboardPageIntegrationTest` kiểm render/CSRF/local assets/navigation, H2 totals, gate-staff policy và KPI OPEN dẫn tới danh sách thực sự lọc. Chạy focused cùng `LoginPageIntegrationTest,SecurityAuthorizationIntegrationTest` theo lệnh focused ở trên. Login browser regression kiểm Dashboard dùng CSS riêng, rồi mở `/vehicles` để kiểm legacy `app.css` và confirmation dialog vẫn cô lập.

Nhánh dữ liệu demo được chủ dự án cấp: `node tests/e2e/web/dashboard-live.mjs <fresh-report-directory> baseline` hoặc stage `connected-final`. Đọc credential local theo [hướng dẫn](../docs/operations/setup-and-troubleshooting.md#phiên-demo-được-chủ-dự-án-cấp-cho-dashboard-uiux), chỉ dùng đúng origin đã duyệt. Không trace/log credential hay row identifiers; ảnh mask tên tài khoản và bodies bảng. Baseline dùng API legacy hiện có; connected-final yêu cầu API đọc mới, đối chiếu trend/summary với toàn bộ trang DTO, kiểm trùng và tổng. Nếu Web chưa restart để nạp backend mới thì báo BLOCKED, không dùng baseline/mock/H2 để thay cho connected PASS. Không tự kill/restart owner's process hoặc seed/reset DB.

Vị trí được duyệt: `tests/api/`, `tests/e2e/web/`, `tests/e2e/desktop/`, fixtures synthetic ở `tests/fixtures/`; chỉ tạo suite/helper khi có implementation thật.

Trước live run phải xác nhận URL, DB/schema/media test độc lập, tài khoản synthetic/quyền, credential channel và seed/cleanup. Không tự dùng datasource hiện tại, reset DB, mở camera hoặc gọi mutation route. Spring thật + ANPR giả cần ghi nhãn như vậy, không full E2E. Web harness và Windows UI Automation chưa chọn; Playwright không test WinForms. Desktop outbox tests hiện có không thay cho client↔Spring network test; HTTP doubles của feature branch chỉ là lịch sử và cũng không live network proof.
