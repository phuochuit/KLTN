$tokenVariable = 'PARKING_INTERNAL_SERVICE_TOKEN'
if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($tokenVariable, 'Process'))) {
    return
}

$workspace = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$tokenDirectory = Join-Path $PSScriptRoot 'keys'
$tokenFile = Join-Path $tokenDirectory 'internal-service-token.txt'

# An explicit value in .env.local takes precedence over the generated local token.
$localEnvironment = Join-Path $workspace '.env.local'
if (Test-Path -LiteralPath $localEnvironment) {
    foreach ($line in Get-Content -LiteralPath $localEnvironment) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
        $separator = $trimmed.IndexOf('=')
        if ($separator -le 0) { continue }
        $name = $trimmed.Substring(0, $separator).Trim()
        if ($name -eq $tokenVariable) {
            $value = $trimmed.Substring($separator + 1).Trim()
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                [Environment]::SetEnvironmentVariable($tokenVariable, $value, 'Process')
                return
            }
        }
    }
}

New-Item -ItemType Directory -Path $tokenDirectory -Force | Out-Null
if (-not (Test-Path -LiteralPath $tokenFile)) {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes) } finally { $generator.Dispose() }
    $token = [Convert]::ToBase64String($bytes)
    try {
        $stream = [IO.File]::Open($tokenFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $writer = New-Object IO.StreamWriter($stream, [Text.UTF8Encoding]::new($false))
            try { $writer.Write($token) } finally { $writer.Dispose() }
        } finally {
            if ($stream) { $stream.Dispose() }
        }
    } catch [IO.IOException] {
        # Another launcher created the shared token first.
    }
}

$storedToken = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
if ($storedToken.Length -lt 32) {
    throw 'Internal service token is missing or invalid. Delete the local token file and restart the launchers.'
}
[Environment]::SetEnvironmentVariable($tokenVariable, $storedToken, 'Process')
