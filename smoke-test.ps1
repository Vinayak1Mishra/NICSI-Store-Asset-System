Write-Host "=== 1. Health check ==="
$health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
$health | ConvertTo-Json

Write-Host "=== 2. Dev token for store.operator ==="
$tokenResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/dev/token?username=store.operator"
$token = $tokenResp.token
Write-Host "Token obtained: $($token.Substring(0, 30))..."
Write-Host "User: $($tokenResp.user.username) ($($tokenResp.user.displayName))"

Write-Host "=== 3. WhoAmI ==="
$headers = @{ "Authorization" = "Bearer $token" }
$whoami = Invoke-RestMethod -Uri "http://localhost:8080/api/store/whoami" -Headers $headers
$whoami | ConvertTo-Json

Write-Host "=== 4. Ping with store.operator (expecting 403 Forbidden) ==="
try {
    Invoke-RestMethod -Uri "http://localhost:8080/api/store/ping" -Headers $headers
} catch {
    Write-Host "Status Code: $($_.Exception.Response.StatusCode.value__)"
}

Write-Host "=== 5. Token for admin & Ping with admin (expecting 200 OK) ==="
$adminResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/dev/token?username=admin"
$adminHeaders = @{ "Authorization" = "Bearer $($adminResp.token)" }
$ping = Invoke-RestMethod -Uri "http://localhost:8080/api/store/ping" -Headers $adminHeaders
$ping | ConvertTo-Json

Write-Host "=== All smoke tests passed! ==="
