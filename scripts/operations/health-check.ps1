param(
    [string]$SpringUrl = 'http://127.0.0.1:8080',
    [string]$AnprUrl = 'http://127.0.0.1:8001'
)
$ErrorActionPreference = 'Stop'
$result = [ordered]@{ checkedAt = (Get-Date).ToUniversalTime().ToString('o') }
$healthy = $true
try {
    $spring = Invoke-RestMethod "$($SpringUrl.TrimEnd('/'))/api/parking/health" -TimeoutSec 10
    $result.spring = $spring.status
    if ($spring.status -ne 'UP') { $healthy = $false }
} catch { $result.spring = 'DOWN'; $result.springError = $_.Exception.Message; $healthy = $false }
try {
    $anpr = Invoke-RestMethod "$($AnprUrl.TrimEnd('/'))/health" -TimeoutSec 10
    $result.anpr = if ($anpr.status) { $anpr.status } else { 'UP' }
} catch { $result.anpr = 'DOWN'; $result.anprError = $_.Exception.Message; $healthy = $false }
$result.healthy = $healthy
$result | ConvertTo-Json -Depth 4
if (-not $healthy) { exit 1 }
