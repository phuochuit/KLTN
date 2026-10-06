$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $MyInvocation.MyCommand.Path
$service = Join-Path $workspace 'anpr-service'
$venv = Join-Path $service '.venv'
$python = Join-Path $venv 'Scripts\python.exe'
$requirements = Join-Path $service 'requirements.txt'
$requirementsStamp = Join-Path $venv '.requirements.sha256'
$env:EASYOCR_MODULE_PATH = Join-Path $service '.EasyOCR'
$env:YOLO_CONFIG_DIR = Join-Path $service '.ultralytics'
$env:PYTHONUTF8 = '1'

function Resolve-BasePython {
    $candidates = New-Object System.Collections.Generic.List[object]
    if ($env:PARKING_PYTHON) {
        $candidates.Add([pscustomobject]@{ Command = $env:PARKING_PYTHON; Prefix = @(); Label = 'PARKING_PYTHON' })
    }
    $launcher = Get-Command 'py.exe' -ErrorAction SilentlyContinue
    if ($launcher) {
        foreach ($version in @('-3.12', '-3.11', '-3.10', '-3')) {
            $candidates.Add([pscustomobject]@{ Command = $launcher.Source; Prefix = @($version); Label = "py $version" })
        }
    }
    foreach ($name in @('python.exe', 'python3.exe', 'python', 'python3')) {
        $command = Get-Command $name -ErrorAction SilentlyContinue
        if ($command) {
            $candidates.Add([pscustomobject]@{ Command = $command.Source; Prefix = @(); Label = $name })
        }
    }
    foreach ($candidate in $candidates) {
        try {
            & $candidate.Command @($candidate.Prefix) -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" 2>$null
            if ($LASTEXITCODE -eq 0) { return $candidate }
        } catch { }
    }
    throw @'
Khong tim thay Python 3.10 tro len.
Cach xu ly:
1. Cai Python tu https://www.python.org/downloads/
2. Khi cai, danh dau "Add python.exe to PATH".
3. Dong terminal cu va chay lai run-anpr.cmd.
Neu Python nam o vi tri rieng, dat bien PARKING_PYTHON tro den python.exe.
'@
}

function Test-VenvPython {
    if (-not (Test-Path -LiteralPath $python)) { return $false }
    try {
        & $python -c "import sys; print(sys.executable)" 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch { return $false }
}

$occupied = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue
if ($occupied) {
    # A just-started Uvicorn process can reserve its port before /health is
    # ready. Wait briefly, then distinguish our running ANPR service from a
    # real port conflict instead of showing a misleading generic error.
    $health = $null
    for ($attempt = 1; $attempt -le 10; $attempt++) {
        try {
            $health = Invoke-RestMethod -Uri 'http://localhost:8001/health' -TimeoutSec 5
            if ($health.status -eq 'UP' -and $health.service -eq 'parking-anpr') { break }
            $health = $null
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if ($health) {
        Write-Host 'ANPR API dang chay san tai http://localhost:8001. Khong can mo run-anpr.cmd lan thu hai.' -ForegroundColor Green
        exit 0
    }
    $processId = $occupied[0].OwningProcess
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    $processName = if ($process) { $process.ProcessName } else { 'unknown' }
    throw "Cong 8001 dang duoc tien trinh $processName (PID $processId) su dung, nhung khong phai ANPR API. Hay dong tien trinh do hoac doi cong."
}

if (-not (Test-VenvPython)) {
    if (Test-Path -LiteralPath $venv) {
        $backup = "$venv.incompatible.$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Write-Host "Moi truong Python cu khong dung duoc tren may nay. Dang chuyen sang: $backup" -ForegroundColor Yellow
        Move-Item -LiteralPath $venv -Destination $backup
    }
    $basePython = Resolve-BasePython
    Write-Host "Dang tao moi truong Python bang $($basePython.Label)..." -ForegroundColor Cyan
    & $basePython.Command @($basePython.Prefix) -m venv $venv
    if ($LASTEXITCODE -ne 0 -or -not (Test-VenvPython)) { throw 'Khong tao duoc moi truong Python .venv.' }
    & $python -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw 'Khong cap nhat duoc pip.' }
}

$currentHash = (Get-FileHash -LiteralPath $requirements -Algorithm SHA256).Hash
$installedHash = if (Test-Path -LiteralPath $requirementsStamp) { (Get-Content -LiteralPath $requirementsStamp -Raw).Trim() } else { '' }
if ($currentHash -ne $installedHash) {
    Write-Host 'Dang cai dat/cap nhat thu vien Python cho ANPR...' -ForegroundColor Cyan
    & $python -m pip install -r $requirements
    if ($LASTEXITCODE -ne 0) { throw 'Cai dat thu vien Python that bai. Hay kiem tra ket noi Internet va chay lai.' }
    Set-Content -LiteralPath $requirementsStamp -Value $currentHash -Encoding ASCII
}

Write-Host 'ANPR API: http://localhost:8001' -ForegroundColor Green
Push-Location $service
try {
    while ($true) {
        & $python -m uvicorn app.main:app --host 0.0.0.0 --port 8001
        $exitCode = $LASTEXITCODE
        if ($exitCode -eq 0) { break } # Ctrl+C or a normal shutdown.
        Write-Warning "ANPR native runtime stopped unexpectedly (exit code $exitCode). Restarting in 2 seconds..."
        Start-Sleep -Seconds 2
    }
} finally { Pop-Location }
