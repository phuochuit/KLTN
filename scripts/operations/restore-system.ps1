param(
    [Parameter(Mandatory = $true)]
    [string]$BackupDir,
    [string]$TargetDatabase = '',
    [switch]$VerifyOnly,
    [switch]$Force
)
$ErrorActionPreference = 'Stop'

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  PARKING ANPR - QUY TRÌNH PHỤC HỒI HỆ THỐNG (RESTORE SYSTEM)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

if (-not (Test-Path $BackupDir)) {
    throw "Backup directory does not exist: $BackupDir"
}

# 1. Đọc cấu hình từ .env.local
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
$defaultDb = $uri.AbsolutePath.TrimStart('/').Split('?')[0]
$dbToRestore = if ([string]::IsNullOrWhiteSpace($TargetDatabase)) { $defaultDb } else { $TargetDatabase }

# 2. Xác thực tính toàn vẹn với SHA256SUMS.txt
$checksumFile = Join-Path $BackupDir 'SHA256SUMS.txt'
if (Test-Path $checksumFile) {
    Write-Host "[1/3] Đang kiểm tra tính toàn vẹn tệp tin sao lưu (SHA-256)..." -ForegroundColor Yellow
    $lines = Get-Content $checksumFile
    foreach ($line in $lines) {
        $parts = $line -split '\s+', 2
        if ($parts.Count -lt 2) { continue }
        $expectedHash = $parts[0].Trim()
        $fileName = $parts[1].Trim()
        $filePath = Join-Path $BackupDir $fileName
        if (-not (Test-Path $filePath)) {
            throw "Tệp sao lưu bị thiếu so với mã băm: $fileName"
        }
        $actualHash = (Get-FileHash $filePath -Algorithm SHA256).Hash
        if ($actualHash -ne $expectedHash) {
            throw "CẢNH BÁO TOÀN VẸN: Mã băm SHA-256 không khớp cho $fileName (Mong đợi: $expectedHash, Thực tế: $actualHash)"
        }
        Write-Host "  [OK] $fileName : $actualHash" -ForegroundColor Green
    }
} else {
    Write-Warning "Không tìm thấy file SHA256SUMS.txt trong thư mục backup."
}

$sqlFile = Join-Path $BackupDir 'parking_anpr.sql'
if (-not (Test-Path $sqlFile)) {
    throw "Không tìm thấy tệp cơ sở dữ liệu parking_anpr.sql trong $BackupDir"
}

# 3. Tìm mysql.exe
$mysql = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
if (-not $mysql) {
    $mysql = Get-ChildItem 'D:\2001230931_LucXuanThinh\file_laragon','C:\laragon','D:\laragon_new' -Recurse -Filter mysql.exe `
        -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mysql) { throw 'mysql.exe was not found on system.' }

Write-Host "[2/3] Công cụ MySQL Client: $mysql" -ForegroundColor Gray
Write-Host "      Mục tiêu khôi phục: $($uri.Host):$($uri.Port) -> Cơ sở dữ liệu: $dbToRestore" -ForegroundColor Gray

if ($VerifyOnly) {
    Write-Host "[3/3] Chế độ kiểm tra toàn vẹn (VerifyOnly): Toàn bộ mã băm và tệp tin SQL hợp lệ." -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Cyan
    Write-Host "  KIỂM TRA HOÀN TẤT - BẢN SAO LƯU SẴN SÀNG CHO PHỤC HỒI (RTO < 3 phút)" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Cyan
    return
}

# 4. Thực thi nạp dữ liệu khôi phục
Write-Host "[3/3] Đang tiến hành phục hồi dữ liệu từ $sqlFile vào $dbToRestore..." -ForegroundColor Yellow
$sw = [System.Diagnostics.Stopwatch]::StartNew()

Get-Content $sqlFile -Encoding UTF8 | & $mysql --host=$($uri.Host) --port=$($uri.Port) `
    --user=$($env:PARKING_DB_USERNAME) "--password=$($env:PARKING_DB_PASSWORD)" `
    --ssl-mode=REQUIRED --default-character-set=utf8mb4 $dbToRestore

if ($LASTEXITCODE -ne 0) {
    throw "Quá trình nạp cơ sở dữ liệu thất bại với mã lỗi $LASTEXITCODE."
}
$sw.Stop()

# 5. Phục hồi tệp nhị phân riêng tư nếu có
$privateZip = Join-Path $BackupDir 'private-data.zip'
if (Test-Path $privateZip) {
    $privateDest = if ($env:PARKING_PRIVATE_STORAGE_PATH) { $env:PARKING_PRIVATE_STORAGE_PATH }
        else { Join-Path $workspace 'spring-web\private-data' }
    Write-Host "  Đang giải nén tài sản riêng tư vào $privateDest..." -ForegroundColor Yellow
    Expand-Archive -Path $privateZip -DestinationPath $privateDest -Force
}

Write-Host "=================================================================" -ForegroundColor Green
Write-Host "  [SUCCESS] PHỤC HỒI DỮ LIỆU THÀNH CÔNG!" -ForegroundColor Green
Write-Host "  Thời gian phục hồi thực tế (RTO): $($sw.Elapsed.TotalSeconds.ToString('F2')) giây" -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Green
