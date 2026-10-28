param([string]$ReportPath)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Import-Module (Join-Path $root 'scripts/test/TestRun.psm1') -Force
. (Join-Path $root 'scripts/tool-paths.ps1')
. (Join-Path $root 'scripts/check-workspace.ps1')
$work = Join-Path $root ('.cache/test-temp/runner-selftest-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $work | Out-Null
$cases = [System.Collections.Generic.List[string]]::new()
function Check($Name, [scriptblock]$Test) {
    & $Test
    $cases.Add($Name)
    Write-Host "PASS $Name"
}
function Assert($Condition) { if (-not $Condition) { throw 'Assertion failed' } }
Check 'Run IDs are unique and retain previous files' {
    $a = New-ParkingTestRun $work 'fixture'
    Set-Content (Join-Path $a.Directory 'retained.txt') 'keep'
    $b = New-ParkingTestRun $work 'fixture'
    Assert ($a.Id -ne $b.Id)
    Assert (Test-Path (Join-Path $a.Directory 'retained.txt'))
}
Check 'JUnit counts include failures errors and skipped' {
    $reports = New-Item -ItemType Directory (Join-Path $work 'junit')
    '<testsuites><testsuite tests="5" failures="1" errors="1" skipped="2" /></testsuites>' | Set-Content "$reports/junit.xml"
    $c = Get-ParkingTestCounts $reports
    Assert ($c.Total -eq 5 -and $c.Failed -eq 2 -and $c.Skipped -eq 2)
}
Check 'TRX default namespace is parsed' {
    $reports = New-Item -ItemType Directory (Join-Path $work 'trx')
    '<TestRun xmlns="http://microsoft.com/schemas/VisualStudio/TeamTest/2010"><ResultSummary outcome="Completed"><Counters total="12" executed="11" passed="11" failed="0" /></ResultSummary></TestRun>' | Set-Content "$reports/desktop.trx"
    $c = Get-ParkingTestCounts $reports
    Assert ($c.Total -eq 12 -and $c.Skipped -eq 1 -and $c.Failed -eq 0)
    Assert ((Get-ParkingTestStatus 0 $c) -eq 'passed')
}
foreach ($counter in 'timeout','notRunnable','inconclusive','disconnected','passedButRunAborted') {
    Check "TRX $counter cannot pass with exit zero" {
        $reports = New-Item -ItemType Directory (Join-Path $work "trx-$counter")
        "<TestRun><ResultSummary outcome=`"Completed`"><Counters total=`"2`" executed=`"2`" passed=`"1`" $counter=`"1`" /></ResultSummary></TestRun>" | Set-Content "$reports/desktop.trx"
        $c = Get-ParkingTestCounts $reports
        Assert ((Get-ParkingTestStatus 0 $c) -eq 'failed')
    }
}
foreach ($outcome in 'Failed','Aborted','Error','InProgress') {
    Check "TRX summary $outcome cannot pass despite successful counters" {
        $reports = New-Item -ItemType Directory (Join-Path $work "trx-summary-$outcome")
        "<TestRun><ResultSummary outcome=`"$outcome`"><Counters total=`"1`" executed=`"1`" passed=`"1`" /></ResultSummary></TestRun>" | Set-Content "$reports/desktop.trx"
        $c = Get-ParkingTestCounts $reports
        Assert ((Get-ParkingTestStatus 0 $c) -eq 'failed')
    }
}
Check 'TRX Passed summary is accepted' {
    $reports = New-Item -ItemType Directory (Join-Path $work 'trx-passed-summary')
    '<TestRun><ResultSummary outcome="Passed"><Counters total="1" executed="1" passed="1" /></ResultSummary></TestRun>' | Set-Content "$reports/desktop.trx"
    Assert ((Get-ParkingTestStatus 0 (Get-ParkingTestCounts $reports)) -eq 'passed')
}
Check 'TRX missing summary outcome cannot pass' {
    $reports = New-Item -ItemType Directory (Join-Path $work 'trx-missing-outcome')
    '<TestRun><ResultSummary><Counters total="1" executed="1" passed="1" /></ResultSummary></TestRun>' | Set-Content "$reports/desktop.trx"
    Assert ((Get-ParkingTestStatus 0 (Get-ParkingTestCounts $reports)) -eq 'failed')
}
Check 'Zero tests cannot pass' { Assert ((Get-ParkingTestStatus 0 @{Total=0;Failed=0;Skipped=0}) -eq 'failed') }
Check 'All skipped cannot pass' { Assert ((Get-ParkingTestStatus 0 @{Total=2;Failed=0;Skipped=2}) -eq 'failed') }
Check 'Nonzero process exit cannot pass' { Assert ((Get-ParkingTestStatus 7 @{Total=2;Failed=0;Skipped=0}) -eq 'failed') }
Check 'Failed report cannot pass even with exit zero' { Assert ((Get-ParkingTestStatus 0 @{Total=2;Failed=1;Skipped=0}) -eq 'failed') }
Check 'Executed successful tests pass' { Assert ((Get-ParkingTestStatus 0 @{Total=2;Failed=0;Skipped=0}) -eq 'passed') }
Check 'Malformed report is an error' {
    $reports = New-Item -ItemType Directory (Join-Path $work 'malformed')
    '<bad' | Set-Content "$reports/bad.xml"
    $threw = $false
    try { Get-ParkingTestCounts $reports | Out-Null } catch { $threw = $true }
    Assert $threw
}
Check 'Native failure is retained and cwd restored including spaces' {
    $cwd = (Get-Location).Path
    $dir = New-Item -ItemType Directory (Join-Path $work 'path with spaces')
    $code = Invoke-ParkingTestCommand $env:ComSpec @('/d','/c','echo synthetic-output & exit /b 7') $dir (Join-Path $work 'native.log')
    Assert ($code -eq 7)
    Assert ((Get-Content (Join-Path $work 'native.log') -Raw) -match 'synthetic-output')
    Assert ((Get-Location).Path -eq $cwd)
}
Check 'Failure to retain the command log cannot silently pass' {
    $invalidLog = New-Item -ItemType Directory (Join-Path $work 'not-a-log-file')
    $threw = $false
    try {
        Invoke-ParkingTestCommand $env:ComSpec @('/d','/c','echo synthetic-output & exit /b 0') $work $invalidLog | Out-Null
    } catch { $threw = $true }
    Assert $threw
}
Check 'Maven bundled path is preferred' {
    $bin = Join-Path $work 'tools/apache-maven-3.9.11/bin'
    New-Item -ItemType Directory -Force $bin | Out-Null
    Set-Content (Join-Path $bin 'mvn.cmd') '@exit /b 0'
    Assert ((Resolve-ParkingMaven $work) -eq (Join-Path $bin 'mvn.cmd'))
}
Check 'Missing Maven produces actionable failure with empty PATH' {
    $saved = $env:PATH
    try {
        $env:PATH = ''
        $threw = $false
        try { Resolve-ParkingMaven (Join-Path $work 'missing') | Out-Null } catch { $threw = $_.Exception.Message -match 'Maven' }
        Assert $threw
    } finally { $env:PATH = $saved }
}
Check 'Maven falls back to PATH when bundled tool is absent' {
    $saved = $env:PATH
    try {
        $bin = Join-Path $work 'tools/apache-maven-3.9.11/bin'
        $env:PATH = $bin
        Assert ((Resolve-ParkingMaven (Join-Path $work 'missing')) -eq (Join-Path $bin 'mvn.cmd'))
    } finally { $env:PATH = $saved }
}
Check 'Native stderr does not turn exit zero into failure' {
    $code = Invoke-ParkingTestCommand $env:ComSpec @('/d','/c','echo synthetic-warning 1>&2 & exit /b 0') $work (Join-Path $work 'stderr.log')
    Assert ($code -eq 0)
}
Check 'Blank native stderr is not rendered as a RemoteException' {
    $log = Join-Path $work 'blank-stderr.log'
    $code = Invoke-ParkingTestCommand (Get-Command powershell.exe).Source @('-NoProfile','-Command','[Console]::Error.WriteLine(); exit 0') $work $log
    Assert ($code -eq 0)
    Assert ((Get-Content $log -Raw) -notmatch 'System.Management.Automation.RemoteException')
}
Check 'Workspace guard detects old scratch without deleting it' {
    $fixture = New-Item -ItemType Directory (Join-Path $work 'policy-fixture')
    $legacy = New-Item -ItemType Directory (Join-Path $fixture 'tmp')
    $findings = @(Get-ParkingWorkspaceFindings $fixture)
    Assert ($findings.Count -eq 1 -and $findings[0] -match 'tmp')
    Assert (Test-Path $legacy)
}
Check 'Workspace guard detects legacy Spring test output and root logs' {
    $fixture = New-Item -ItemType Directory (Join-Path $work 'spring-legacy-fixture')
    $target = Join-Path $fixture 'spring-web/target'
    foreach ($name in 'test-gate-evidence','test-revenue-gate-evidence','test-uploads','test-revenue-uploads','test-tmp','test-tmp-old') {
        New-Item -ItemType Directory -Force (Join-Path $target $name) | Out-Null
    }
    Set-Content (Join-Path $target 'old-test.log') 'synthetic'
    Assert (@(Get-ParkingWorkspaceFindings $fixture).Count -eq 7)
    Assert (Test-Path (Join-Path $target 'old-test.log'))
}
Check 'Native build directories are allowed by workspace guard' {
    $fixture = New-Item -ItemType Directory (Join-Path $work 'native-fixture')
    foreach ($path in 'spring-web/target/classes','spring-web/target/test-classes','spring-web/target/surefire-reports','spring-web/target/test-work','desktop-winform.tests/bin','desktop-winform.tests/obj') {
        New-Item -ItemType Directory -Force (Join-Path $fixture $path) | Out-Null
    }
    Assert (@(Get-ParkingWorkspaceFindings $fixture).Count -eq 0)
}
Check 'Laragon launcher targets local MySQL and reuses the secured Web launcher' {
    $allLaragon = Get-Content (Join-Path $root 'scripts\run-all-laragon.ps1') -Raw
    $webLaragon = Get-Content (Join-Path $root 'scripts\run-web-laragon.ps1') -Raw
    $web = Get-Content (Join-Path $root 'scripts\run-web.ps1') -Raw
    Assert ($allLaragon -match "run-web-laragon\.ps1")
    Assert ($webLaragon -match "PARKING_DB_MODE\s*=\s*'laragon'")
    Assert ($webLaragon -match '127\.0\.0\.1:3306/parking_anpr')
    Assert ($webLaragon -match 'PARKING_LARAGON_PASSWORD')
    Assert ($webLaragon -match 'Read-Host.*-AsSecureString')
    Assert ($webLaragon -match 'PARKING_LARAGON_ALLOW_EMPTY_PASSWORD')
    Assert ($webLaragon -match "run-web\.ps1")
    Assert ($web.Contains('if (-not $isLaragon -and (Test-Path $localEnvironment))'))
    Assert ($web -match 'Laragon mode only accepts MySQL')
}
if ($ReportPath) {
    $items = ($cases | ForEach-Object { '<testcase name="' + [System.Security.SecurityElement]::Escape($_) + '" />' }) -join ''
    "<testsuite tests=`"$($cases.Count)`" failures=`"0`" errors=`"0`" skipped=`"0`">$items</testsuite>" | Set-Content -LiteralPath $ReportPath -Encoding UTF8
}
Write-Host "$($cases.Count) runner checks passed. Fixtures retained at $work"
