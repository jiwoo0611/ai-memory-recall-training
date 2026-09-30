$ErrorActionPreference = 'Stop'
$statePath = Join-Path $PSScriptRoot 'local-logs/processes.json'
if (-not (Test-Path -LiteralPath $statePath)) { Write-Output 'No saved local server processes.'; return }
$state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
foreach ($kind in @('backend', 'frontend')) {
    $process = Get-Process -Id $state.$kind -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq ([datetime]$state."${kind}Started").ToUniversalTime().Ticks) {
        Stop-Process -Id $process.Id
        Wait-Process -Id $process.Id -Timeout 15 -ErrorAction SilentlyContinue
    }
}
Write-Output 'Local processes stopped. Database and images are preserved.'
