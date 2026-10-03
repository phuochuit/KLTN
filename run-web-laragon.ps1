$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$localMaven = Join-Path $workspace 'tools\apache-maven-3.9.11\bin\mvn.cmd'
$maven = if (Test-Path $localMaven) { $localMaven } else { 'mvn' }

if (Test-Path 'C:\Program Files\Java\jdk-21.0.12') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}

$laragon = 'D:\laragon_new\laragon.exe'
$mysqlClient = 'D:\laragon_new\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe'

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
if (-not $mysql) {
    if (Test-Path $laragon) {
        throw 'MySQL Laragon chua chay. Hay mo Laragon, bam Start All, sau do chay lai.'
    }
    throw 'Khong tim thay MySQL tren cong 3306.'
}

try {
    $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/health' -TimeoutSec 2
    if ($health.status -eq 'UP') {
        if ($health.service -ne 'parking-web') { throw 'Cong 8080 dang do ung dung khac su dung.' }
        $connection = Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1
        Stop-Process -Id $connection.OwningProcess -Force
        Start-Sleep -Seconds 2
    }
} catch {
    if ($_.Exception.Message -like 'Cong 8080*') { throw }
}

$occupied = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($occupied) { throw 'Cong 8080 dang duoc ung dung khac su dung.' }

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

$env:PARKING_DB_HOST = '127.0.0.1'
$env:PARKING_DB_PORT = '3306'
$env:PARKING_DB_NAME = 'parking_anpr'
$env:PARKING_DB_USER = 'root'
$env:SPRING_PROFILES_ACTIVE = 'laragon'

Write-Host 'Database: MySQL Laragon / parking_anpr' -ForegroundColor Cyan
Write-Host 'Parking Web: http://localhost:8080' -ForegroundColor Green
Push-Location (Join-Path $workspace 'spring-web')
try { & $maven clean spring-boot:run } finally {
    $env:PARKING_DB_PASSWORD = $null
    Pop-Location
}
