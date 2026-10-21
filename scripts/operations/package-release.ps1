param([string]$OutputRoot = '')
$ErrorActionPreference = 'Stop'

$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $env:LOCALAPPDATA "ParkingANPR\releases"
}
$release = Join-Path $OutputRoot "parking-anpr-$stamp"
$maven = Join-Path $workspace 'tools\apache-maven-3.9.11\bin\mvn.cmd'
if (-not (Test-Path $maven)) { $maven = 'mvn' }

New-Item -ItemType Directory -Force -Path $release | Out-Null
& $maven -f (Join-Path $workspace 'spring-web\pom.xml') clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw 'Spring package failed.' }
& dotnet publish (Join-Path $workspace 'desktop-winform\ParkingGateDesktop.csproj') `
    --configuration Release --output (Join-Path $release 'desktop')
if ($LASTEXITCODE -ne 0) { throw 'Desktop publish failed.' }

New-Item -ItemType Directory -Force -Path (Join-Path $release 'spring-web') | Out-Null
$jar = Get-ChildItem (Join-Path $workspace 'spring-web\target') -Filter '*.jar' |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $jar) { throw 'Spring executable jar was not found.' }
Copy-Item $jar.FullName (Join-Path $release 'spring-web\parking-web.jar')

$pythonDestination = Join-Path $release 'anpr-service'
New-Item -ItemType Directory -Force -Path $pythonDestination | Out-Null
Copy-Item (Join-Path $workspace 'anpr-service\app') $pythonDestination -Recurse
Copy-Item (Join-Path $workspace 'anpr-service\requirements.txt') $pythonDestination
Get-ChildItem $pythonDestination -Recurse -Directory -Filter '__pycache__' | Remove-Item -Recurse -Force
Get-ChildItem $pythonDestination -Recurse -File -Filter '*.pyc' | Remove-Item -Force

Copy-Item (Join-Path $workspace 'scripts\database') (Join-Path $release 'database-migrations') -Recurse
Copy-Item (Join-Path $workspace 'docs\operations') (Join-Path $release 'operations-docs') -Recurse
Copy-Item (Join-Path $workspace '.env.local.example') $release
Copy-Item (Join-Path $workspace 'README.md') $release

@'
Models AI, JWT private keys, service token, .env.local, uploads and private biometric data
are intentionally excluded. Provision them separately according to operations documentation.
'@ | Set-Content (Join-Path $release 'PROVISION-SEPARATELY.txt') -Encoding UTF8

$checksums = Get-ChildItem $release -Recurse -File | Sort-Object FullName | ForEach-Object {
    $relative = $_.FullName.Substring($release.Length + 1)
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
    "$hash  $relative"
}
$checksums | Set-Content (Join-Path $release 'SHA256SUMS.txt') -Encoding ASCII
Write-Output "release=$release"
