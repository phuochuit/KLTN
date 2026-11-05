#requires -Version 5.1
<#
.SYNOPSIS
    Sinh cap JWT key RSA cho Spring Web (PKCS#8 private + X.509 public).

.DESCRIPTION
    Tao 2 file trong deploy/secrets:
      - jwt-private.pem  (-----BEGIN PRIVATE KEY-----)
      - jwt-public.pem   (-----BEGIN PUBLIC KEY-----)

    Mac dinh khong ghi de neu key da ton tai, de tranh lam mat hieu luc
    token Desktop dang dung. Dung -Force neu that su muon tao lai.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\gen-jwt-keys.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File deploy\gen-jwt-keys.ps1 -Force
#>
[CmdletBinding()]
param(
    [switch]$Force,
    [int]$KeySize = 2048
)

$ErrorActionPreference = 'Stop'

$scriptDir   = Split-Path -Parent $MyInvocation.MyCommand.Path
$secretsDir  = Join-Path $scriptDir 'secrets'
$privatePath = Join-Path $secretsDir 'jwt-private.pem'
$publicPath  = Join-Path $secretsDir 'jwt-public.pem'

function Find-OpenSsl {
    $cmd = Get-Command openssl -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    $candidates = @(
        (Join-Path $env:ProgramFiles 'Git\usr\bin\openssl.exe'),
        (Join-Path $env:ProgramFiles 'Git\mingw64\bin\openssl.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Git\usr\bin\openssl.exe'),
        (Join-Path $env:ProgramFiles 'OpenSSL-Win64\bin\openssl.exe')
    )
    foreach ($path in $candidates) {
        if ($path -and (Test-Path $path)) { return $path }
    }
    return $null
}

if ((Test-Path $privatePath) -and (Test-Path $publicPath) -and -not $Force) {
    Write-Host 'JWT key da ton tai:' -ForegroundColor Yellow
    Write-Host "  $privatePath"
    Write-Host "  $publicPath"
    Write-Host 'Khong ghi de. Dung -Force neu that su muon tao lai (token cu se mat hieu luc).' -ForegroundColor Yellow
    exit 0
}

New-Item -ItemType Directory -Force -Path $secretsDir | Out-Null

$openssl = Find-OpenSsl
if (-not $openssl) {
    throw 'Khong tim thay openssl. Cai Git for Windows hoac OpenSSL, hoac them openssl vao PATH.'
}

Write-Host "Dang tao RSA $KeySize bit..." -ForegroundColor Cyan

& $openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$KeySize" -out $privatePath
if ($LASTEXITCODE -ne 0) { throw "openssl genpkey that bai (exit $LASTEXITCODE)." }

& $openssl rsa -pubout -in $privatePath -out $publicPath
if ($LASTEXITCODE -ne 0) { throw "openssl rsa -pubout that bai (exit $LASTEXITCODE)." }

$privateText = Get-Content -Raw $privatePath
$publicText  = Get-Content -Raw $publicPath

if ($privateText -notmatch 'BEGIN PRIVATE KEY') {
    throw 'File private khong phai PKCS#8 (thieu BEGIN PRIVATE KEY).'
}
if ($publicText -notmatch 'BEGIN PUBLIC KEY') {
    throw 'File public khong phai X.509 (thieu BEGIN PUBLIC KEY).'
}

$publicDer = [Convert]::FromBase64String(($publicText -replace '-----[^-]+-----', '' -replace '\s', ''))
$sha256 = [System.Security.Cryptography.SHA256]::Create()
$hash = $sha256.ComputeHash($publicDer)
$fingerprint = ($hash | ForEach-Object { $_.ToString('x2') }) -join ':'

Write-Host ''
Write-Host 'Da tao JWT key:' -ForegroundColor Green
Write-Host "  Private : $privatePath"
Write-Host "  Public  : $publicPath"
Write-Host "  SHA-256 : $fingerprint"
Write-Host ''
Write-Host 'Buoc tiep theo:' -ForegroundColor Cyan
Write-Host '  1. copy deploy\.env.deploy.example deploy\.env.deploy'
Write-Host '  2. Dien thong tin Aiven/Laragon vao deploy\.env.deploy'
Write-Host '  3. Kiem tra PARKING_SECURITY_JWT_KID khop voi kid dang dung'
Write-Host '  4. cd deploy'
Write-Host '  5. docker compose up -d --build'
