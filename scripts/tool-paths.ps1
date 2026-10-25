function Resolve-ParkingMaven([string]$Workspace) {
    $bundled = Join-Path $Workspace '.cache/toolchains/apache-maven-3.9.11/bin/mvn.cmd'
    if (Test-Path -LiteralPath $bundled) { return $bundled }
    $command = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'Maven missing. Restore Apache Maven 3.9.11 under .cache/toolchains/ or provide mvn.cmd in PATH. No automatic download.'
}
