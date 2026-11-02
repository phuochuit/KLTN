$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot

if (Test-Path "$env:LOCALAPPDATA\dotnet8\dotnet.exe") {
    $env:DOTNET_ROOT = "$env:LOCALAPPDATA\dotnet8"
    $env:Path = "$env:DOTNET_ROOT;" + $env:Path
}

$localDotnet = Join-Path $workspace 'tools\dotnet\dotnet.exe'
$globalDotnet = Get-Command 'dotnet.exe' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source
$dotnet = $globalDotnet
if (-not $dotnet -or $dotnet -eq $localDotnet) {
    throw 'Khong tim thay .NET 8 SDK toan cuc. Hay cai Microsoft.DotNet.SDK.8 hoac chay scripts\run-desktop.ps1 tu mot terminal da cai .NET 8.'
}
$env:DOTNET_CLI_HOME = Join-Path $workspace '.cache\dotnet-cli'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$project = Join-Path $workspace 'desktop-winform\ParkingGateDesktop.csproj'
$releaseExe = Join-Path $workspace 'desktop-winform\bin\Release\net8.0-windows\ParkingGateDesktop.exe'
$releaseDll = Join-Path $workspace 'desktop-winform\bin\Release\net8.0-windows\ParkingGateDesktop.dll'

$existing = Get-Process -Name 'ParkingGateDesktop' -ErrorAction SilentlyContinue
if ($existing) {
    Stop-Process -InputObject $existing -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 500
}

Write-Host 'Dang bien dich ban WinForms moi nhat...' -ForegroundColor Cyan
& $dotnet build $project --configuration Release
if ($LASTEXITCODE -ne 0) {
    throw "Build WinForms that bai (exit code $LASTEXITCODE)."
}

Write-Host 'Dang mo ung dung tram gac...' -ForegroundColor Green
if (Test-Path $releaseDll) {
    Start-Process -FilePath $dotnet -ArgumentList "`"$releaseDll`"" -Wait
} else {
    Start-Process -FilePath $releaseExe -Wait
}
