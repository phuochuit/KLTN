[CmdletBinding()]
param([ValidateSet('spring','desktop','anpr','tooling','thesis','all')][string]$Suite = 'all')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Import-Module (Join-Path $PSScriptRoot 'TestRun.psm1') -Force
. (Join-Path $workspace 'scripts/tool-paths.ps1')

function Invoke-Suite([string]$Name) {
    $run = New-ParkingTestRun $workspace $Name
    $reports = Join-Path $run.Directory 'reports'
    $logs = Join-Path $run.Directory 'logs'
    $temp = Join-Path $workspace ".cache/test-temp/$($run.Id)"
    New-Item -ItemType Directory -Force $temp | Out-Null
    $boundaries = @{
        spring='H2 in-memory + mock/fake ANPR, synthetic media/JWT; not live E2E'
        desktop='xUnit outbox component tests; no WinForms interaction or live Spring'
        anpr='FastAPI TestClient + fake recognizer; no camera/model evaluation'
        tooling='Local runner/tool policy checks; no application E2E'
        thesis='Document tool CLI/path/import and synthetic DOCX; no real proposal generation/render'
    }
    $summary = [ordered]@{
        schema_version=1; run_id=$run.Id; suite=$Name; started_at=[DateTime]::UtcNow.ToString('o')
        finished_at=$null; status='interrupted'; exit_code=130; counts=$null
        boundary=$boundaries[$Name]; commands=@(); report_directory=$reports
        note='Provisional until finished_at is set. Forced process termination may leave this marker.'
    }
    $summaryPath = Join-Path $run.Directory 'summary.json'
    $summary | ConvertTo-Json -Depth 6 | Set-Content $summaryPath -Encoding UTF8
    $saved = @{}
    $variables = 'TEMP','TMP','DOTNET_CLI_HOME','DOTNET_CLI_TELEMETRY_OPTOUT','DOTNET_SKIP_FIRST_TIME_EXPERIENCE','DOTNET_GENERATE_ASPNET_CERTIFICATE','PYTHONDONTWRITEBYTECODE'
    foreach ($key in $variables) { $saved[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
    $lock = $null
    $executing = $false
    $code = 2
    try {
        $lockDir = Join-Path $workspace '.cache/locks'
        New-Item -ItemType Directory -Force $lockDir | Out-Null
        # OS file handle prevents competing runners from sharing native build output. A crash releases it.
        $lock = [System.IO.File]::Open((Join-Path $lockDir "$Name.lock"), 'OpenOrCreate', 'ReadWrite', 'None')
        $env:TEMP = $temp
        $env:TMP = $temp
        $env:DOTNET_CLI_HOME = Join-Path $workspace '.cache/dotnet-cli'
        $env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
        $env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
        $env:DOTNET_GENERATE_ASPNET_CERTIFICATE = 'false'
        $env:PYTHONDONTWRITEBYTECODE = '1'
        $cwd = $workspace
        switch ($Name) {
            spring {
                $exe = Resolve-ParkingMaven $workspace
                if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) { throw 'Set JAVA_HOME to a JDK 21 installation before running Spring tests.' }
                $cwd = Join-Path $workspace 'spring-web'
                $args = @('test', "-Dparking.test.reports-directory=$reports", "-Djava.io.tmpdir=$temp", '-DfailIfNoTests=true')
                $versionArgs = @('--version')
            }
            desktop {
                $exe = (Get-Command dotnet.exe -ErrorAction Stop).Source
                if ($exe.StartsWith((Join-Path $workspace 'tools/dotnet'), [StringComparison]::OrdinalIgnoreCase) -or $exe.StartsWith((Join-Path $workspace '.cache/toolchains'), [StringComparison]::OrdinalIgnoreCase)) { throw 'Desktop requires a global .NET SDK supporting .NET 8.' }
                $args = @('test','desktop-winform.Tests/ParkingGateDesktop.Tests.csproj','--configuration','Release','--logger','trx;LogFileName=desktop.trx','--results-directory',$reports)
                $versionArgs = @('--info')
            }
            anpr {
                $exe = Join-Path $workspace 'anpr-service/.venv/Scripts/python.exe'
                if (-not (Test-Path $exe)) { throw 'ANPR venv missing. Prepare it and requirements-test.txt explicitly; the runner does not install packages.' }
                $cwd = Join-Path $workspace 'anpr-service'
                $args = @('-m','pytest','tests/', "--junitxml=$reports/anpr.xml", "--basetemp=$temp/pytest", '-o',"cache_dir=$workspace/.cache/pytest/anpr")
                $versionArgs = @('-c','import platform,pytest,httpx; print(platform.python_version(),pytest.__version__,httpx.__version__)')
            }
            tooling {
                $exe = (Get-Command powershell.exe -ErrorAction Stop).Source
                $args = @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $workspace 'tests/tooling/runner.tests.ps1'),'-ReportPath',"$reports/tooling.xml")
                $versionArgs = @('-NoProfile','-Command','$PSVersionTable.PSVersion.ToString()')
            }
            thesis {
                $exe = Join-Path $workspace 'tools/thesis/.venv/Scripts/python.exe'
                if (-not (Test-Path $exe)) { throw 'Prepare tools/thesis/.venv and its requirements.txt explicitly; no global or ANPR environment changes.' }
                $args = @('-B',(Join-Path $workspace 'tests/tooling/test_thesis_cli.py'),'--junitxml',"$reports/thesis.xml")
                $versionArgs = @('-c','import platform,docx,lxml; print(platform.python_version(),docx.__version__,lxml.__version__)')
            }
        }
        $summary.commands += @{executable=$exe; arguments=$versionArgs; working_directory=$cwd; log='logs/version.log'}
        $versionCode = Invoke-ParkingTestCommand $exe $versionArgs $cwd (Join-Path $logs 'version.log')
        if ($versionCode -ne 0) { throw 'Tool version probe failed; inspect logs/version.log.' }
        if ($Name -eq 'spring' -and (Get-Content (Join-Path $logs 'version.log') -Raw) -notmatch 'Java version: 21[\.,]') { throw 'Spring tests require JDK 21. Check JAVA_HOME and Maven version output.' }
        $summary.commands += @{executable=$exe; arguments=$args; working_directory=$cwd; log='logs/test.log'}
        $executing = $true
        $code = Invoke-ParkingTestCommand $exe $args $cwd (Join-Path $logs 'test.log')
        $summary.counts = Get-ParkingTestCounts $reports
        $summary.status = Get-ParkingTestStatus $code $summary.counts
        if ($summary.status -ne 'passed' -and $code -eq 0) { $code = 1 }
        $summary.note = 'Native exit and fresh reports both required. See boundary; this is not application readiness.'
    } catch {
        $summary.status = if ($executing) { 'failed' } else { 'blocked' }
        $summary.note = if ($executing) { 'Execution/report processing failed; inspect logs/error.log.' } else { 'Prerequisite or suite lock unavailable; inspect logs/error.log.' }
        $_.Exception.Message | Set-Content (Join-Path $logs 'error.log') -Encoding UTF8
        Write-Warning $_.Exception.Message
        $code = if ($executing) { 1 } else { 2 }
    } finally {
        foreach ($key in $variables) { [Environment]::SetEnvironmentVariable($key,$saved[$key],'Process') }
        if ($lock) { $lock.Dispose() }
        $summary.exit_code = $code
        $summary.finished_at = [DateTime]::UtcNow.ToString('o')
        $summary | ConvertTo-Json -Depth 6 | Set-Content $summaryPath -Encoding UTF8
        Write-Host "$Name : $($summary.status) -> $summaryPath"
    }
    return $code
}

$exitCode = 0
$suites = if ($Suite -eq 'all') { @('spring','desktop','anpr') } else { @($Suite) }
foreach ($name in $suites) {
    $result = Invoke-Suite $name
    if ($result -ne 0) { $exitCode = [Math]::Max($exitCode, $result) }
}
exit $exitCode
