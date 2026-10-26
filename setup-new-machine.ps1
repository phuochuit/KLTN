# Kiem tra va chuan bi mot may moi chay duoc du an.
# Chay: setup-new-machine.cmd  (KHONG bam dup file .ps1)
# Script chi tu sua cac viec an toan: tao .env.local tu file mau, sinh token noi bo,
# sinh cap khoa JWT Desktop neu may co openssl. Con lai chi bao thieu, khong tu tai.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$results = New-Object System.Collections.ArrayList

function Add-Check {
    param([string]$Name, [string]$Status, [string]$Detail)
    [void]$results.Add([pscustomobject]@{ Muc = $Name; KetQua = $Status; ChiTiet = $Detail })
}

# Goi chuong trinh ngoai ma khong de stderr lam dung script (java -version ghi vao stderr).
function Invoke-Native {
    param([string]$FilePath, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = & $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        return ($lines -join "`n")
    } catch {
        return ''
    } finally {
        $ErrorActionPreference = $previous
    }
}

function Find-OpenSsl {
    $command = Get-Command openssl.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $patterns = @('C:\laragon\bin\apache\*\bin\openssl.exe',
                  'D:\laragon\bin\apache\*\bin\openssl.exe',
                  'D:\laragon_new\bin\apache\*\bin\openssl.exe',
                  'C:\Program Files\Git\usr\bin\openssl.exe')
    foreach ($pattern in $patterns) {
        $found = Get-ChildItem $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { return $found.FullName }
    }
    return $null
}

function Test-TcpPort {
    param([string]$ComputerName, [int]$Port, [int]$TimeoutMs = 5000)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $task = $client.ConnectAsync($ComputerName, $Port)
        if ($task.Wait($TimeoutMs) -and $client.Connected) { return $true }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

Write-Host ''
Write-Host '=== KIEM TRA MAY MOI CHAY DU AN PARKING ANPR ===' -ForegroundColor Cyan
Write-Host "Thu muc: $root"
Write-Host ''

if (-not (Test-Path (Join-Path $root 'run-all-laragon.cmd'))) {
    Write-Host 'LOI: Khong tim thay run-all-laragon.cmd. Hay dat script nay o thu muc goc project.' -ForegroundColor Red
    exit 1
}

# 1. Java 21 tro len
$javaDetail = ''
$javaOk = $false
$javaFolders = Get-ChildItem 'C:\Program Files\Java\jdk-*' -Directory -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending
foreach ($folder in $javaFolders) {
    if ($folder.Name -match 'jdk-(\d+)') {
        if ([int]$matches[1] -ge 21) { $javaOk = $true; $javaDetail = $folder.FullName; break }
    }
}
if (-not $javaOk) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        $versionText = Invoke-Native $javaCommand.Source @('-version')
        if ($versionText -match '"(\d+)') {
            if ([int]$matches[1] -ge 21) { $javaOk = $true; $javaDetail = ($versionText -split "`n")[0] }
            else { $javaDetail = "Dang la Java $($matches[1]). Can JDK 21 tro len." }
        } else {
            $javaDetail = 'Khong doc duoc phien ban Java.'
        }
    } else {
        $javaDetail = 'Chua cai JDK 21. Tai Temurin 21 hoac Oracle JDK 21.'
    }
}
if ($javaOk) { Add-Check 'Java 21+' 'OK' $javaDetail } else { Add-Check 'Java 21+' 'THIEU' $javaDetail }

# 2. Maven (bundled trong tools)
$maven = Join-Path $root 'tools\apache-maven-3.9.11\bin\mvn.cmd'
if (Test-Path $maven) { Add-Check 'Maven' 'OK' 'tools\apache-maven-3.9.11' }
else { Add-Check 'Maven' 'THIEU' 'Thieu tools\apache-maven-3.9.11. Clone lai repo day du.' }

# 3. .NET 8 SDK (cho Desktop)
$dotnet = Get-Command dotnet.exe -ErrorAction SilentlyContinue
if ($dotnet) {
    $sdks = Invoke-Native $dotnet.Source @('--list-sdks')
    if ($sdks -match '(?m)^8\.') {
        $firstSdk = ($sdks -split "`n" | Where-Object { $_ -match '^8\.' } | Select-Object -First 1)
        Add-Check '.NET 8 SDK' 'OK' $firstSdk
    } else {
        Add-Check '.NET 8 SDK' 'THIEU' 'Da co dotnet nhung thieu SDK 8.x.'
    }
} else {
    Add-Check '.NET 8 SDK' 'THIEU' 'Chua cai .NET 8 SDK (can cho Desktop WinForms).'
}

# 4. Python 3.10+ (cho ANPR)
$pythonOk = $false
$pythonDetail = ''
foreach ($candidate in @('py', 'python')) {
    $command = Get-Command $candidate -ErrorAction SilentlyContinue
    if (-not $command) { continue }
    $versionText = Invoke-Native $command.Source @('--version')
    if ($versionText -match 'Python (\d+)\.(\d+)') {
        $major = [int]$matches[1]; $minor = [int]$matches[2]
        $pythonDetail = $versionText.Trim()
        if ($major -eq 3 -and $minor -ge 10) { $pythonOk = $true; break }
    }
}
if ($pythonOk) { Add-Check 'Python 3.10+' 'OK' $pythonDetail }
else { Add-Check 'Python 3.10+' 'THIEU' 'Can Python 3.10 tro len (run-anpr.ps1 se tu tao .venv).' }

