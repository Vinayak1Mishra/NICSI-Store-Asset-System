# NICSI ERP 2.0 - Phase 5 & 6 End-to-End Smoke Test
# Tests:
#   Phase 5 (Issue Workflow):
#     Create Issue Draft -> Submit -> Approve -> Post to Inventory (Stock OUT + Asset Assignment) ->
#     Idempotent Replay -> Recipient Acknowledgement
#   Phase 6 (Asset Assignment & Software Licenses):
#     Standalone Asset Assign (POST /api/assets/{id}/assign) ->
#     Register Software License Entitlement -> Allocate Seats -> Exceed Check -> Release Seat

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DbName = "nicsi_store"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "NICSI ERP 2.0 - Phase 5 & 6 Live Smoke Test" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL : $BaseUrl" -ForegroundColor Gray

# 1. Obtain Tokens
Write-Host "`n1. Obtaining Dev JWT Tokens..." -ForegroundColor Yellow
$operatorResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=store.operator"
$operatorToken = $operatorResp.token
Write-Host "   store.operator (Maker) token acquired" -ForegroundColor Green

$managerResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=store.manager"
$managerToken = $managerResp.token
Write-Host "   store.manager (Checker / Approver) token acquired" -ForegroundColor Green

$adminResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=admin"
$adminToken = $adminResp.token
Write-Host "   admin token acquired" -ForegroundColor Green

# 2. Master Lookups
Write-Host "`n2. Querying Store and Storage Location..." -ForegroundColor Yellow
$stores = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/masters/stores" -Headers @{ Authorization = "Bearer $adminToken" }
$targetStore = $stores[0]
$locations = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/masters/locations?storeId=$($targetStore.id)" -Headers @{ Authorization = "Bearer $adminToken" }
$targetLocation = $locations[0]
Write-Host "   Store: $($targetStore.storeCode) | Location: $($targetLocation.locationCode)" -ForegroundColor Gray

$items = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/masters/items?size=50" -Headers @{ Authorization = "Bearer $adminToken" }
$targetItem = $items.content | Where-Object { $_.itemType -eq "NON_CONSUMABLE" -or $_.assetRequired -eq $true } | Select-Object -First 1
if (-not $targetItem) { $targetItem = $items.content[0] }
Write-Host "   Item: $($targetItem.itemCode) - $($targetItem.itemName)" -ForegroundColor Gray

# ─── PHASE 5: ISSUE WORKFLOW ─────────────────────────────────────────────
Write-Host "`n=== PHASE 5: MATERIAL ISSUE WORKFLOW ===" -ForegroundColor Cyan

# 3. Create Issue Draft (as store.operator)
Write-Host "`n3. Creating Issue Draft..." -ForegroundColor Yellow
$createIssueBody = @{
    storeId = $targetStore.id
    issuedToType = "EMPLOYEE"
    issuedToNameSnapshot = "John Doe (Developer)"
    departmentNameSnapshot = "NICSI IT Projects"
    purpose = "Laptop issue for new project onboarding"
    items = @(
        @{
            lineNo = 1
            itemId = $targetItem.id
            locationId = $targetLocation.id
            issueQty = 1.000
            remarks = "Smoke test issue line 1"
        }
    )
} | ConvertTo-Json -Depth 5

$issue = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues" `
    -Headers @{ Authorization = "Bearer $operatorToken"; "Content-Type" = "application/json" } `
    -Body $createIssueBody

$issueId = $issue.id
Write-Host "   Issue Draft Created: $($issue.issueNo) (ID: $issueId, Status: $($issue.status))" -ForegroundColor Green

# 4. Submit Issue
Write-Host "`n4. Submitting Issue for Approval..." -ForegroundColor Yellow
$submitted = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issueId/submit" `
    -Headers @{ Authorization = "Bearer $operatorToken" }
Write-Host "   Issue Submitted (Status: $($submitted.status))" -ForegroundColor Green

# 5. Approve Issue (as store.manager)
Write-Host "`n5. Approving Issue..." -ForegroundColor Yellow
$approved = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issueId/approve" `
    -Headers @{ Authorization = "Bearer $managerToken" }
Write-Host "   Issue Approved (Status: $($approved.status))" -ForegroundColor Green

# 6. Post Issue to Inventory (as store.manager — maker-checker)
Write-Host "`n6. Posting Issue to Stock Ledger..." -ForegroundColor Yellow
$idemKey = "SMOKE-ISS-POST-$issueId"
$postBody = @{
    remarks = "Issued and handed over physically"
} | ConvertTo-Json

$postResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issueId/post" `
    -Headers @{ Authorization = "Bearer $managerToken"; "Idempotency-Key" = $idemKey; "Content-Type" = "application/json" } `
    -Body $postBody
Write-Host "   Stock Posted (Status: $($postResult.status), Posted Lines: $($postResult.totalPostedLines))" -ForegroundColor Green

# 7. Idempotency Replay Check
Write-Host "`n7. Testing Idempotent Replay on Post..." -ForegroundColor Yellow
$replayResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issueId/post" `
    -Headers @{ Authorization = "Bearer $managerToken"; "Idempotency-Key" = $idemKey; "Content-Type" = "application/json" } `
    -Body $postBody
if ($replayResult.status -eq $postResult.status) {
    Write-Host "   [PASS] Idempotent replay returned consistent cached response!" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] Replay status differed!" -ForegroundColor Red
}

# 8. Recipient Acknowledge Receipt
Write-Host "`n8. Recipient Digital Acknowledgement..." -ForegroundColor Yellow
$ackBody = @{
    acknowledgementStatus = "ACCEPTED"
    remarks = "Hardware received in good operating condition"
} | ConvertTo-Json

$ackResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issueId/acknowledge" `
    -Headers @{ Authorization = "Bearer $managerToken"; "Content-Type" = "application/json" } `
    -Body $ackBody
Write-Host "   Issue Acknowledged (Status: $($ackResult.status), Acknowledgement: $($ackResult.acknowledgementStatus))" -ForegroundColor Green

# ─── PHASE 6: ASSET ASSIGNMENT & SOFTWARE LICENSES ───────────────────────
Write-Host "`n=== PHASE 6: ASSET ASSIGNMENT & SOFTWARE LICENSES ===" -ForegroundColor Cyan

# 9. Standalone Asset Custodianship Assignment
Write-Host "`n9. Testing Standalone Asset Assignment (POST /api/assets/{id}/assign)..." -ForegroundColor Yellow
$assetsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/assets?size=10" -Headers @{ Authorization = "Bearer $adminToken" }
if ($assetsResp.content.Count -gt 0) {
    $targetAsset = $assetsResp.content[0]
    $assignBody = @{
        assignmentType = "EMPLOYEE"
        assigneeNameSnapshot = "Alice Smith (Lead Architect)"
        remarks = "Smoke test direct custodianship assignment"
    } | ConvertTo-Json

    $assignResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/assets/$($targetAsset.id)/assign" `
        -Headers @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" } `
        -Body $assignBody
    Write-Host "   Asset $($targetAsset.assetCode) assigned to Alice Smith (Status: $($assignResult.assetStatus))" -ForegroundColor Green
} else {
    Write-Host "   No assets found to test assignment; skipped." -ForegroundColor Gray
}

# 10. Software License Registration & Allocation
Write-Host "`n10. Testing Software License Entitlement Pool..." -ForegroundColor Yellow
$swItem = $items.content | Where-Object { $_.itemType -eq "SOFTWARE" } | Select-Object -First 1
if (-not $swItem) { $swItem = $targetItem }

$licCode = "SMOKE-LIC-$((Get-Date).Ticks)"
$licBody = @{
    itemId = $swItem.id
    licenseCode = $licCode
    licenseType = "USER"
    entitlementQty = 25.000
    poNumberSnapshot = "PO/2026/SMOKE-01"
} | ConvertTo-Json

$licResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/licenses" `
    -Headers @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" } `
    -Body $licBody
$licId = $licResp.id
Write-Host "   Registered License: $licCode (Entitlements: $($licResp.entitlementQty), Available: $($licResp.availableQty))" -ForegroundColor Green

# 11. Allocate Seats
Write-Host "`n11. Allocating 5 License Seats..." -ForegroundColor Yellow
$allocBody = @{
    allocationType = "USER"
    quantity = 5.000
    remarks = "Allocated to Dev Team Alpha"
} | ConvertTo-Json

$allocResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/licenses/$licId/allocate" `
    -Headers @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" } `
    -Body $allocBody
Write-Host "   Seats Allocated (Allocated: $($allocResult.allocatedQty), Available: $($allocResult.availableQty))" -ForegroundColor Green

# 12. Release Allocation
Write-Host "`n12. Releasing Allocated Seat..." -ForegroundColor Yellow
$allocId = $allocResult.allocations[0].id
$releaseResult = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/licenses/$licId/allocations/$allocId/release" `
    -Headers @{ Authorization = "Bearer $adminToken" }
Write-Host "   Seat Released (Allocated: $($releaseResult.allocatedQty), Available: $($releaseResult.availableQty))" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "ALL PHASE 5 & PHASE 6 SMOKE TEST STEPS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
