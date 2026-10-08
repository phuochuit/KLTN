$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$mysqlClient = 'mysql-2709ad1f-phuoc190305-fcdc.e.aivencloud.com, Port: 26805, User: avnadmin'

function Test-MySqlLogin {
    param([string]$Password)
    if (-not (Test-Path $mysqlClient)) { return $true }
    $oldMysqlPwd = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        & $mysqlClient --default-character-set=utf8mb4 --protocol=TCP --host=127.0.0.1 --port=3306 --user=root --connect-timeout=5 --silent --skip-column-names --execute='SELECT 1;' 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } finally {
        $env:MYSQL_PWD = $oldMysqlPwd
    }
}

$mysql = Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue
if (-not $mysql) { throw 'MySQL Laragon chua chay. Hay mo Laragon va bam Start All.' }

if (-not $env:PARKING_DB_PASSWORD) {
    $secure = Read-Host 'Nhap mat khau MySQL root cua Laragon' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:PARKING_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if (-not (Test-MySqlLogin -Password $env:PARKING_DB_PASSWORD)) {
    $env:PARKING_DB_PASSWORD = $null
    throw 'Sai mat khau MySQL root, hoac cong 3306 dang la MySQL khac khong phai Laragon. Hay mo HeidiSQL, thu dang nhap root tai 127.0.0.1:3306, roi chay lai va nhap dung mat khau.'
}

$web = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($web) {
    try { $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/health' -TimeoutSec 2 } catch { }
    if ($health.service -ne 'parking-web') { throw 'Cong 8080 dang do ung dung khac su dung.' }
    Stop-Process -Id $web.OwningProcess -Force
    Start-Sleep -Seconds 2
}

Start-Process -FilePath 'powershell.exe' -ArgumentList @(
    '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $PSScriptRoot 'run-web-laragon.ps1')
) -WorkingDirectory $workspace

$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    try {
        $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/health' -TimeoutSec 2
        if ($health.status -eq 'UP' -and $health.database -match 'MySQL') { $ready = $true; break }
    } catch { }
}
if (-not $ready) { throw 'Spring Web khong ket noi duoc MySQL. Xem loi trong cua so Parking Web Laragon.' }

Start-Process -FilePath 'powershell.exe' -ArgumentList @(
    '-NoExit', '-NoProfile', '-ExecutionPolicy', 'Bypass',
    '-File', (Join-Path $PSScriptRoot 'run-anpr.ps1')
) -WorkingDirectory $workspace
Start-Sleep -Seconds 5
& (Join-Path $PSScriptRoot 'run-desktop.ps1')
$env:PARKING_DB_PASSWORD = $null
