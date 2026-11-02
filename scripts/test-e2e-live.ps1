# ==============================================================================
# Script Chạy Kiểm Thử Tự Động Playwright E2E Trực Quan (Observable) - Phase 10
# Tự động mở Google Chrome, hiển thị banner hướng dẫn Tiếng Việt từng bước
# và vòng sáng Neon trước mỗi thao tác click / gõ phím.
# ==============================================================================
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot

# 1. Kiem tra Web Server dang chay o cong 8080
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/parking/health" -TimeoutSec 2
    if ($health.status -ne 'UP') {
        throw "Web Server chua san sang"
    }
} catch {
    Write-Host "[!] Web Server chua chay. Vui long chay .\scripts\run-web-laragon.ps1 truoc!" -ForegroundColor Red
    exit 1
}

# 2. Xac dinh Python co san Playwright trong anpr-service\.venv
$pythonExe = Join-Path $workspace "anpr-service\.venv\Scripts\python.exe"
if (-not (Test-Path $pythonExe)) {
    $pythonExe = "python"
}

# 3. Chay test Playwright truc quan
$testScript = Join-Path $workspace "scripts\run-e2e-playwright-live.py"
Write-Host "Dang khoi chay Playwright E2E Live qua trinh duyet Chrome..." -ForegroundColor Cyan
Write-Host "Toc do: Cham (slowMo: 900ms) - Kem banner dinh dinh huong dan chi tiet tung buoc." -ForegroundColor Yellow

& $pythonExe $testScript
exit $LASTEXITCODE
