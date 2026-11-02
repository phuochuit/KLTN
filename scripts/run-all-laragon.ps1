$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Write-Host 'Che do du phong: MySQL Laragon local 127.0.0.1:3306/parking_anpr.' -ForegroundColor Yellow

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
