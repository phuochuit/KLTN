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

if (Test-Path 'C:\Program Files\Java\jdk-21.0.12') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}

# Kiem tra va giai phong port 8080
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

Write-Host 'Database: MySQL Aiven Cloud' -ForegroundColor Cyan
Write-Host 'Parking Web: http://localhost:8080' -ForegroundColor Green
Push-Location (Join-Path $workspace 'spring-web')
try { 
    & $maven clean spring-boot:run 
} finally {
    Pop-Location
}