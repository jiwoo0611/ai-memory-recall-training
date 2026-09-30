param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$backendPath = Join-Path $projectRoot 'backend'
$frontendPath = Join-Path $projectRoot 'frontend'
$logPath = Join-Path $projectRoot 'local-logs'
New-Item -ItemType Directory -Force -Path $logPath | Out-Null
foreach ($port in @(18080, 5173)) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $port is already in use. Stop the previous local server first."
    }
}
if (-not $SkipBuild) {
    Push-Location $backendPath
    try { & .\gradlew.bat bootJar --no-daemon; if ($LASTEXITCODE -ne 0) { throw 'Backend build failed' } }
    finally { Pop-Location }
    if (-not (Test-Path (Join-Path $frontendPath 'node_modules'))) {
        Push-Location $frontendPath
        try { & npm.cmd ci; if ($LASTEXITCODE -ne 0) { throw 'Frontend install failed' } }
        finally { Pop-Location }
    }
}
$jar = Get-ChildItem (Join-Path $backendPath 'build/libs/*.jar') | Where-Object Name -NotLike '*-plain.jar' | Select-Object -First 1
if (-not $jar) { throw 'Build the backend first.' }
$backendProcess = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', ('"' + $jar.FullName + '"'), '--spring.profiles.active=local') -WorkingDirectory $backendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logPath 'backend.log') -RedirectStandardError (Join-Path $logPath 'backend.err.log')
$env:VITE_API_BASE_URL = 'http://localhost:18080'
$frontendProcess = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @('node_modules/vite/bin/vite.js', '--configLoader', 'runner', '--host', 'localhost', '--port', '5173', '--strictPort') -WorkingDirectory $frontendPath -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logPath 'frontend.log') -RedirectStandardError (Join-Path $logPath 'frontend.err.log')
@{ backend = $backendProcess.Id; frontend = $frontendProcess.Id; backendStarted = $backendProcess.StartTime.ToUniversalTime().ToString('o'); frontendStarted = $frontendProcess.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $logPath 'processes.json')
Write-Output 'Local app: http://localhost:5173/#/login'
Write-Output 'Demo login: demo@example.com / DemoPass123!'
Write-Output 'The backend may need 15-30 seconds to start. Logs are in local-logs.'
