$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path

# Laragon la duong du phong local, tach khoi .env.local cua Aiven. Co the override
# bang PARKING_LARAGON_* ma khong phai sua hoac commit credential vao repository.
$env:PARKING_DB_MODE = 'laragon'
$env:PARKING_DB_URL = if ([string]::IsNullOrWhiteSpace($env:PARKING_LARAGON_URL)) {
    'jdbc:mysql://127.0.0.1:3306/parking_anpr?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh&useSSL=false&allowPublicKeyRetrieval=true'
} else { $env:PARKING_LARAGON_URL }
$env:PARKING_DB_USERNAME = if ([string]::IsNullOrWhiteSpace($env:PARKING_LARAGON_USERNAME)) {
    'root'
} else { $env:PARKING_LARAGON_USERNAME }
if ($null -ne $env:PARKING_LARAGON_PASSWORD) {
    $env:PARKING_DB_PASSWORD = $env:PARKING_LARAGON_PASSWORD
} elseif ($env:PARKING_LARAGON_ALLOW_EMPTY_PASSWORD -eq 'true') {
    $env:PARKING_DB_PASSWORD = ''
} else {
    $securePassword = Read-Host "Nhap mat khau MySQL Laragon cho user $env:PARKING_DB_USERNAME" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:PARKING_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

& (Join-Path $workspace 'run-web.ps1')
exit $LASTEXITCODE
