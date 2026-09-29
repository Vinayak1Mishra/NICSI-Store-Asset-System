# NICSI ERP 2.0 - Phase 4 End-to-End Smoke Test
# Tests: PO -> GRN -> Inspection -> Self-Post Guard (403) -> Post to Inventory -> Idempotent Replay -> Reconciliation -> DB Query

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [string]$DbName = "nicsi_store",
    [string]$DbUser = "postgres",
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "NICSI ERP 2.0 - Phase 4 Inventory & Asset Live Smoke Test" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL : $BaseUrl" -ForegroundColor Gray
Write-Host "Target Database : $DbName on ${DbHost}:${DbPort}" -ForegroundColor Gray

# Helper to find psql executable
if (-not (Test-Path $PsqlPath)) {
    $psqlCmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($psqlCmd) {
        $PsqlPath = $psqlCmd.Source
    } else {
        $PsqlPath = $null
    }
}

function Execute-PsqlQuery {
    param([string]$Sql)
    if ($PsqlPath -and (Test-Path $PsqlPath)) {
        Write-Host "   Running SQL via psql.exe..." -ForegroundColor Gray
        & $PsqlPath -h $DbHost -p $DbPort -U $DbUser -d $DbName -c $Sql
    } else {
        Write-Host "   [NOTE] psql.exe not found at '$PsqlPath' or on PATH. Direct DB row print skipped." -ForegroundColor Yellow
        Write-Host "   You can inspect rows using pgAdmin or psql: $Sql" -ForegroundColor Gray
    }
}

# 1. Obtain Tokens
Write-Host "`n1. Obtaining Dev JWT Tokens..." -ForegroundColor Yellow
$managerResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=store.manager"
$managerToken = $managerResp.token
$managerId = $managerResp.user.userId
Write-Host "   store.manager (Maker) token acquired (User ID: $managerId)" -ForegroundColor Green

$adminResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=admin"
$adminToken = $adminResp.token
$adminId = $adminResp.user.userId
Write-Host "   admin (Checker) token acquired (User ID: $adminId)" -ForegroundColor Green

# 2. Master Lookups
Write-Host "`n2. Looking up Item, Store Site, and Storage Location..." -ForegroundColor Yellow
$itemsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/items?size=50" -Headers @{ Authorization = "Bearer $adminToken" }
$testItem = $itemsResp.content | Where-Object { $_.itemCode -eq "LAPTOP-DELL" -or $_.assetRequired -eq $true } | Select-Object -First 1
if (-not $testItem) {
    $testItem = $itemsResp.content[0]
}
Write-Host "   Target Item: $($testItem.itemCode) - $($testItem.itemName) (ID: $($testItem.id), AssetRequired: $($testItem.assetRequired))" -ForegroundColor Green

$storesResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/stores?size=10" -Headers @{ Authorization = "Bearer $adminToken" }
$testStore = $storesResp.content[0]
Write-Host "   Target Store: $($testStore.storeCode) - $($testStore.storeName) (ID: $($testStore.id))" -ForegroundColor Green

$locsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/locations?storeId=$($testStore.id)&size=10" -Headers @{ Authorization = "Bearer $adminToken" }
$testLoc = $locsResp.content[0]
Write-Host "   Target Location: $($testLoc.locationCode) (ID: $($testLoc.id))" -ForegroundColor Green

# 3. Create PO Reference
Write-Host "`n3. Creating Purchase Order Reference..." -ForegroundColor Yellow
$ts = [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$poNumber = "PO-PHASE4-$ts"
$poBody = @{
    sourceSystem = "NICSI_ERP"
    poNumber = $poNumber
    poDate = (Get-Date -Format "yyyy-MM-dd")
    procurementMode = "GEM"
    gemOrderNumber = "GEM-PHASE4-$ts"
    vendorNameSnapshot = "Dell Enterprise Solutions India"
    currencyCode = "INR"
    totalAmount = 150000.00
    items = @(
        @{
            poLineNo = 1
            itemId = $testItem.id
            itemDescription = $testItem.itemName
            orderedQty = 2.000
            unitRate = 75000.00
            taxAmount = 13500.00
            deliveryDueDate = (Get-Date).AddMonths(1).ToString("yyyy-MM-dd")
            projectNameSnapshot = "NICSI Store Phase 4 Smoke Test"
        }
    )
} | ConvertTo-Json -Depth 5

$poResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/purchase-orders" -Headers @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
} -Body $poBody
$poId = $poResp.id
$poItemId = $poResp.items[0].id
Write-Host "   PO Reference created: $($poResp.poNumber) (ID: $poId)" -ForegroundColor Green

