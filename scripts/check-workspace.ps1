# Dot-source exposes the read-only path checks to tooling tests; normal execution also checks Git tracking.
function Get-ParkingWorkspaceFindings([string]$Workspace) {
    foreach ($path in 'tmp','.test-tmp','.dotnet-cli-home','.pytest_cache','spring-web/.test-tmp') {
        if (Test-Path -LiteralPath (Join-Path $Workspace $path)) { "Legacy output path exists: $path" }
    }
    foreach ($item in Get-ChildItem -LiteralPath $Workspace -Force -ErrorAction Stop) {
        if ($item.Name -like '.test-tmp-*' -or $item.Name -like 'qa_*') { "Unclassified root scratch: $($item.Name)" }
    }
    $target = Join-Path $Workspace 'spring-web/target'
    if (Test-Path -LiteralPath $target) {
        foreach ($item in Get-ChildItem -LiteralPath $target -Force) {
            if (($item.PSIsContainer -and ($item.Name -in 'test-gate-evidence','test-revenue-gate-evidence','test-uploads','test-revenue-uploads','test-tmp' -or $item.Name -like 'test-tmp-*')) -or
                (-not $item.PSIsContainer -and $item.Extension -eq '.log')) {
                "Legacy test output path exists: spring-web/target/$($item.Name)"
            }
        }
    }
    $files = @()
    $scripts = Join-Path $Workspace 'scripts'
    if (Test-Path $scripts) { $files += Get-ChildItem $scripts -File -Recurse -Include '*.ps1','*.psm1' | Where-Object { $_.Name -ne 'check-workspace.ps1' } }
    $thesis = Join-Path $Workspace 'tools/thesis'
    if (Test-Path $thesis) { $files += Get-ChildItem $thesis -File -Filter '*.py' }
    $pattern = '\.dotnet-cli-home|\.test-tmp|D:[\\/]Code[\\/]|C:[\\/]Users[\\/]Dphuoc1903'
    foreach ($file in $files) {
        if (Select-String -LiteralPath $file.FullName -Pattern $pattern -Quiet) { "Legacy active path reference: $($file.FullName.Substring($Workspace.Length+1))" }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    $ErrorActionPreference = 'Stop'
    $workspace = Split-Path -Parent $PSScriptRoot
    $findings = @(Get-ParkingWorkspaceFindings $workspace)
    $trackedLocal = @(git -C $workspace ls-files -- .cache .artifacts .codegraph tasks) + @(git -C $workspace ls-files -- .opencode | Where-Object { $_ -notlike '.opencode/skills/*' })
    if ($LASTEXITCODE -ne 0) { throw 'Git tracking check unavailable; workspace verification is blocked.' }
    if ($trackedLocal.Count -gt 0) { $findings += "Local-only files tracked: $($trackedLocal.Count). Inspect git ls-files; no index changes were made." }
    $trackedDependencies = @(git -C $workspace ls-files -- survey_analysis/node_modules)
    if ($LASTEXITCODE -ne 0) { throw 'Dependency tracking check unavailable.' }
    if ($trackedDependencies.Count -gt 0) { Write-Warning "Known preserved exception: $($trackedDependencies.Count) survey node_modules files are tracked. See survey_analysis/README.md; ignore cannot untrack them." }
    if ($findings.Count -gt 0) {
        $findings | ForEach-Object { Write-Host "FAIL $_" }
        exit 1
    }
    Write-Host 'Workspace path/tracking checks passed (known warnings are not resolved). No files were changed.'
    exit 0
}
