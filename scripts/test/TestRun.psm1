function New-ParkingTestRun([string]$Workspace, [string]$Suite) {
    $id = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ') + '-' + $Suite + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
    $dir = Join-Path $Workspace ".artifacts/test-runs/$id"
    New-Item -ItemType Directory -Path $dir -ErrorAction Stop | Out-Null
    foreach ($name in 'logs','reports') { New-Item -ItemType Directory (Join-Path $dir $name) | Out-Null }
    return [pscustomobject]@{ Id=$id; Directory=$dir }
}

function Get-ParkingTestCounts([string]$Reports) {
    $counts = @{Total=0; Failed=0; Skipped=0; ReportFailed=$false}
    foreach ($file in Get-ChildItem -LiteralPath $Reports -File -Recurse | Where-Object { $_.Extension -in '.xml','.trx' }) {
        $settings = [System.Xml.XmlReaderSettings]::new()
        $settings.DtdProcessing = [System.Xml.DtdProcessing]::Prohibit
        $reader = [System.Xml.XmlReader]::Create($file.FullName, $settings)
        try {
            $doc = [System.Xml.XmlDocument]::new()
            $doc.XmlResolver = $null
            $doc.Load($reader)
            if ($file.Extension -eq '.trx') {
                $summary = $doc.SelectSingleNode('//*[local-name()="ResultSummary"]')
                if (-not $summary) { throw "Missing TRX summary: $($file.Name)" }
                $c = $summary.SelectSingleNode('*[local-name()="Counters"]')
                if (-not $c) { throw "Missing TRX counters: $($file.Name)" }
                # An aborted/incomplete run can still contain successful test counters.
                if ($summary.GetAttribute('outcome') -notin 'Completed','Passed') { $counts.ReportFailed = $true }
                $counts.Total += [int]$c.total
                foreach ($name in 'failed','error','aborted','timeout','notRunnable','inconclusive','disconnected','passedButRunAborted') {
                    $counts.Failed += [int]$c.GetAttribute($name)
                }
                $counts.Skipped += [int]$c.total - [int]$c.executed
            } else {
                $suites = $doc.SelectNodes('//testsuite[not(testsuite)]')
                if ($suites.Count -eq 0) { throw "Missing JUnit suites: $($file.Name)" }
                foreach ($s in $suites) {
                    $counts.Total += [int]$s.tests
                    $counts.Failed += [int]$s.failures + [int]$s.errors
                    $counts.Skipped += [int]$s.skipped
                }
            }
        } finally { $reader.Dispose() }
    }
    return $counts
}

function Get-ParkingTestStatus([int]$ExitCode, $Counts) {
    if ($ExitCode -ne 0 -or $Counts.Total -le $Counts.Skipped -or $Counts.Failed -gt 0 -or $Counts.ReportFailed) { return 'failed' }
    return 'passed'
}

function Invoke-ParkingTestCommand([string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory, [string]$Log) {
    Push-Location -LiteralPath $WorkingDirectory
    try {
        # Windows PowerShell represents native stderr as ErrorRecord; judge the native exit code, not stderr presence.
        $ErrorActionPreference = 'Continue'
        & $Executable @Arguments 2>&1 | ForEach-Object {
            if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { [string]$_ }
        } | Tee-Object -FilePath $Log | Out-Host
        return $LASTEXITCODE
    } finally { Pop-Location }
}

Export-ModuleMember -Function New-ParkingTestRun,Get-ParkingTestCounts,Get-ParkingTestStatus,Invoke-ParkingTestCommand
