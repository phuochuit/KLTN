$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$localDotnet = Join-Path $workspace 'tools\dotnet\dotnet.exe'
$dotnet = if (Test-Path $localDotnet) { $localDotnet } else { 'dotnet' }
$env:DOTNET_CLI_HOME = Join-Path $workspace '.dotnet-cli-home'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'
$env:DOTNET_CLI_TELEMETRY_OPTOUT = '1'
$project = Join-Path $workspace 'desktop-winform\ParkingGateDesktop.csproj'
$releaseExe = Join-Path $workspace 'desktop-winform\bin\Release\net7.0-windows\ParkingGateDesktop.exe'

Write-Host 'Đang biên dịch bản WinForms mới nhất...' -ForegroundColor Cyan
& $dotnet build $project --configuration Release
if ($LASTEXITCODE -ne 0) {
    throw "Build WinForms thất bại (exit code $LASTEXITCODE)."
}

Write-Host 'Đang mở ứng dụng trạm gác...' -ForegroundColor Green
Start-Process -FilePath $releaseExe -Wait
