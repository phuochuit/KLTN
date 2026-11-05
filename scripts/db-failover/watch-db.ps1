param(
    [int]$IntervalSeconds = 15,
    [int]$FailureThreshold = 2,
    [int]$SuccessThreshold = 2,
    [switch]$Once,
    [switch]$DryRun,
    [switch]$SimulateAivenDown
)

$ErrorActionPreference = 'Continue'

$ScriptDir = $PSScriptRoot
$Root = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$DeployDir = Join-Path $Root 'deploy'
$EnvFile = Join-Path $DeployDir '.env.deploy'
$LogDir = Join-Path $ScriptDir 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$LogFile = Join-Path $LogDir 'failover.log'
$StateFile = Join-Path $ScriptDir '.state.json'
$LockFile = Join-Path $ScriptDir '.watch.lock'

function Write-Log {
    param([string]$Message, [string]$Level = 'INFO')
    $line = '{0} {1} {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Add-Content -LiteralPath $LogFile -Value $line -Encoding UTF8
    Write-Host $line
}

if ($SimulateAivenDown -and -not $DryRun) {
    Write-Log 'SimulateAivenDown chi duoc dung voi -DryRun.' 'ERROR'
    exit 2
}

$lockStream = $null
try {
    $lockStream = [System.IO.File]::Open(
        $LockFile,
        [System.IO.FileMode]::OpenOrCreate,
        [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None
    )
} catch {
    Write-Host 'DB failover watchdog is already running.'
    exit 0
}

$envMap = @{}
if (Test-Path -LiteralPath $EnvFile) {
    Get-Content -LiteralPath $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
            $key, $value = $line -split '=', 2
            $value = $value.Trim()
            if ($value.Length -ge 2 -and (($value[0] -eq '"' -and $value[-1] -eq '"') -or ($value[0] -eq "'" -and $value[-1] -eq "'"))) {
                $value = $value.Substring(1, $value.Length - 2)
            }
            $envMap[$key.Trim()] = $value
        }
    }
}

function Get-Setting {
    param([string]$Name, [string]$Default = $null)
    if ($envMap.ContainsKey($Name) -and $envMap[$Name] -ne '') {
        return $envMap[$Name]
    }
    return $Default
}

$aivenHost = Get-Setting 'PARKING_AIVEN_HOST'
$aivenPort = Get-Setting 'PARKING_AIVEN_PORT'
$aivenDb = Get-Setting 'PARKING_AIVEN_DB'
$aivenUser = Get-Setting 'PARKING_AIVEN_USERNAME'
$aivenPass = Get-Setting 'PARKING_AIVEN_PASSWORD'
if (-not $aivenHost) {
    $aivenUrl = Get-Setting 'PARKING_AIVEN_URL'
    if ($aivenUrl -match 'jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') {
        $aivenHost = $Matches[1]
        if (-not $aivenPort -and $Matches[2]) { $aivenPort = $Matches[2] }
        if (-not $aivenDb) { $aivenDb = $Matches[3] }
    }
}
if (-not $aivenPort) { $aivenPort = '3306' }
if (-not $aivenDb) { $aivenDb = 'parking_anpr' }

$localHost = '127.0.0.1'
$localPort = Get-Setting 'PARKING_LARAGON_PORT' '3306'
$localDb = Get-Setting 'PARKING_LARAGON_DB' 'parking_anpr'
$localUser = Get-Setting 'PARKING_LARAGON_USERNAME' 'root'
$localPass = Get-Setting 'PARKING_LARAGON_PASSWORD' ''

$mode = Get-Setting 'PARKING_DB_MODE' 'auto'
if ($mode -ne 'auto') {
    Write-Log "PARKING_DB_MODE=$mode. Watchdog chi hoat dong khi mode=auto." 'ERROR'
    exit 1
}
if (-not $aivenHost -or -not $aivenUser -or -not $aivenDb) {
    Write-Log 'Thieu cau hinh Aiven trong deploy\.env.deploy.' 'ERROR'
    exit 1
}

