param()
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

# Credentials remain in this process and its Java child; nothing is written to a secrets file.
$env:DB_URL = 'jdbc:mysql://localhost:3306/memory_recall?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME = 'root'
if (-not $env:DB_PASSWORD) {
    $securePassword = Read-Host 'Enter your existing MySQL root password (hidden)' -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential('root', $securePassword)
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
}
if (-not $env:DB_PASSWORD) { throw 'DB_PASSWORD is required. No password was guessed or changed.' }
if (-not $env:JWT_SECRET) {
    $keyBytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($keyBytes)
    [Array]::Clear($keyBytes, 0, $keyBytes.Length)
    Write-Host 'A temporary JWT signing key was generated in memory; it is not printed or saved.'
}
# Keep inherited DEBUG settings from enabling verbose HTTP/security output.
$env:DEBUG = 'false'
$env:TRACE = 'false'
Write-Host 'Target: localhost:3306/memory_recall, user root. No other database will be modified.'
Write-Host 'The server stays running on http://127.0.0.1:8080 after verification. Stop with Ctrl+C.'
& .\gradlew.bat --gradle-user-home .gradle-home --no-daemon --offline verifyLocalMySql
if ($LASTEXITCODE -ne 0) { throw 'Verification did not complete. Check build/local-mysql-verification.json and the error above.' }