# 5. .env.local
$envPath = Join-Path $root '.env.local'
$envExample = Join-Path $root '.env.local.example'
$envMap = @{}
if (-not (Test-Path $envPath)) {
    if (Test-Path $envExample) {
        Copy-Item $envExample $envPath
        Add-Check '.env.local' 'CAN-SUA' 'Da tao tu .env.local.example. Mo file va dien host/user/password Aiven.'
    } else {
        Add-Check '.env.local' 'THIEU' 'Khong co .env.local lan file mau.'
    }
} else {
    foreach ($line in Get-Content $envPath) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { continue }
        $envMap[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
    }
    $missing = @()
    foreach ($name in @('PARKING_DB_URL', 'PARKING_DB_USERNAME', 'PARKING_DB_PASSWORD')) {
        if (-not $envMap.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($envMap[$name])) { $missing += $name }
    }
    if ($missing.Count -gt 0) {
        Add-Check '.env.local' 'CAN-SUA' ('Thieu bien: ' + ($missing -join ', '))
    } elseif ($envMap['PARKING_DB_URL'] -match 'YOUR_AIVEN_HOST') {
        Add-Check '.env.local' 'CAN-SUA' 'Van con placeholder YOUR_AIVEN_HOST, can dien host that.'
    } elseif ($envMap['PARKING_DB_URL'] -notmatch '/parking_anpr(\?|$)') {
        Add-Check '.env.local' 'CAN-SUA' 'PARKING_DB_URL phai tro toi database parking_anpr.'
    } elseif ($envMap['PARKING_DB_URL'] -match 'VERIFY_CA|VERIFY_IDENTITY') {
        Add-Check '.env.local' 'CAN-SUA' 'URL dang bat VERIFY_CA: phai co them CA certificate. Doi sang sslMode=REQUIRED neu khong can.'
    } else {
        Add-Check '.env.local' 'OK' 'Du 3 bien, URL hop le.'
    }
}

# 6. Ket noi database (chi kiem tra mo cong TCP)
if ($envMap.ContainsKey('PARKING_DB_URL') -and $envMap['PARKING_DB_URL'] -match '^jdbc:mysql://([^:/]+):(\d+)/([^?]+)') {
    $dbHost = $matches[1]; $dbPort = [int]$matches[2]; $dbName = $matches[3]
    if (Test-TcpPort -ComputerName $dbHost -Port $dbPort) {
        Add-Check 'Ket noi database' 'OK' "${dbHost}:$dbPort/$dbName mo cong"
    } else {
        Add-Check 'Ket noi database' 'LOI' "Khong mo duoc ${dbHost}:$dbPort (Aiven sap, sai host, hoac mang chan)."
    }
} else {
    Add-Check 'Ket noi database' 'BO-QUA' 'Chua co PARKING_DB_URL hop le.'
}

# 7. Model AI: 2 YOLO (.pt) + 2 ONNX khuon mat
$yoloMissing = @()
foreach ($model in @('anpr-service\models\license_plate_detector.pt', 'anpr-service\models\vehicle_detector.pt')) {
    if (-not (Test-Path (Join-Path $root $model))) { $yoloMissing += (Split-Path $model -Leaf) }
}

$onnxNames = @('face_detection_yunet_2023mar.onnx', 'face_recognition_sface_2021dec.onnx')
function Test-OnnxModel {
    param([string]$Name)
    if (Test-Path (Join-Path $root "anpr-service\models\$Name")) { return $true }
    if (Test-Path (Join-Path $root "face-verification-demo\models\$Name")) { return $true }
    return $false
}

$onnxMissing = @($onnxNames | Where-Object { -not (Test-OnnxModel $_) })
if ($onnxMissing.Count -gt 0) {
    $downloader = Join-Path $root 'face-verification-demo\download-models.ps1'
    if (Test-Path $downloader) {
        Write-Host 'Dang tai 2 model khuon mat ONNX...' -ForegroundColor Cyan
        try { & $downloader | Out-Null } catch { }
        $onnxMissing = @($onnxNames | Where-Object { -not (Test-OnnxModel $_) })
    }
}

if ($yoloMissing.Count -eq 0 -and $onnxMissing.Count -eq 0) {
    Add-Check 'Model AI' 'OK' '2 YOLO .pt + 2 ONNX khuon mat'
} else {
    $parts = @()
    if ($yoloMissing.Count -gt 0) { $parts += ('YOLO: ' + ($yoloMissing -join ', ') + ' - copy tu may dang chay') }
    if ($onnxMissing.Count -gt 0) { $parts += ('ONNX: ' + ($onnxMissing -join ', ') + ' - tai that bai') }
    Add-Check 'Model AI' 'THIEU' ($parts -join ' | ')
}

