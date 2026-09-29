$ErrorActionPreference = 'Stop'
$demoDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspace = Split-Path -Parent $demoDir
$python = Join-Path (Split-Path -Parent $workspace) 'anpr-service\.venv\Scripts\python.exe'

if (-not (Test-Path $python)) {
    throw 'Chua co moi truong Python. Hay chay run-anpr.cmd o thu muc goc project truoc.'
}

$yunet = Join-Path $demoDir 'models\face_detection_yunet_2023mar.onnx'
$sface = Join-Path $demoDir 'models\face_recognition_sface_2021dec.onnx'
if (-not (Test-Path $yunet) -or -not (Test-Path $sface)) {
    Write-Host 'Models are missing. Downloading from OpenCV Zoo...' -ForegroundColor Cyan
    & (Join-Path $demoDir 'download-models.ps1')
}

function Test-DemoHealth([int]$port) {
    try {
        $healthUrl = "http://127.0.0.1:$port/api/health"
        $response = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 2
        return ($null -ne $response.modelsReady)
    } catch {
        return $false
    }
}

$port = 8002
if (Test-DemoHealth $port) {
    $existingUrl = "http://localhost:$port"
    Write-Host "The demo is already running at $existingUrl" -ForegroundColor Yellow
    if ($env:FACE_DEMO_NO_BROWSER -ne '1') {
        Start-Process $existingUrl
    }
    if ($env:FACE_DEMO_NONINTERACTIVE -ne '1') {
        Read-Host 'Press Enter to close this duplicate launcher'
    }
    exit 0
}

$usedPorts = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port
while (($usedPorts -contains $port) -and ($port -lt 8012)) {
    $port++
}
if ($usedPorts -contains $port) {
    throw 'No free port was found from 8002 through 8012.'
}

$demoUrl = "http://localhost:$port"
Write-Host "Demo: $demoUrl" -ForegroundColor Green
Write-Host 'Press Ctrl+C to stop.' -ForegroundColor DarkGray

if ($env:FACE_DEMO_NO_BROWSER -ne '1') {
    $openBrowser = "Start-Sleep -Seconds 2; Start-Process '$demoUrl'"
    Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile', '-Command', $openBrowser) -WindowStyle Hidden
}

Push-Location $demoDir
$serverExitCode = 0
try {
    & $python -m uvicorn app:app --host 127.0.0.1 --port $port
    $serverExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($serverExitCode -ne 0) {
    throw "Demo server stopped with exit code $serverExitCode."
}
