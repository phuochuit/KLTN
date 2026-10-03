$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$localMaven = Join-Path $workspace 'tools\apache-maven-3.9.11\bin\mvn.cmd'
$maven = if (Test-Path $localMaven) { $localMaven } else { 'mvn' }

if (Test-Path 'C:\Program Files\Java\jdk-21.0.12') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}


try {
    $health = Invoke-RestMethod -Uri 'http://localhost:8080/api/parking/health' -TimeoutSec 2
    if ($health.status -eq 'UP' -and $health.service -eq 'parking-web' -and $health.version -eq '4.0-face-slots') {
        Write-Host 'Parking Web is already running at http://localhost:8080' -ForegroundColor Green
        exit 0
    }
    if ($health.status -eq 'UP' -and $health.service -eq 'parking-web') {
        throw 'Parking Web cu dang chay. Hay dong cua so Parking Web cu, sau do chay lai run-all.cmd.'
    }
} catch {
    if ($_.Exception.Message -like 'Parking Web cu*') { throw }
    # Parking Web is not running yet.
}

$occupied = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($occupied) {
    throw 'Port 8080 is being used by another application.'
}

Push-Location (Join-Path $workspace 'spring-web')
try { & $maven clean spring-boot:run } finally { Pop-Location }
