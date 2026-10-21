param([string]$OutputRoot = '', [switch]$IncludePrivateData)
$ErrorActionPreference = 'Stop'

$workspace = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$envFile = Join-Path $workspace '.env.local'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $item = $line.Trim()
        if (-not $item -or $item.StartsWith('#')) { continue }
        $separator = $item.IndexOf('=')
        if ($separator -le 0) { continue }
        $name = $item.Substring(0, $separator).Trim()
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            Set-Item -Path "Env:$name" -Value $item.Substring($separator + 1)
        }
    }
}
foreach ($name in 'PARKING_DB_URL','PARKING_DB_USERNAME','PARKING_DB_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace((Get-Item "Env:$name" -ErrorAction SilentlyContinue).Value)) {
        throw "Missing required environment variable: $name"
    }
}

$uri = [Uri]$env:PARKING_DB_URL.Substring(5)
$database = $uri.AbsolutePath.TrimStart('/').Split('?')[0]
$dump = (Get-Command mysqldump.exe -ErrorAction SilentlyContinue).Source
if (-not $dump) {
    $dump = Get-ChildItem 'C:\laragon','D:\laragon_new' -Recurse -Filter mysqldump.exe `
        -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $dump) { throw 'mysqldump.exe was not found.' }
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $env:LOCALAPPDATA 'ParkingANPR\backups'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$directory = Join-Path $OutputRoot $stamp
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$databaseFile = Join-Path $directory 'parking_anpr.sql'

& $dump --no-defaults --host $uri.Host --port $uri.Port --user $env:PARKING_DB_USERNAME `
    "--password=$($env:PARKING_DB_PASSWORD)" --ssl-mode=REQUIRED --default-character-set=utf8mb4 `
    --single-transaction --set-gtid-purged=OFF --routines --triggers $database |
    Set-Content $databaseFile -Encoding UTF8
if ($LASTEXITCODE -ne 0) { throw 'Database backup failed.' }

if ($IncludePrivateData) {
    $privatePath = if ($env:PARKING_PRIVATE_STORAGE_PATH) { $env:PARKING_PRIVATE_STORAGE_PATH }
        else { Join-Path $workspace 'spring-web\private-data' }
    if (Test-Path $privatePath) {
        Compress-Archive -Path (Join-Path $privatePath '*') `
            -DestinationPath (Join-Path $directory 'private-data.zip') -CompressionLevel Optimal
    }
}

Get-ChildItem $directory -File | ForEach-Object {
    "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash, $_.Name
} | Set-Content (Join-Path $directory 'SHA256SUMS.txt') -Encoding ASCII
Write-Output "backup=$directory"
