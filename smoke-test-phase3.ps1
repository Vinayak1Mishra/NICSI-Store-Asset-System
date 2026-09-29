$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "NICSI ERP 2.0 - Phase 3 Live End-to-End Smoke Test" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Obtain Tokens
Write-Host "`n1. Obtaining Dev JWT Tokens..." -ForegroundColor Yellow
$operatorResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/dev/token?username=store.operator"
$operatorToken = $operatorResp.token
Write-Host "   store.operator token acquired: $($operatorResp.user.displayName)" -ForegroundColor Green

$managerResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/dev/token?username=store.manager"
$managerToken = $managerResp.token
Write-Host "   store.manager token acquired: $($managerResp.user.displayName)" -ForegroundColor Green

$adminResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/dev/token?username=admin"
$adminToken = $adminResp.token
Write-Host "   admin token acquired: $($adminResp.user.displayName)" -ForegroundColor Green

# 2. Lookup Masters
Write-Host "`n2. Looking up Item and Store Site..." -ForegroundColor Yellow
$itemsResp = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/items?size=10" -Headers @{ Authorization = "Bearer $adminToken" }
$testItem = $itemsResp.content[0]
Write-Host "   Target Item: $($testItem.itemCode) - $($testItem.itemName) (ID: $($testItem.id))" -ForegroundColor Green

$storesResp = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/stores?size=10" -Headers @{ Authorization = "Bearer $adminToken" }
$testStore = $storesResp.content[0]
Write-Host "   Target Store: $($testStore.storeCode) - $($testStore.storeName) (ID: $($testStore.id))" -ForegroundColor Green

$locsResp = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/locations?storeId=$($testStore.id)&size=10" -Headers @{ Authorization = "Bearer $adminToken" }
$testLoc = $locsResp.content[0]
Write-Host "   Target Location: $($testLoc.locationCode) (ID: $($testLoc.id))" -ForegroundColor Green

# 3. Create Purchase Order Reference
Write-Host "`n3. Creating Purchase Order Reference..." -ForegroundColor Yellow
$poNumber = "PO-GEM-" + [System.DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$poBody = @{
    sourceSystem = "NICSI_ERP"
    poNumber = $poNumber
    poDate = (Get-Date -Format "yyyy-MM-dd")
    procurementMode = "GEM"
    gemOrderNumber = "GEMC-2026-99"
    vendorNameSnapshot = "Dell International Services India"
    currencyCode = "INR"
    items = @(
        @{
            poLineNo = 1
            itemId = $testItem.id
            itemDescription = $testItem.itemName
            orderedQty = 15.000
            unitRate = 55000.00
            taxAmount = 99000.00
            deliveryDueDate = (Get-Date).AddMonths(1).ToString("yyyy-MM-dd")
            projectNameSnapshot = "NICSI Cloud ERP"
        }
    )
} | ConvertTo-Json -Depth 5

$poResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/purchase-orders" -Headers @{
    Authorization = "Bearer $adminToken"
    "Content-Type" = "application/json"
} -Body $poBody
$poId = $poResp.id
$poItemId = $poResp.items[0].id
Write-Host "   Purchase Order created: $($poResp.poNumber) (ID: $poId)" -ForegroundColor Green

# 4. Create GRN Draft by store.operator (Receiver)
Write-Host "`n4. Creating GRN Draft by store.operator..." -ForegroundColor Yellow
$grnBody = @{
    grnDate = (Get-Date -Format "yyyy-MM-dd")
    storeId = $testStore.id
    poRefId = $poId
    vendorNameSnapshot = "Dell International Services India"
    challanNumber = "DC-NICSI-2026-01"
    challanDate = (Get-Date -Format "yyyy-MM-dd")
    invoiceNumber = "INV-2026-8812"
    invoiceDate = (Get-Date -Format "yyyy-MM-dd")
    remarks = "Consignment received at gate entry"
    items = @(
        @{
            poItemRefId = $poItemId
            itemId = $testItem.id
            receivedQty = 10.000
            unitRate = 55000.00
            receivingLocationId = $testLoc.id
            batchLotNo = "LOT-2026-OCT"
            remarks = "10 units received in good packing"
        }
    )
} | ConvertTo-Json -Depth 5

$grnResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/grns" -Headers @{
    Authorization = "Bearer $operatorToken"
    "Content-Type" = "application/json"
} -Body $grnBody
$grnId = $grnResp.id
Write-Host "   GRN Draft created: $($grnResp.grnNo) (Status: $($grnResp.status))" -ForegroundColor Green

# 5. Submit GRN for Inspection
Write-Host "`n5. Submitting GRN for Inspection..." -ForegroundColor Yellow
$submitResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/grns/$grnId/submit" -Headers @{
    Authorization = "Bearer $operatorToken"
}
Write-Host "   GRN Submitted! New Status: $($submitResp.status)" -ForegroundColor Green

# 6. Retrieve Generated Inspection
Write-Host "`n6. Retrieving Inspection for GRN..." -ForegroundColor Yellow
$insResp = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/inspections/by-grn/$grnId" -Headers @{
    Authorization = "Bearer $operatorToken"
}
$insId = $insResp.id
$insItemId = $insResp.items[0].id
$insVersion = $insResp.version
Write-Host "   Inspection Record: $($insResp.inspectionNo) (Status: $($insResp.status), Items: $($insResp.items.Count))" -ForegroundColor Green

# 7. Maker-Checker Test: Receiver (store.operator) attempts to approve inspection
Write-Host "`n7. Testing Maker-Checker Guard Violation..." -ForegroundColor Yellow
$decideBody = @{
    overallRemarks = "Self-inspection attempt"
    version = $insVersion
    items = @(
        @{
            inspectionItemId = $insItemId
            acceptedQty = 10.000
            rejectedQty = 0.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            warrantyVerified = $true
            accessoryVerified = $true
            remarks = "All verified"
        }
    )
} | ConvertTo-Json -Depth 5

$blocked = $false
try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/inspections/$insId/decide" -Headers @{
        Authorization = "Bearer $operatorToken"
        "Content-Type" = "application/json"
    } -Body $decideBody
} catch {
    $blocked = $true
    Write-Host "   [SUCCESS] Self-inspection blocked by security/governance guard! Status Code: $($_.Exception.Response.StatusCode)" -ForegroundColor Green
}
if (-not $blocked) {
    throw "Security Failure: Receiver was able to inspect their own GRN!"
}

# 8. Store Manager conducts and records technical inspection decision
Write-Host "`n8. Store Manager records technical inspection decision (8 Accepted, 2 Rejected)..." -ForegroundColor Yellow
$managerDecideBody = @{
    overallRemarks = "Technical inspection conducted. 8 units passed diagnostics, 2 units rejected with chassis damage."
    version = $insVersion
    items = @(
        @{
            inspectionItemId = $insItemId
            acceptedQty = 8.000
            rejectedQty = 2.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            warrantyVerified = $true
            accessoryVerified = $true
            technicalResult = '{"diagnostics":"PASS","tested_by":"store.manager"}'
            remarks = "8 accepted, 2 rejected"
        }
    )
} | ConvertTo-Json -Depth 5

$finalInsResp = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/store/inspections/$insId/decide" -Headers @{
    Authorization = "Bearer $managerToken"
    "Content-Type" = "application/json"
} -Body $managerDecideBody
Write-Host "   Inspection Decision Recorded: $($finalInsResp.status)" -ForegroundColor Green
Write-Host "   Accepted Qty: $($finalInsResp.items[0].acceptedQty), Rejected Qty: $($finalInsResp.items[0].rejectedQty)" -ForegroundColor Green

# 9. Verify Final GRN and PO State
Write-Host "`n9. Verifying final GRN & PO state..." -ForegroundColor Yellow
$finalGrn = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/grns/$grnId" -Headers @{
    Authorization = "Bearer $adminToken"
}
Write-Host "   Final GRN Status: $($finalGrn.status) (Accepted: $($finalGrn.items[0].acceptedQty), Rejected: $($finalGrn.items[0].rejectedQty))" -ForegroundColor Green

$finalPo = Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/store/purchase-orders/$poId" -Headers @{
    Authorization = "Bearer $adminToken"
}
Write-Host "   Final PO Item: Ordered=$($finalPo.items[0].orderedQty), Received=$($finalPo.items[0].receivedQty), Remaining=$($finalPo.items[0].remainingQty)" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "ALL PHASE 3 LIVE SMOKE CHECKS PASSED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
