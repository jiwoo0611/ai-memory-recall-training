$ErrorActionPreference = 'Stop'
$apiRoot = 'http://localhost:18080'
function Login-Local {
    $login = Invoke-RestMethod -Method Post -Uri "$apiRoot/api/auth/login" -ContentType 'application/json' -Body '{"email":"demo@example.com","password":"DemoPass123!"}'
    return @{ Authorization = "Bearer $($login.accessToken)" }
}
$headers = Login-Local
$list = Invoke-RestMethod -Uri "$apiRoot/api/users/me/training-sessions?size=100" -Headers $headers
$record = $list.content | Where-Object status -EQ 'COMPLETED' | Select-Object -First 1
if (-not $record) { throw 'Submit at least one demo training session first.' }
$sourceUrl = "$apiRoot/api/users/me/training-sessions/$($record.id)"
$retryBody = @{ requestId = [guid]::NewGuid().ToString() } | ConvertTo-Json
$retryBefore = Invoke-RestMethod -Method Post -Uri "$sourceUrl/retrain" -Headers $headers -ContentType 'application/json' -Body $retryBody
$detailUrl = "$apiRoot/api/users/me/training-sessions/$($retryBefore.sessionId)"
$newSession = Invoke-RestMethod -Uri $detailUrl -Headers $headers
$answerBody = @{ answers = @($newSession.questions | ForEach-Object { @{ questionId = $_.id; status = 'ANSWERED'; content = 'Saved immediately before process restart.' } }) } | ConvertTo-Json -Depth 10
$null = Invoke-RestMethod -Method Post -Uri "$detailUrl/answers" -Headers $headers -ContentType 'application/json' -Body $answerBody
$null = Invoke-RestMethod -Method Post -Uri "$detailUrl/feedback" -Headers $headers
$before = Invoke-RestMethod -Uri $detailUrl -Headers $headers | ConvertTo-Json -Depth 30 -Compress
$before | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local-logs/restart-before.json')
& (Join-Path $PSScriptRoot 'Stop-Local.ps1')
& (Join-Path $PSScriptRoot 'Start-Local.ps1') -SkipBuild
$ready = $false
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    try { $headers = Login-Local; $ready = $true; break } catch { Start-Sleep -Seconds 1 }
}
if (-not $ready) { throw 'Restarted server did not become ready.' }
$after = Invoke-RestMethod -Uri $detailUrl -Headers $headers | ConvertTo-Json -Depth 30 -Compress
$after | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local-logs/restart-after.json')
if ($before -cne $after) { throw 'Saved result changed across restart.' }
$retryAfter = Invoke-RestMethod -Method Post -Uri "$sourceUrl/retrain" -Headers $headers -ContentType 'application/json' -Body $retryBody
if ($retryBefore.sessionId -ne $retryAfter.sessionId) { throw 'Retry created an extra session after restart.' }
Write-Output "PASS: newly saved training $($retryBefore.sessionId), answers, summary and retry identity survived forced process restart unchanged."
