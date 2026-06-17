param(
    [string] $BaseUrl = "http://localhost:8080",
    [string] $BuyerId = "00000000-0000-0000-0000-000000000001",
    [int] $StartSecond = 100,
    [int] $EndSecond = 110,
    [string] $EventId = "",
    [string] $PaymentReference = "",
    [string] $RequestId = "",
    [string] $EventType = "payment_intent.succeeded",
    [string] $PayloadHash = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($EventId)) {
    $EventId = "evt_local_${StartSecond}_${EndSecond}"
}
if ([string]::IsNullOrWhiteSpace($PaymentReference)) {
    $PaymentReference = "pi_local_${StartSecond}_${EndSecond}"
}
if ([string]::IsNullOrWhiteSpace($RequestId)) {
    $RequestId = "local-request-${StartSecond}-${EndSecond}"
}
if ([string]::IsNullOrWhiteSpace($PayloadHash)) {
    $PayloadHash = "sha256-local-${StartSecond}-${EndSecond}"
}

function Write-Step {
    param([string] $Message)
    Write-Host "[verify] $Message"
}

function Assert-True {
    param(
        [bool] $Condition,
        [string] $Message
    )
    if (-not $Condition) {
        throw $Message
    }
}

Write-Step "Using BaseUrl=$BaseUrl"
Write-Step "Using range [$StartSecond, $EndSecond)"

$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health"
Assert-True ($health.status -eq "UP") "Expected health UP, got $($health.status)"
Write-Step "Health check passed"

$availability = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/archive/availability?startSecond=$StartSecond&endSecond=$EndSecond"
Assert-True ($availability.available -eq $true) "Range is not available. Use -StartSecond and -EndSecond to choose another range."
Write-Step "Initial availability passed"

$reservationBody = @{
    buyerId = $BuyerId
    startSecond = $StartSecond
    endSecond = $EndSecond
} | ConvertTo-Json
$reservation = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/purchase/reservations" `
    -ContentType "application/json" `
    -Body $reservationBody
Assert-True (-not [string]::IsNullOrWhiteSpace($reservation.reservationId)) "Reservation ID was empty"
Write-Step "Reservation created: $($reservation.reservationId)"

$checkout = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/purchase/reservations/$($reservation.reservationId)/checkout"
Assert-True (-not [string]::IsNullOrWhiteSpace($checkout.checkoutUrl)) "Checkout URL was empty"
Write-Step "Checkout created: $($checkout.checkoutUrl)"

$webhookBody = @{
    providerEventId = $EventId
    eventType = $EventType
    payloadHash = $PayloadHash
    reservationId = $reservation.reservationId
    paymentReference = $PaymentReference
    requestId = $RequestId
} | ConvertTo-Json
$payment = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/internal/payments/fake/webhooks/primary-purchase-completed" `
    -ContentType "application/json" `
    -Body $webhookBody
Assert-True ($payment.alreadyProcessed -eq $false) "Expected first webhook to process payment"
Assert-True (-not [string]::IsNullOrWhiteSpace($payment.purchaseId)) "Purchase ID was empty"
Write-Step "Payment completed: $($payment.purchaseId)"

$duplicatePayment = Invoke-RestMethod `
    -Method Post `
    -Uri "$BaseUrl/api/internal/payments/fake/webhooks/primary-purchase-completed" `
    -ContentType "application/json" `
    -Body $webhookBody
Assert-True ($duplicatePayment.alreadyProcessed -eq $true) "Expected duplicate webhook to be alreadyProcessed=true"
Write-Step "Duplicate webhook idempotency passed"

$finalAvailability = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/archive/availability?startSecond=$StartSecond&endSecond=$EndSecond"
Assert-True ($finalAvailability.available -eq $false) "Expected completed range to be unavailable"
Write-Step "Final availability passed"

Write-Step "Local purchase flow verification passed"
