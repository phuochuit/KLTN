$ErrorActionPreference = 'Stop'

$workspace = Split-Path -Parent $PSScriptRoot
$source = Join-Path $workspace 'private-data\evidence'
$backupRoot = Join-Path $workspace 'backups\private-data'

if (-not (Test-Path -LiteralPath $source)) {
    throw "Evidence storage not found: $source"
}

New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$target = Join-Path $backupRoot "evidence-$stamp"

Write-Host "Backing up evidence: $source"
Write-Host "Destination: $target"

robocopy $source $target /E /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
$exitCode = $LASTEXITCODE
if ($exitCode -ge 8) {
    throw "Robocopy failed with exit code $exitCode"
}

$files = (Get-ChildItem -LiteralPath $target -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count
Write-Host "Backup completed: $files files" -ForegroundColor Green