$mysql = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    $candidate = Get-ChildItem -Path 'D:\laragon*\bin\mysql\*\bin\mysql.exe' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($candidate) { $mysql = $candidate.FullName }
}
if (-not $mysql -or -not (Test-Path -LiteralPath $mysql)) {
    Write-Log 'Khong tim thay mysql.exe de kiem tra DB.' 'ERROR'
    exit 1
}

$emptyIni = Join-Path $env:TEMP 'autoparking-empty-my.ini'
Set-Content -LiteralPath $emptyIni -Value "[client]`n" -Encoding ASCII

function Test-Database {
    param(
        [string]$DbHost,
        [int]$DbPort,
        [string]$Database,
        [string]$User,
        [string]$Password,
        [bool]$Tls
    )
    $oldPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $mysqlArgs = @("--defaults-file=$emptyIni")
        if ($Tls) { $mysqlArgs += '--ssl-mode=REQUIRED' }
        $mysqlArgs += @(
            "--host=$DbHost",
            "--port=$DbPort",
            "--user=$User",
            '--protocol=TCP',
            '--connect-timeout=5',
            '--batch',
            '--raw',
            '-e',
            'SELECT 1',
            $Database
        )
        & $mysql @mysqlArgs *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    } finally {
        if ($null -eq $oldPwd) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $oldPwd
        }
    }
}

function Get-ActiveFromLog {
    if (-not (Test-Path -LiteralPath $DeployDir)) { return $null }
    Push-Location $DeployDir
    try {
        $lines = & docker compose logs --tail=250 web 2>$null
        $match = $lines | Select-String -Pattern '\[db\] dang dung (Aiven|Laragon)' | Select-Object -Last 1
        if ($match) {
            if ($match.Line -match 'Aiven') { return 'aiven' }
            return 'local'
        }
    } catch {
    } finally {
        Pop-Location
    }
    return $null
}

function Test-WebHealthy {
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8080/api/parking/health' -TimeoutSec 3
        return ($health.status -eq 'UP')
    } catch {
        return $false
    }
}

function Restart-Web {
    param([string]$Target)
    if ($DryRun) {
        Write-Log "DRY-RUN: se restart web de dung $Target."
        return $true
    }
    Push-Location $DeployDir
    try {
        $output = & docker compose up -d --force-recreate --no-deps web 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Log ('Recreate web that bai: ' + ($output -join ' ')) 'ERROR'
            return $false
        }
    } catch {
        Write-Log ('Recreate web loi: ' + $_.Exception.Message) 'ERROR'
        return $false
    } finally {
        Pop-Location
    }

    Start-Sleep -Seconds 2
    $healthy = $false
    for ($i = 0; $i -lt 60; $i++) {
        if (Test-WebHealthy) {
            $healthy = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $healthy) {
        Write-Log 'Web khong healthy sau khi restart.' 'ERROR'
        return $false
    }

    $actual = Get-ActiveFromLog
    if ($actual -and $actual -ne $Target) {
        Write-Log "Web healthy nhung log bao '$actual' (mong doi '$Target')." 'ERROR'
        return $false
    }
    Write-Log "Web da chay lai va healthy voi $Target."
    return $true
}

function Invoke-SyncOnce {
    if ($DryRun) {
        Write-Log 'DRY-RUN: se chay sync --once truoc khi quay lai Aiven.'
        return $true
    }
    $python = Join-Path $Root 'scripts\sync\.venv\Scripts\python.exe'
    $syncScript = Join-Path $Root 'scripts\sync\sync_two_way.py'
    if (-not (Test-Path -LiteralPath $python) -or -not (Test-Path -LiteralPath $syncScript)) {
        Write-Log 'Khong tim thay Python venv hoac sync script.' 'ERROR'
        return $false
    }
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        & $python $syncScript --once 2>&1 | Out-Null
        $code = $LASTEXITCODE
        if ($code -eq 0) { return $true }
        if ($code -eq 3) {
            Write-Log 'Sync dang bi khoa, thu lai sau 5 giay.' 'WARN'
            Start-Sleep -Seconds 5
            continue
        }
        Write-Log "Sync --once that bai (exit $code)." 'ERROR'
        return $false
    }
    return $false
}