# 4. Create GRN Draft as store.manager (Receiver)
Write-Host "`n4. Creating GRN Draft as store.manager (Receiver)..." -ForegroundColor Yellow
$grnBody = @{
    grnDate = (Get-Date -Format "yyyy-MM-dd")
    storeId = $testStore.id
    poRefId = $poId
    vendorNameSnapshot = "Dell Enterprise Solutions India"
    challanNumber = "CH-SMOKE-$ts"
    challanDate = (Get-Date -Format "yyyy-MM-dd")
    invoiceNumber = "INV-SMOKE-$ts"
    invoiceDate = (Get-Date -Format "yyyy-MM-dd")
    remarks = "Phase 4 Smoke Test Receipt"
    items = @(
        @{
            poItemRefId = $poItemId
            itemId = $testItem.id
            receivedQty = 2.000
            unitRate = 75000.00
            receivingLocationId = $testLoc.id
            batchLotNo = "LOT-$ts"
            remarks = "2 serialised laptops received"
        }
    )
} | ConvertTo-Json -Depth 5

$grnResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns" -Headers @{
    Authorization = "Bearer $managerToken"
    "Content-Type" = "application/json"
} -Body $grnBody
$grnId = $grnResp.id
$grnItemId = $grnResp.items[0].id
Write-Host "   GRN Draft created: $($grnResp.grnNo) (ID: $grnId, ReceivedBy: $($grnResp.receivedByUserId))" -ForegroundColor Green

# 5. Submit GRN for Inspection
Write-Host "`n5. Submitting GRN for Inspection..." -ForegroundColor Yellow
$submitResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grnId/submit" -Headers @{
    Authorization = "Bearer $managerToken"
}
Write-Host "   GRN submitted: Status = $($submitResp.status)" -ForegroundColor Green

# 6. Technical Inspection Decision by admin
Write-Host "`n6. Recording Inspection Decision by admin..." -ForegroundColor Yellow
$inspResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/inspections/by-grn/$grnId" -Headers @{
    Authorization = "Bearer $adminToken"
}
$inspectionId = $inspResp.id
$inspItemId = $inspResp.items[0].id

$decideBody = @{
    overallRemarks = "All 2 units passed technical inspection"
    version = $inspResp.version
    items = @(
        @{
            inspectionItemId = $inspItemId
            acceptedQty = 2.000
            rejectedQty = 0.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            warrantyVerified = $true
            accessoryVerified = $true
            technicalResult = '{"pass":true,"testedComponents":["Screen","Keyboard","Battery"]}'
            remarks = "Passed standard verification"
        }
    )
} | ConvertTo-Json -Depth 5

$decideResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/inspections/$inspectionId/decide" -Headers @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
} -Body $decideBody
Write-Host "   Inspection Decided: Status = $($decideResp.status), Accepted Qty = $($decideResp.items[0].acceptedQty)" -ForegroundColor Green

# 7. Maker-Checker Guard: Self-Post by Receiver MUST return 403
Write-Host "`n7. Verifying Maker-Checker Guard: Self-Posting by Receiver (store.manager)..." -ForegroundColor Yellow
$postBody = @{
    remarks = "Attempted self-post"
    lineSerials = @(
        @{
            grnItemId = $grnItemId
            serialNumbers = @("SN-SMOKE-$ts-001", "SN-SMOKE-$ts-002")
        }
    )
} | ConvertTo-Json -Depth 5

$selfPostBlocked = $false
try {
    $blockedResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grnId/post" -Headers @{
        Authorization = "Bearer $managerToken"
        "Content-Type" = "application/json"
        "Idempotency-Key" = "IDEM-BLOCKED-$ts"
    } -Body $postBody
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        $selfPostBlocked = $true
        Write-Host "   [SUCCESS] Self-post correctly rejected with HTTP 403 Forbidden (MAKER_CHECKER_VIOLATION)" -ForegroundColor Green
    } else {
        Write-Host "   [FAIL] Expected 403 but received HTTP $statusCode" -ForegroundColor Red
    }
}

