$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$localMaven = Join-Path $workspace 'tools\apache-maven-3.9.11\bin\mvn.cmd'
$maven = if (Test-Path $localMaven) { $localMaven } else { 'mvn' }

# Aiven is the primary database. Existing process environment takes precedence;
# .env.local is only a developer-machine fallback and is excluded from Git.
$localEnvironment = Join-Path $workspace '.env.local'
if (Test-Path $localEnvironment) {
    foreach ($line in Get-Content $localEnvironment) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { throw "Invalid entry in .env.local: $trimmed" }
        $name = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1)
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}
foreach ($required in @('PARKING_DB_URL', 'PARKING_DB_USERNAME', 'PARKING_DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($required, 'Process'))) {
        throw "Missing Aiven setting $required. Configure it in the environment or .env.local."
    }
}
if ($env:PARKING_DB_URL -notmatch '/parking_anpr(?:\?|$)') {
    throw 'PARKING_DB_URL must target the primary Aiven database parking_anpr.'
}

. (Join-Path $workspace 'scripts\security\ensure-internal-service-token.ps1')

if (Test-Path 'C:\Program Files\Java\jdk-21.0.12') {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}

$defaultPrivateKey = Join-Path $workspace 'scripts\security\keys\desktop-jwt-private.pem'
$defaultPublicKey = Join-Path $workspace 'scripts\security\keys\desktop-jwt-public.pem'

# Local launchers enable Desktop JWT by default. Deployment environments may
# override every value without storing key material in this repository.
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_ENABLED)) {
    $env:PARKING_SECURITY_JWT_ENABLED = 'true'
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_ISSUER)) {
    $env:PARKING_SECURITY_JWT_ISSUER = 'https://parking.local'
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_AUDIENCE)) {
    $env:PARKING_SECURITY_JWT_AUDIENCE = 'parking-desktop'
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_KID)) {
    $env:PARKING_SECURITY_JWT_KID = 'key-2026-10'
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION)) {
    $env:PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION = $defaultPrivateKey
}
if ([string]::IsNullOrWhiteSpace($env:PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION)) {
    $env:PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION = $defaultPublicKey
}
if ($env:PARKING_SECURITY_JWT_ENABLED -eq 'true') {
    if (-not (Test-Path $env:PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION)) {
        throw 'Desktop JWT private key was not found. Configure PARKING_SECURITY_JWT_PRIVATE_KEY_LOCATION.'
    }
    if (-not (Test-Path $env:PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION)) {
        throw 'Desktop JWT public key was not found. Configure PARKING_SECURITY_JWT_PUBLIC_KEY_LOCATION.'
    }
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