$state = [ordered]@{
    active          = 'unknown'
    aivenFailures   = 0
    aivenSuccesses  = 0
    updated         = (Get-Date).ToString('s')
}
if (Test-Path -LiteralPath $StateFile) {
    try {
        $loaded = Get-Content -LiteralPath $StateFile -Raw | ConvertFrom-Json
        if ($loaded.active) { $state.active = [string]$loaded.active }
        $state.aivenFailures = [int]$loaded.aivenFailures
        $state.aivenSuccesses = [int]$loaded.aivenSuccesses
    } catch {
    }
}

$fromLog = Get-ActiveFromLog
if ($fromLog) { $state.active = $fromLog }

function Save-State {
    if ($DryRun) { return }
    $state.updated = (Get-Date).ToString('s')
    $state | ConvertTo-Json -Compress | Set-Content -LiteralPath $StateFile -Encoding UTF8
}

Write-Log "Watchdog bat dau: Aiven=$aivenHost/$aivenDb, Local=$localHost/$localDb, active=$($state.active), interval=${IntervalSeconds}s."

$nextFailbackAttempt = Get-Date
try {
    while ($true) {
        $aivenOk = $false
        if (-not $SimulateAivenDown) {
            $aivenOk = Test-Database -DbHost $aivenHost -DbPort ([int]$aivenPort) -Database $aivenDb -User $aivenUser -Password $aivenPass -Tls $true
        }
        $localOk = Test-Database -DbHost $localHost -DbPort ([int]$localPort) -Database $localDb -User $localUser -Password $localPass -Tls $false

        if ($aivenOk) {
            $state.aivenSuccesses = [int]$state.aivenSuccesses + 1
            $state.aivenFailures = 0
        } else {
            $state.aivenFailures = [int]$state.aivenFailures + 1
            $state.aivenSuccesses = 0
        }

        if ($state.active -eq 'unknown') {
            if ($aivenOk) {
                $state.active = 'aiven'
            } elseif ($localOk) {
                $state.active = 'local'
            }
        }

        if (($state.active -eq 'aiven' -or $state.active -eq 'unknown') -and -not $aivenOk -and $localOk -and $state.aivenFailures -ge $FailureThreshold) {
            Write-Log "Aiven khong san sang ($($state.aivenFailures) lan). Chuyen web sang Laragon."
            if (Restart-Web -Target 'local') {
                $state.active = 'local'
                $state.aivenFailures = 0
                $state.aivenSuccesses = 0
                Save-State
            } else {
                Write-Log 'Chua chuyen duoc sang Laragon; se thu lai.' 'ERROR'
            }
        } elseif ($state.active -eq 'local' -and $aivenOk -and $state.aivenSuccesses -ge $SuccessThreshold -and (Get-Date) -ge $nextFailbackAttempt) {
            Write-Log "Aiven da hoat dong lai ($($state.aivenSuccesses) lan). Dong bo local -> Aiven truoc khi quay lai."
            if (Invoke-SyncOnce) {
                if (Restart-Web -Target 'aiven') {
                    $state.active = 'aiven'
                    $state.aivenFailures = 0
                    $state.aivenSuccesses = 0
                    Save-State
                } else {
                    Write-Log 'Khong quay lai Aiven duoc; tiep tuc dung Laragon.' 'ERROR'
                    $nextFailbackAttempt = (Get-Date).AddMinutes(1)
                }
            } else {
                Write-Log 'Chua dong bo duoc; tiep tuc dung Laragon.' 'WARN'
                $nextFailbackAttempt = (Get-Date).AddMinutes(1)
            }
        }

        Save-State
        if ($Once) { break }
        Start-Sleep -Seconds $IntervalSeconds
    }
} finally {
    if ($lockStream) { $lockStream.Dispose() }
}