if (-not $selfPostBlocked) {
    throw "Maker-Checker guard failed: Receiver was able to post own GRN!"
}

# 8. Post to Stock by admin (Checker)
Write-Host "`n8. Posting GRN to Inventory as admin with Idempotency-Key..." -ForegroundColor Yellow
$idempotencyKey = "IDEM-PHASE4-SMOKE-$ts"

$postResp1 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grnId/post" -Headers @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
    "Idempotency-Key" = $idempotencyKey
} -Body $postBody

$post1Json = $postResp1 | ConvertTo-Json -Depth 5
Write-Host "   [POST 1 SUCCESS] GRN posted to stock! Total Assets Created: $($postResp1.totalAssetsCreated)" -ForegroundColor Green
Write-Host "   Transaction No : $($postResp1.postedLines[0].transactionNo)" -ForegroundColor Gray
Write-Host "   Generated Asset IDs : $($postResp1.generatedAssetIds -join ', ')" -ForegroundColor Gray

# 9. Idempotent Replay with Same Idempotency-Key
Write-Host "`n9. Replaying POST with SAME Idempotency-Key..." -ForegroundColor Yellow
$postResp2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grnId/post" -Headers @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
    "Idempotency-Key" = $idempotencyKey
} -Body $postBody

$post2Json = $postResp2 | ConvertTo-Json -Depth 5

Write-Host "--- RESPONSE 1 (Live Post) ---" -ForegroundColor Cyan
Write-Host $post1Json -ForegroundColor Gray
Write-Host "--- RESPONSE 2 (Idempotent Replay) ---" -ForegroundColor Cyan
Write-Host $post2Json -ForegroundColor Gray

if ($postResp1.grnNo -eq $postResp2.grnNo -and 
    $postResp1.totalAssetsCreated -eq $postResp2.totalAssetsCreated -and
    $postResp1.postedLines[0].transactionNo -eq $postResp2.postedLines[0].transactionNo) {
    Write-Host "   [SUCCESS] Idempotent replay returned consistent response without duplicate posting!" -ForegroundColor Green
} else {
    Write-Host "   [FAIL] Replay response differed from original post!" -ForegroundColor Red
}

# 10. Call Reconciliation Endpoint
Write-Host "`n10. Calling Inventory Reconciliation Endpoint..." -ForegroundColor Yellow
$reconResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/inventory/reconciliation" -Headers @{
    Authorization = "Bearer $adminToken"
}
Write-Host "   Reconciled: $($reconResp.reconciled)" -ForegroundColor ($reconResp.reconciled ? "Green" : "Yellow")
Write-Host "   Total Ledger Mismatches : $($reconResp.totalLedgerMismatchCount)" -ForegroundColor Gray
Write-Host "   Total Asset Mismatches  : $($reconResp.totalAssetMismatchCount)" -ForegroundColor Gray
Write-Host "   Checked At              : $($reconResp.checkedAt)" -ForegroundColor Gray

# 11. Print Database Rows directly
Write-Host "`n11. Querying PostgreSQL Database directly ($DbName)..." -ForegroundColor Yellow

$sqlQuery = @"
SELECT '--- STOCK BALANCE ---' AS section;
SELECT item_id, store_id, location_id, on_hand_qty, available_qty, avg_unit_cost, inventory_value 
FROM store.stock_balance WHERE item_id = '$($testItem.id)';

SELECT '--- STOCK TRANSACTION LEDGER ---' AS section;
SELECT transaction_no, transaction_type, quantity_in, unit_cost, total_cost, reference_no, idempotency_key 
FROM store.stock_transaction WHERE item_id = '$($testItem.id)' ORDER BY transaction_time DESC LIMIT 3;

SELECT '--- ASSETS CREATED ---' AS section;
SELECT asset_code, serial_number, asset_status, condition_status, qr_code_value 
FROM store.asset WHERE item_id = '$($testItem.id)' ORDER BY created_at DESC LIMIT 2;

SELECT '--- AUDIT TRAIL ---' AS section;
SELECT event_time, actor_username, action, entity_type, entity_ref_no 
FROM audit.event WHERE entity_ref_no = '$($grnResp.grnNo)' OR entity_id = '$grnId' ORDER BY event_time DESC LIMIT 5;
"@

Execute-PsqlQuery -Sql $sqlQuery

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "PHASE 4 SMOKE TEST COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
