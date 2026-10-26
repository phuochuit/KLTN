$ErrorActionPreference = 'Stop'

$mysqlPath = if ($c = Get-Command mysql.exe -ErrorAction SilentlyContinue) { $c.Source } else { 'mysql.exe' }
if (-not (Test-Path $mysqlPath)) { throw 'Laragon mysql.exe not found' }

$configPath = Join-Path $PSScriptRoot '..\..\spring-web\src\main\resources\application.properties'
$settings = @{}
foreach ($line in [System.IO.File]::ReadAllLines($configPath)) {
    if ($line -match '^\s*([^#!][^=]*)=(.*)$') {
        $settings[$matches[1].Trim()] = $matches[2].Trim()
    }
}

$rawUrl = $settings['spring.datasource.url']
if ($rawUrl -notmatch '^jdbc:mysql://([^:/]+)(?::(\d+))/([^?]+)') { throw 'Unexpected datasource URL format' }
$mysqlHost = $matches[1]
$mysqlPort = if ($matches[2]) { [int]$matches[2] } else { 3306 }
if ($matches[3] -ne 'parking_anpr') { throw 'Refusing non-parking_anpr database' }

$dbUsername = [string]$settings['spring.datasource.username']
$dbPassword = [string]$settings['spring.datasource.password']
$env:MYSQL_PWD = $dbPassword
try {
    $output = & $mysqlPath --default-character-set=utf8mb4 --protocol=TCP --host=$mysqlHost --port=$mysqlPort --user=$dbUsername --connect-timeout=10 --batch --skip-column-names --execute="SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('system_accounts','security_audit_events','desktop_sessions','desktop_refresh_tokens') ORDER BY table_name;"
    if ($LASTEXITCODE -ne 0) { throw 'Schema verification query failed' }
    Write-Output $output
}
finally {
    $env:MYSQL_PWD = $null
}