# 8. Anh bang chung
$privateData = Join-Path $root 'spring-web\private-data'
if (Test-Path $privateData) {
    $count = (Get-ChildItem $privateData -Recurse -File -ErrorAction SilentlyContinue).Count
    Add-Check 'private-data' 'OK' "$count file (anh cu dan + bang chung)"
} else {
    Add-Check 'private-data' 'THIEU' 'Copy thu muc spring-web\private-data tu may dang chay (thieu thi man hinh doi chieu trong).'
}

# 9. Token noi bo (tu sinh duoc)
$tokenFile = Join-Path $root 'scripts\security\keys\internal-service-token.txt'
$ensureToken = Join-Path $root 'scripts\security\ensure-internal-service-token.ps1'
if (Test-Path $tokenFile) {
    Add-Check 'Token noi bo' 'OK' 'scripts\security\keys\internal-service-token.txt'
} elseif (Test-Path $ensureToken) {
    try { & $ensureToken } catch { }
    if (Test-Path $tokenFile) { Add-Check 'Token noi bo' 'OK' 'Da tu sinh' }
    else { Add-Check 'Token noi bo' 'LOI' 'Khong sinh duoc token, chay tay ensure-internal-service-token.ps1.' }
} else {
    Add-Check 'Token noi bo' 'THIEU' 'Thieu scripts\security\ensure-internal-service-token.ps1'
}

# 10. Khoa JWT Desktop
$privateKey = Join-Path $root 'scripts\security\keys\desktop-jwt-private.pem'
$publicKey = Join-Path $root 'scripts\security\keys\desktop-jwt-public.pem'
if ((Test-Path $privateKey) -and (Test-Path $publicKey)) {
    Add-Check 'Khoa JWT Desktop' 'OK' '2 file .pem da co'
} else {
    $openSsl = Find-OpenSsl
    if ($openSsl) {
        $keyDir = Split-Path -Parent $privateKey
        if (-not (Test-Path $keyDir)) { New-Item -ItemType Directory -Path $keyDir | Out-Null }
        $env:RANDFILE = Join-Path $env:TEMP '.openssl-rand'
        Invoke-Native $openSsl @('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', $privateKey) | Out-Null
        Invoke-Native $openSsl @('rsa', '-in', $privateKey, '-pubout', '-out', $publicKey) | Out-Null
        if ((Test-Path $privateKey) -and (Test-Path $publicKey)) {
            Add-Check 'Khoa JWT Desktop' 'OK' 'Da tu sinh bang openssl (PKCS#8)'
        } else {
            Add-Check 'Khoa JWT Desktop' 'LOI' 'openssl chay that bai, copy 2 file .pem tu may dang chay.'
        }
    } else {
        Add-Check 'Khoa JWT Desktop' 'THIEU' 'Thieu 2 file .pem va khong tim thay openssl. Copy tu may dang chay.'
    }
}

# 11. Cong 8080 / 8001
foreach ($port in @(8080, 8001)) {
    $listener = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($listener) { Add-Check "Cong $port" 'CANH-BAO' "Dang bi PID $($listener.OwningProcess) chiem. Tat app cu truoc khi chay." }
    else { Add-Check "Cong $port" 'OK' 'Dang trong' }
}

# 12. mysql.exe cho backup (tuy chon)
$mysql = Get-Command mysql.exe -ErrorAction SilentlyContinue
if ($mysql) { Add-Check 'mysql.exe (backup)' 'OK' $mysql.Source }
else { Add-Check 'mysql.exe (backup)' 'TUY-CHON' 'Chi can khi chay backup/restore. Laragon co san.' }

Write-Host ''
$results | Format-Table -AutoSize -Wrap
Write-Host ''

$fail = @($results | Where-Object { $_.KetQua -eq 'THIEU' -or $_.KetQua -eq 'LOI' })
$warn = @($results | Where-Object { $_.KetQua -eq 'CAN-SUA' -or $_.KetQua -eq 'CANH-BAO' -or $_.KetQua -eq 'BO-QUA' })

if ($fail.Count -gt 0) {
    Write-Host "CON $($fail.Count) MUC PHAI XU LY TRUOC KHI CHAY." -ForegroundColor Red
    foreach ($item in $fail) { Write-Host "  - $($item.Muc): $($item.ChiTiet)" -ForegroundColor Red }
    Write-Host ''
    Write-Host 'Sau khi sua, chay lai setup-new-machine.cmd de kiem tra.' -ForegroundColor Yellow
    exit 1
}

if ($warn.Count -gt 0) {
    Write-Host "Da du de chay, nhung con $($warn.Count) muc nen xem lai:" -ForegroundColor Yellow
    foreach ($item in $warn) { Write-Host "  - $($item.Muc): $($item.ChiTiet)" -ForegroundColor Yellow }
} else {
    Write-Host 'MOI THU DA SAN SANG.' -ForegroundColor Green
}

Write-Host ''
Write-Host 'Buoc tiep theo: chay run-all-laragon.cmd (hoac run-web.cmd + run-anpr.cmd + run-desktop.cmd).' -ForegroundColor Cyan
exit 0
