$ErrorActionPreference = 'Stop'
$baseUrl = 'http://localhost:8080'
$apiKey = if ($env:PAYROUTE_API_KEY) { $env:PAYROUTE_API_KEY } else { 'local-demo-key' }
$webhookSecret = if ($env:PAYROUTE_WEBHOOK_SECRET) { $env:PAYROUTE_WEBHOOK_SECRET } else { 'local-webhook-secret-change-me' }
$headers = @{ 'X-API-Key' = $apiKey }

function Invoke-Api($method, $path, $body, $extraHeaders = @{}) {
    $requestHeaders = @{}
    $headers.GetEnumerator() | ForEach-Object { $requestHeaders[$_.Key] = $_.Value }
    $extraHeaders.GetEnumerator() | ForEach-Object { $requestHeaders[$_.Key] = $_.Value }
    if ($null -eq $body) {
        return Invoke-RestMethod -Method $method -Uri "$baseUrl$path" -Headers $requestHeaders
    }
    $json = ConvertTo-Json -InputObject $body -Depth 12 -Compress
    return Invoke-RestMethod -Method $method -Uri "$baseUrl$path" -Headers $requestHeaders -ContentType 'application/json' -Body $json
}

function New-Payment($merchant, $amount, $method, $scenario, $key) {
    $body = @{ merchantId = $merchant; amountMinor = $amount; currency = 'INR'; paymentMethod = $method; simulationScenario = $scenario }
    return Invoke-Api 'Post' '/api/v1/payments' $body @{ 'Idempotency-Key' = $key }
}

Write-Host 'Creating and replaying a successful UPI payment...'
$success = New-Payment 'demo-merchant' 12500 'UPI' 'SUCCESS' 'demo-success-001'
$replay = New-Payment 'demo-merchant' 12500 'UPI' 'SUCCESS' 'demo-success-001'
Write-Host "Payment $($success.id): $($success.status), provider $($success.providerCode), replay=$($replay.idempotentReplay)"

Write-Host 'Simulating a retryable provider failure and route failover...'
$retry = New-Payment 'demo-merchant' 25000 'CARD' 'TRANSIENT_FAILURE' 'demo-retry-001'
Write-Host "Payment $($retry.id): $($retry.status), attempts=$($retry.attempts.Count), provider $($retry.providerCode)"

Write-Host 'Simulating a timeout and resolving it with a signed provider webhook...'
$timeout = New-Payment 'demo-merchant' 8500 'UPI' 'TIMEOUT' 'demo-timeout-001'
$webhookBody = ConvertTo-Json -Compress -Depth 8 -InputObject @{ eventId = 'demo-event-timeout-001'; providerReference = $timeout.providerReference; eventType = 'payment.succeeded' }
$hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($webhookSecret))
try { $signature = [BitConverter]::ToString($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($webhookBody))).Replace('-', '').ToLowerInvariant() }
finally { $hmac.Dispose() }
$resolved = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/webhooks/$($timeout.providerCode)" -Headers @{ 'X-Signature' = $signature } -ContentType 'application/json' -Body $webhookBody
Write-Host "Webhook result: $($resolved.outcome), payment status $($resolved.paymentStatus)"

Write-Host 'Creating and replaying a partial refund...'
$refundHeaders = @{ 'Idempotency-Key' = 'demo-refund-001' }
$refund = Invoke-Api 'Post' "/api/v1/payments/$($retry.id)/refunds" @{ amountMinor = 5000 } $refundHeaders
$refundReplay = Invoke-Api 'Post' "/api/v1/payments/$($retry.id)/refunds" @{ amountMinor = 5000 } $refundHeaders
Write-Host "Refund $($refund.id): $($refund.amountMinor) paise, replay=$($refundReplay.idempotentReplay)"

Write-Host 'Reconciling a provider report with one provider-only transaction...'
$report = @{ providerCode = $success.providerCode; rows = @(
    @{ providerReference = $success.providerReference; amountMinor = $success.amountMinor; feeMinor = $success.providerFeeMinor },
    @{ providerReference = 'sim_orphan_demo_001'; amountMinor = 10000; feeMinor = 24 }
) }
$run = Invoke-Api 'Post' '/api/v1/reconciliation/runs' $report
Write-Host "Reconciliation $($run.id): $($run.status), matched=$($run.matchedCount), discrepancies=$($run.discrepancyCount)"

Start-Sleep -Seconds 4
$deliveries = Invoke-Api 'Get' '/api/v1/webhook-deliveries' $null
Write-Host 'Simulated webhook delivery attempts:'
$deliveries | Select-Object -First 5 status, attemptCount, maxAttempts, lastError

$overview = Invoke-Api 'Get' '/api/v1/analytics/overview' $null
Write-Host 'Current measured analytics:'
$overview | ConvertTo-Json -Depth 8
