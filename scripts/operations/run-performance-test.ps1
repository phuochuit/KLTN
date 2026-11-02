param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$pyScript = Join-Path $scriptDir "benchmark_load.py"

$env:PARKING_BASE_URL = $BaseUrl
Write-Host "Running Parking ANPR Performance Benchmark via Python..." -ForegroundColor Cyan
& python $pyScript
