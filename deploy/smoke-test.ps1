#requires -Version 5.1
<#
.SYNOPSIS
    Kiem tra nhanh Spring Web sau khi deploy.

.DESCRIPTION
    Kiem tra:
      1. /api/parking/health tra 200
      2. /login mo duoc va co form dang nhap
      3. Dang nhap admin thanh cong (neu truyen -Password)

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1 -Password "Admin@123456"

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\smoke-test.ps1 -BaseUrl "https://autoparking.tail57e32c.ts.net" -Password "..."

.NOTES
    Mat khau co the truyen qua bien moi truong PARKING_ADMIN_PASSWORD
    de khong hien trong lich su lenh.
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Username = 'admin',
    [string]$Password = '',
    [int]$WaitSeconds = 60,
    [switch]$SkipLogin
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$BaseUrl = $BaseUrl.TrimEnd('/')
$script:failures = 0

function Pass([string]$message) {
    Write-Host "[PASS] $message" -ForegroundColor Green
}

function Fail([string]$message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
    $script:failures++
}

function Info([string]$message) {
    Write-Host "[INFO] $message" -ForegroundColor Cyan
}

function Get-FinalUrl($response) {
    try {
        if ($response.BaseResponse.ResponseUri) {
            return $response.BaseResponse.ResponseUri.AbsoluteUri
        }
    } catch { }
    try {
        if ($response.BaseResponse.RequestMessage.RequestUri) {
            return $response.BaseResponse.RequestMessage.RequestUri.AbsoluteUri
        }
    } catch { }
    return ''
}

# ------------------------------------------------------------
# 1. Health (co retry vi container co the dang khoi dong)
# ------------------------------------------------------------
$health = $null
$attempts = [Math]::Max(1, [int]($WaitSeconds / 2))
for ($i = 1; $i -le $attempts; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$BaseUrl/api/parking/health" -Method Get -TimeoutSec 10
        break
    } catch {
        if ($i -eq $attempts) {
            Fail "Health khong phan hoi sau $WaitSeconds giay: $($_.Exception.Message)"
        } else {
            Start-Sleep -Seconds 2
        }
    }
}

if ($health) {
    $json = try { $health | ConvertTo-Json -Compress -Depth 4 } catch { "$health" }
    Pass "Health 200: $json"
}

# ------------------------------------------------------------
# 2. Trang dang nhap
# ------------------------------------------------------------
$loginPage = $null
$webSession = $null
try {
    $loginPage = Invoke-WebRequest -Uri "$BaseUrl/login" -Method Get `
        -UseBasicParsing -TimeoutSec 15 -SessionVariable webSession
    if ($loginPage.StatusCode -eq 200 -and $loginPage.Content -match 'name="username"') {
        Pass 'Trang /login tra 200 va co form dang nhap'
    } else {
        Fail "Trang /login khong dung (status $($loginPage.StatusCode))"
    }
} catch {
    Fail "Khong mo duoc /login: $($_.Exception.Message)"
}

# ------------------------------------------------------------
# 3. Dang nhap
# ------------------------------------------------------------
if ($SkipLogin) {
    Info 'Bo qua dang nhap (-SkipLogin)'
} elseif ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = $env:PARKING_ADMIN_PASSWORD
}

if (-not $SkipLogin -and -not [string]::IsNullOrWhiteSpace($Password) -and $loginPage) {
    try {
        $csrf = [regex]::Match($loginPage.Content, 'name="_csrf"[^>]*?value="([^"]+)"').Groups[1].Value
        if (-not $csrf) {
            $csrf = [regex]::Match($loginPage.Content, 'value="([^"]+)"[^>]*?name="_csrf"').Groups[1].Value
        }
        if (-not $csrf) {
            throw 'Khong tim thay CSRF token trong trang login'
        }

        $body = @{
            username = $Username
            password = $Password
            _csrf    = $csrf
        }

        $loginResult = Invoke-WebRequest -Uri "$BaseUrl/login" -Method Post -Body $body `
            -ContentType 'application/x-www-form-urlencoded' `
            -WebSession $webSession -UseBasicParsing -TimeoutSec 20

        $finalUrl = Get-FinalUrl $loginResult
        if ($finalUrl -match '[?&]error') {
            Fail "Dang nhap that bai (sai tai khoan/mat khau): $finalUrl"
        } elseif ($finalUrl -match '/account/password') {
            Pass "Dang nhap thanh cong, tai khoan yeu cau doi mat khau lan dau"
        } elseif ($loginResult.StatusCode -eq 200) {
            Pass "Dang nhap thanh cong ($finalUrl)"
        } else {
            Fail "Dang nhap tra status $($loginResult.StatusCode)"
        }
    } catch {
        Fail "Dang nhap loi: $($_.Exception.Message)"
    }
} elseif (-not $SkipLogin) {
    Info 'Bo qua dang nhap: chua truyen -Password hoac PARKING_ADMIN_PASSWORD'
}

# ------------------------------------------------------------
# Ket qua
# ------------------------------------------------------------
Write-Host ''
if ($script:failures -gt 0) {
    Write-Host "SMOKE TEST THAT BAI: $($script:failures) muc" -ForegroundColor Red
    exit 1
}

Write-Host 'SMOKE TEST PASS' -ForegroundColor Green
exit 0
