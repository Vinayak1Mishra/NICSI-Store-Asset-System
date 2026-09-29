# =============================================================================
# NICSI Store & Asset Management System
# Dev Transactions Seeding Script (REST API Driven)
# =============================================================================

param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function New-IdemKey {
    return [System.Guid]::NewGuid().ToString()
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "NICSI Store Dev Transactions Seeder" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Target Base URL: $BaseUrl" -ForegroundColor Gray

# -----------------------------------------------------------------------------
# 1. Obtain Dev JWT Tokens for Role-Based Maker/Checker Execution
# -----------------------------------------------------------------------------
Write-Host "`n1. Obtaining Dev JWT Tokens for Mock Users..." -ForegroundColor Yellow

$adminResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=admin"
$adminToken = $adminResp.token
$adminHeaders = @{ Authorization = "Bearer $adminToken"; "Content-Type" = "application/json" }

$managerResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=store.manager"
$managerToken = $managerResp.token
$managerHeaders = @{ Authorization = "Bearer $managerToken"; "Content-Type" = "application/json" }

$operatorResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=store.operator"
$operatorToken = $operatorResp.token
$operatorHeaders = @{ Authorization = "Bearer $operatorToken"; "Content-Type" = "application/json" }

$hodResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=hod.dept1"
$hodToken = $hodResp.token
$hodHeaders = @{ Authorization = "Bearer $hodToken"; "Content-Type" = "application/json" }

$empResp = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/dev/token?username=employee.john"
$empToken = $empResp.token
$empHeaders = @{ Authorization = "Bearer $empToken"; "Content-Type" = "application/json" }
$empUserId = $empResp.user.userId

Write-Host "   Acquired JWTs for: admin, store.manager, store.operator, hod.dept1, employee.john" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 2. Master Lookups
# -----------------------------------------------------------------------------
Write-Host "`n2. Querying Master Data..." -ForegroundColor Yellow

$storesResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/stores?size=20" -Headers $adminHeaders
$hqStore = $storesResp.content | Where-Object { $_.storeCode -eq "STORE-HQ-GEN" } | Select-Object -First 1
$itStore = $storesResp.content | Where-Object { $_.storeCode -eq "STORE-IT-MAIN" } | Select-Object -First 1

$hqLocsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/locations?storeId=$($hqStore.id)&size=20" -Headers $adminHeaders
$hqRack = $hqLocsResp.content | Where-Object { $_.locationCode -eq "HQ-RACK-A" } | Select-Object -First 1
$hqBin = $hqLocsResp.content | Where-Object { $_.locationCode -eq "HQ-BIN-A1" } | Select-Object -First 1

$itLocsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/locations?storeId=$($itStore.id)&size=20" -Headers $adminHeaders
$itRack = $itLocsResp.content | Where-Object { $_.locationCode -eq "IT-RACK-01" } | Select-Object -First 1
$itShelf = $itLocsResp.content | Where-Object { $_.locationCode -eq "IT-SHELF-01A" } | Select-Object -First 1

$itemsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/items?size=50" -Headers $adminHeaders
$itemDellLat = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-HW-DELL-LAT" } | Select-Object -First 1
$itemHpElite = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-HW-HP-ELITE" } | Select-Object -First 1
$itemCat6 = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-NET-CAT6-2M" } | Select-Object -First 1
$itemA4Paper = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-STAT-A4" } | Select-Object -First 1
$itemPens = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-STAT-PEN" } | Select-Object -First 1
$itemWin11 = $itemsResp.content | Where-Object { $_.itemCode -eq "ITEM-SW-WIN11PRO" } | Select-Object -First 1

$posResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/purchase-orders?size=20" -Headers $adminHeaders
$po1 = $posResp.content | Where-Object { $_.poNumber -eq "PO-NICSI-2026-001" } | Select-Object -First 1
$po2 = $posResp.content | Where-Object { $_.poNumber -eq "PO-NICSI-2026-002" } | Select-Object -First 1

Write-Host "   Master stores, locations, items, and purchase orders resolved." -ForegroundColor Green

# -----------------------------------------------------------------------------
# 3. GRN 1: Dell Laptops & Cat6 Cables (Partial & Quarantine Inspection Decision)
# -----------------------------------------------------------------------------
Write-Host "`n3. Processing GRN 1 (PO-NICSI-2026-001)..." -ForegroundColor Yellow

$po1Detail = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/purchase-orders/$($po1.id)" -Headers $adminHeaders
$po1LineDell = $po1Detail.items | Where-Object { $_.itemId -eq $itemDellLat.id } | Select-Object -First 1
$po1LineCat6 = $po1Detail.items | Where-Object { $_.itemId -eq $itemCat6.id } | Select-Object -First 1

$grn1Body = @{
    grnDate = (Get-Date -Format "yyyy-MM-dd")
    storeId = $itStore.id
    poRefId = $po1.id
    vendorNameSnapshot = $po1Detail.vendorNameSnapshot
    invoiceNumber = "INV-DELL-2026-101"
    invoiceDate = (Get-Date -Format "yyyy-MM-dd")
    challanNumber = "DC-DELL-2026-101"
    challanDate = (Get-Date -Format "yyyy-MM-dd")
    remarks = "Consignment received in IT Store loading bay"
    items = @(
        @{
            poItemRefId = $po1LineDell.id
            itemId = $itemDellLat.id
            receivedQty = 10.000
            unitRate = $po1LineDell.unitRate
            receivingLocationId = $itShelf.id
            batchLotNo = "LOT-DELL-2026-Q1"
            remarks = "10 boxes Dell Latitude 5440"
        },
        @{
            poItemRefId = $po1LineCat6.id
            itemId = $itemCat6.id
            receivedQty = 50.000
            unitRate = $po1LineCat6.unitRate
            receivingLocationId = $itRack.id
            batchLotNo = "LOT-CAT6-2026-Q1"
            remarks = "50 Cat6 patch cords"
        }
    )
} | ConvertTo-Json -Depth 5

$grn1 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns" -Headers $operatorHeaders -Body $grn1Body
$grn1Id = $grn1.id
Write-Host "   Created GRN Draft: $($grn1.grnNo) (ID: $grn1Id)" -ForegroundColor Green

$grn1Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grn1Id/submit" -Headers $operatorHeaders
Write-Host "   Submitted GRN: $($grn1Sub.grnNo) (Status: $($grn1Sub.status))" -ForegroundColor Green

# Inspect GRN 1: Dell 8 accepted + 2 quarantine; Cat6 40 accepted + 10 rejected
$insp1 = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/inspections/by-grn/$grn1Id" -Headers $managerHeaders
$insp1LineDell = $insp1.items | Where-Object { $_.itemId -eq $itemDellLat.id } | Select-Object -First 1
$insp1LineCat6 = $insp1.items | Where-Object { $_.itemId -eq $itemCat6.id } | Select-Object -First 1

$insp1DecideBody = @{
    overallRemarks = "Inspection completed: 8 Laptops accepted, 2 quarantined for screen diagnostics. 40 Cat6 accepted, 10 damaged/rejected."
    version = $insp1.version
    items = @(
        @{
            inspectionItemId = $insp1LineDell.id
            acceptedQty = 8.000
            rejectedQty = 0.000
            quarantineQty = 2.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            warrantyVerified = $true
            accessoryVerified = $true
            remarks = "8 units flawless, 2 units kept in quarantine for display checks"
        },
        @{
            inspectionItemId = $insp1LineCat6.id
            acceptedQty = 40.000
            rejectedQty = 10.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "PARTIAL"
            warrantyVerified = $true
            accessoryVerified = $true
            remarks = "40 passed continuity test, 10 cut/defective rejected"
        }
    )
} | ConvertTo-Json -Depth 5

$insp1Res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/inspections/$($insp1.id)/decide" -Headers $managerHeaders -Body $insp1DecideBody
Write-Host "   Recorded Inspection: $($insp1.inspectionNo) (Status: $($insp1Res.status))" -ForegroundColor Green

# Post GRN 1 to Stock (provide 8 serial numbers for 8 accepted Dell Laptops)
$grn1ItemDell = $grn1Sub.items | Where-Object { $_.itemId -eq $itemDellLat.id } | Select-Object -First 1
$grn1PostSerials = @(1..8 | ForEach-Object { "DELL-LAT-2026-$([string]::Format('{0:D3}', $_))" })

$grn1PostBody = @{
    remarks = "Stock posted to IT Depot. 8 assets created."
    lineSerials = @(
        @{
            grnItemId = $grn1ItemDell.id
            serialNumbers = $grn1PostSerials
        }
    )
} | ConvertTo-Json -Depth 5

$grn1PostHeaders = $managerHeaders.Clone()
$grn1PostHeaders["Idempotency-Key"] = New-IdemKey
$grn1PostRes = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grn1Id/post" -Headers $grn1PostHeaders -Body $grn1PostBody
Write-Host "   Posted GRN 1 to Stock: $($grn1PostRes.grnNo) | Created Assets: $($grn1PostRes.totalAssetsCreated)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 4. GRN 2: HP Mini PCs & A4 Paper Reams (All Accepted)
# -----------------------------------------------------------------------------
Write-Host "`n4. Processing GRN 2 (PO-NICSI-2026-002)..." -ForegroundColor Yellow

$po2Detail = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/purchase-orders/$($po2.id)" -Headers $adminHeaders
$po2LineHp = $po2Detail.items | Where-Object { $_.itemId -eq $itemHpElite.id } | Select-Object -First 1
$po2LinePaper = $po2Detail.items | Where-Object { $_.itemId -eq $itemA4Paper.id } | Select-Object -First 1

$grn2Body = @{
    grnDate = (Get-Date -Format "yyyy-MM-dd")
    storeId = $hqStore.id
    poRefId = $po2.id
    vendorNameSnapshot = $po2Detail.vendorNameSnapshot
    invoiceNumber = "INV-HP-2026-202"
    invoiceDate = (Get-Date -Format "yyyy-MM-dd")
    challanNumber = "DC-HP-2026-202"
    challanDate = (Get-Date -Format "yyyy-MM-dd")
    remarks = "Consignment received in HQ General Store"
    items = @(
        @{
            poItemRefId = $po2LineHp.id
            itemId = $itemHpElite.id
            receivedQty = 8.000
            unitRate = $po2LineHp.unitRate
            receivingLocationId = $hqRack.id
            batchLotNo = "LOT-HP-2026-Q1"
            remarks = "8 units HP Mini Workstations"
        },
        @{
            poItemRefId = $po2LinePaper.id
            itemId = $itemA4Paper.id
            receivedQty = 100.000
            unitRate = $po2LinePaper.unitRate
            receivingLocationId = $hqBin.id
            batchLotNo = "LOT-JK-2026-Q1"
            remarks = "100 reams A4 copier paper"
        }
    )
} | ConvertTo-Json -Depth 5

$grn2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns" -Headers $operatorHeaders -Body $grn2Body
$grn2Id = $grn2.id
Write-Host "   Created GRN Draft: $($grn2.grnNo) (ID: $grn2Id)" -ForegroundColor Green

$grn2Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grn2Id/submit" -Headers $operatorHeaders
Write-Host "   Submitted GRN: $($grn2Sub.grnNo) (Status: $($grn2Sub.status))" -ForegroundColor Green

$insp2 = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/store/inspections/by-grn/$grn2Id" -Headers $managerHeaders
$insp2LineHp = $insp2.items | Where-Object { $_.itemId -eq $itemHpElite.id } | Select-Object -First 1
$insp2LinePaper = $insp2.items | Where-Object { $_.itemId -eq $itemA4Paper.id } | Select-Object -First 1

$insp2DecideBody = @{
    overallRemarks = "All items verified against specifications. 100% accepted."
    version = $insp2.version
    items = @(
        @{
            inspectionItemId = $insp2LineHp.id
            acceptedQty = 8.000
            rejectedQty = 0.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            warrantyVerified = $true
            accessoryVerified = $true
            remarks = "8 units Mini PCs accepted"
        },
        @{
            inspectionItemId = $insp2LinePaper.id
            acceptedQty = 100.000
            rejectedQty = 0.000
            quarantineQty = 0.000
            specificationMatch = $true
            physicalCondition = "GOOD"
            remarks = "100 reams paper accepted"
        }
    )
} | ConvertTo-Json -Depth 5

$insp2Res = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/inspections/$($insp2.id)/decide" -Headers $managerHeaders -Body $insp2DecideBody
Write-Host "   Recorded Inspection: $($insp2.inspectionNo) (Status: $($insp2Res.status))" -ForegroundColor Green

$grn2ItemHp = $grn2Sub.items | Where-Object { $_.itemId -eq $itemHpElite.id } | Select-Object -First 1
$grn2PostSerials = @(1..8 | ForEach-Object { "HP-ELITE-2026-$([string]::Format('{0:D3}', $_))" })

$grn2PostBody = @{
    remarks = "Stock posted to HQ General Store. 8 Mini PC assets created."
    lineSerials = @(
        @{
            grnItemId = $grn2ItemHp.id
            serialNumbers = $grn2PostSerials
        }
    )
} | ConvertTo-Json -Depth 5

$grn2PostHeaders = $managerHeaders.Clone()
$grn2PostHeaders["Idempotency-Key"] = New-IdemKey
$grn2PostRes = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/grns/$grn2Id/post" -Headers $grn2PostHeaders -Body $grn2PostBody
Write-Host "   Posted GRN 2 to Stock: $($grn2PostRes.grnNo) | Created Assets: $($grn2PostRes.totalAssetsCreated)" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 5. Requisitions (Submit & Approve Flow)
# -----------------------------------------------------------------------------
Write-Host "`n5. Creating and Approving Requisitions..." -ForegroundColor Yellow

# Requisition 1 (by employee.john, approved by hod.dept1)
$req1Headers = $empHeaders.Clone()
$req1Headers["Idempotency-Key"] = New-IdemKey
$req1Body = @{
    purpose = "Developer workstation and paper supplies for NICSI Cloud Division"
    priority = "NORMAL"
    requiredByDate = (Get-Date).AddDays(7).ToString("yyyy-MM-dd")
    departmentNameSnapshot = "IT Department"
    projectNameSnapshot = "Project 1"
    items = @(
        @{
            itemId = $itemDellLat.id
            requestedQty = 1.000
            estimatedUnitRate = 78000.00
            justification = "Primary development notebook"
        },
        @{
            itemId = $itemA4Paper.id
            requestedQty = 2.000
            estimatedUnitRate = 250.00
            justification = "Documentation and diagram prints"
        }
    )
} | ConvertTo-Json -Depth 5

$req1 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions" -Headers $req1Headers -Body $req1Body
$req1Id = $req1.id
Write-Host "   Created Requisition 1: $($req1.requisitionNo) (ID: $req1Id)" -ForegroundColor Green

$req1Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions/$req1Id/submit" -Headers $empHeaders
Write-Host "   Submitted Requisition 1: $($req1Sub.requisitionNo) (Status: $($req1Sub.status))" -ForegroundColor Green

$req1ApproveHeaders = $hodHeaders.Clone()
$req1ApproveHeaders["Idempotency-Key"] = New-IdemKey
$req1ApproveBody = @{
    action = "APPROVE"
    comments = "Approved for IT Cloud development team"
} | ConvertTo-Json

$req1App = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions/$req1Id/approvals/decision" -Headers $req1ApproveHeaders -Body $req1ApproveBody
Write-Host "   Approved Requisition 1: $($req1App.requisitionNo) (Status: $($req1App.status))" -ForegroundColor Green

# Requisition 2 (by store.operator, approved by store.manager)
$req2Headers = $operatorHeaders.Clone()
$req2Headers["Idempotency-Key"] = New-IdemKey
$req2Body = @{
    purpose = "Network cabling and stationery maintenance stock"
    priority = "HIGH"
    requiredByDate = (Get-Date).AddDays(3).ToString("yyyy-MM-dd")
    departmentNameSnapshot = "Admin Department"
    projectNameSnapshot = "Project 2"
    items = @(
        @{
            itemId = $itemCat6.id
            requestedQty = 5.000
            estimatedUnitRate = 150.00
            justification = "Floor switch patch connections"
        },
        @{
            itemId = $itemPens.id
            requestedQty = 1.000
            estimatedUnitRate = 120.00
            justification = "Store office pens"
        }
    )
} | ConvertTo-Json -Depth 5

$req2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions" -Headers $req2Headers -Body $req2Body
$req2Id = $req2.id
Write-Host "   Created Requisition 2: $($req2.requisitionNo) (ID: $req2Id)" -ForegroundColor Green

$req2Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions/$req2Id/submit" -Headers $operatorHeaders
Write-Host "   Submitted Requisition 2: $($req2Sub.requisitionNo) (Status: $($req2Sub.status))" -ForegroundColor Green

$req2ApproveHeaders = $managerHeaders.Clone()
$req2ApproveHeaders["Idempotency-Key"] = New-IdemKey
$req2ApproveBody = @{
    action = "APPROVE"
    comments = "Approved for store operations"
} | ConvertTo-Json

$req2App = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/requisitions/$req2Id/approvals/decision" -Headers $req2ApproveHeaders -Body $req2ApproveBody
Write-Host "   Approved Requisition 2: $($req2App.requisitionNo) (Status: $($req2App.status))" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 6. Material Issues (Draft -> Submit -> Approve -> Post -> Acknowledge)
# -----------------------------------------------------------------------------
Write-Host "`n6. Creating and Posting Material Issues..." -ForegroundColor Yellow

# Issue 1: Dell Laptop + Cat6 Cables to John Employee
$issue1Body = @{
    storeId = $itStore.id
    issuedToType = "EMPLOYEE"
    issuedToUserId = $empUserId
    issuedToNameSnapshot = "John Employee"
    departmentNameSnapshot = "IT Department"
    projectNameSnapshot = "Project 1"
    purpose = "Developer asset issue against Requisition $($req1.requisitionNo)"
    items = @(
        @{
            lineNo = 1
            itemId = $itemDellLat.id
            locationId = $itShelf.id
            issueQty = 1.000
            remarks = "Dell Latitude 5440 Issued"
        },
        @{
            lineNo = 2
            itemId = $itemCat6.id
            locationId = $itRack.id
            issueQty = 5.000
            remarks = "5 Cat6 patch cords"
        }
    )
} | ConvertTo-Json -Depth 5

$issue1 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues" -Headers $operatorHeaders -Body $issue1Body
$issue1Id = $issue1.id
Write-Host "   Created Issue 1 Draft: $($issue1.issueNo) (ID: $issue1Id)" -ForegroundColor Green

$issue1Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue1Id/submit" -Headers $operatorHeaders
Write-Host "   Submitted Issue 1: $($issue1Sub.issueNo) (Status: $($issue1Sub.status))" -ForegroundColor Green

$issue1App = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue1Id/approve" -Headers $managerHeaders
Write-Host "   Approved Issue 1: $($issue1App.issueNo) (Status: $($issue1App.status))" -ForegroundColor Green

$issue1PostHeaders = $managerHeaders.Clone()
$issue1PostHeaders["Idempotency-Key"] = New-IdemKey
$issue1Post = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue1Id/post" -Headers $issue1PostHeaders -Body (@{ remarks = "Physical handover completed" } | ConvertTo-Json)
Write-Host "   Posted Issue 1 to Inventory: $($issue1Post.issueNo) (Status: $($issue1Post.status))" -ForegroundColor Green

$issue1AckBody = @{
    acknowledgementStatus = "ACCEPTED"
    remarks = "Received laptop and cables in working condition"
} | ConvertTo-Json
$issue1Ack = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue1Id/acknowledge" -Headers $empHeaders -Body $issue1AckBody
Write-Host "   Acknowledged Issue 1: $($issue1Ack.issueNo) (Status: $($issue1Ack.acknowledgementStatus))" -ForegroundColor Green

# Issue 2: HP Mini PC + A4 Paper to Store Officer
$issue2Body = @{
    storeId = $hqStore.id
    issuedToType = "EMPLOYEE"
    issuedToNameSnapshot = "Store Officer"
    departmentNameSnapshot = "Admin Department"
    purpose = "Store desk operations PC and paper supply"
    items = @(
        @{
            lineNo = 1
            itemId = $itemHpElite.id
            locationId = $hqRack.id
            issueQty = 1.000
            remarks = "HP EliteDesk Mini PC"
        },
        @{
            lineNo = 2
            itemId = $itemA4Paper.id
            locationId = $hqBin.id
            issueQty = 2.000
            remarks = "2 Reams A4 Paper"
        }
    )
} | ConvertTo-Json -Depth 5

$issue2 = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues" -Headers $operatorHeaders -Body $issue2Body
$issue2Id = $issue2.id
Write-Host "   Created Issue 2 Draft: $($issue2.issueNo) (ID: $issue2Id)" -ForegroundColor Green

$issue2Sub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue2Id/submit" -Headers $operatorHeaders
Write-Host "   Submitted Issue 2: $($issue2Sub.issueNo) (Status: $($issue2Sub.status))" -ForegroundColor Green

$issue2App = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue2Id/approve" -Headers $managerHeaders
Write-Host "   Approved Issue 2: $($issue2App.issueNo) (Status: $($issue2App.status))" -ForegroundColor Green

$issue2PostHeaders = $managerHeaders.Clone()
$issue2PostHeaders["Idempotency-Key"] = New-IdemKey
$issue2Post = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue2Id/post" -Headers $issue2PostHeaders -Body (@{ remarks = "Physical handover completed" } | ConvertTo-Json)
Write-Host "   Posted Issue 2 to Inventory: $($issue2Post.issueNo) (Status: $($issue2Post.status))" -ForegroundColor Green

$issue2AckBody = @{
    acknowledgementStatus = "ACCEPTED"
    remarks = "Received hardware in good working condition"
} | ConvertTo-Json
$issue2Ack = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/issues/$issue2Id/acknowledge" -Headers $managerHeaders -Body $issue2AckBody
Write-Host "   Acknowledged Issue 2: $($issue2Ack.issueNo) (Status: $($issue2Ack.acknowledgementStatus))" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 7. Material Return (Draft -> Submit -> Receive/Inspect -> Post)
# -----------------------------------------------------------------------------
Write-Host "`n7. Processing Material Return..." -ForegroundColor Yellow

$returnBody = @{
    storeId = $itStore.id
    returnDate = (Get-Date -Format "yyyy-MM-dd")
    returnedByUserId = $empUserId
    departmentId = [System.Guid]::Parse("2e2178eb-9474-32cc-ad9a-e16cffab6614") # dept-it
    remarks = "Surplus Cat6 cable returned from Project 1"
    items = @(
        @{
            itemId = $itemCat6.id
            returnQty = 1.000
            returnLocationId = $itRack.id
            conditionStatus = "GOOD"
            remarks = "Unused spare patch cord"
        }
    )
} | ConvertTo-Json -Depth 5

$ret = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/returns" -Headers $adminHeaders -Body $returnBody
$retId = $ret.id
Write-Host "   Created Return Draft: $($ret.returnNo) (ID: $retId)" -ForegroundColor Green

$retSub = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/returns/$retId/submit" -Headers $adminHeaders
Write-Host "   Submitted Return: $($retSub.returnNo) (Status: $($retSub.status))" -ForegroundColor Green

$retLine = $retSub.items[0]
$retReceiveBody = @{
    receivedByUserId = $managerResp.user.userId
    lines = @(
        @{
            lineId = $retLine.id
            conditionStatus = "GOOD"
            disposition = "RESTOCK"
            returnLocationId = $itRack.id
            remarks = "Inspected and approved for restock"
        }
    )
} | ConvertTo-Json -Depth 5

$retRec = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/returns/$retId/receive" -Headers $adminHeaders -Body $retReceiveBody
Write-Host "   Received & Inspected Return: $($retRec.returnNo) (Status: $($retRec.status))" -ForegroundColor Green

$retPostHeaders = $adminHeaders.Clone()
$retPostHeaders["Idempotency-Key"] = New-IdemKey
$retPost = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/returns/$retId/post" -Headers $retPostHeaders -Body (@{ remarks = "Restocked to inventory" } | ConvertTo-Json)
Write-Host "   Posted Return to Stock: $($retPost.returnNo) (Status: $($retPost.status))" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 8. Asset Repair Ticket
# -----------------------------------------------------------------------------
Write-Host "`n8. Creating Asset Repair Ticket..." -ForegroundColor Yellow

$assetsResp = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/assets?size=10" -Headers $adminHeaders
$targetAsset = $assetsResp.content | Where-Object { $_.itemCode -eq "ITEM-HW-DELL-LAT" } | Select-Object -First 1

$repairBody = @{
    assetId = $targetAsset.id
    complaintDetail = "Display flickering intermittently on external monitor connection"
    warrantyClaim = $true
    vendorNameSnapshot = "Dell Services India"
    expectedReturnDate = (Get-Date).AddDays(5).ToString("yyyy-MM-dd")
    remarks = "Raised under OEM onsite warranty coverage"
} | ConvertTo-Json

$repair = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/repairs" -Headers $adminHeaders -Body $repairBody
$repairId = $repair.id
Write-Host "   Created Repair Ticket: $($repair.repairNo) (ID: $repairId, Status: $($repair.status))" -ForegroundColor Green

$repairUpdateBody = @{
    status = "UNDER_REPAIR"
    diagnosis = "Display cable loose connection"
    repairAction = "Re-seated internal display connector and updated Intel Iris drivers"
    partsReplaced = "None"
    repairCost = 0.00
    vendorNameSnapshot = "Dell Services India"
} | ConvertTo-Json

$repairUp = Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/repairs/$repairId" -Headers $adminHeaders -Body $repairUpdateBody
Write-Host "   Updated Repair Ticket: $($repairUp.repairNo) (Status: $($repairUp.status))" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 9. Warranty Record
# -----------------------------------------------------------------------------
Write-Host "`n9. Creating Warranty Record..." -ForegroundColor Yellow

$warrantyBody = @{
    contractType = "WARRANTY"
    contractNumber = "WAR-DELL-2026-001"
    vendorNameSnapshot = "Dell International Services India"
    itemId = $itemDellLat.id
    assetId = $targetAsset.id
    startDate = (Get-Date -Format "yyyy-MM-dd")
    endDate = (Get-Date).AddYears(3).ToString("yyyy-MM-dd")
    coverageDetail = "3-Year Next Business Day Comprehensive Onsite Warranty"
    slaDetail = "24x7 Support Desk with 4-Hour Response Time"
    amount = 12500.00
    renewalReminderDays = 60
} | ConvertTo-Json

$warranty = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/warranties" -Headers $adminHeaders -Body $warrantyBody
Write-Host "   Created Warranty Record: $($warranty.contractNumber) (ID: $($warranty.id))" -ForegroundColor Green

# -----------------------------------------------------------------------------
# 10. Software License Registration & Allocation
# -----------------------------------------------------------------------------
Write-Host "`n10. Registering Software License & Seat Allocation..." -ForegroundColor Yellow

$licCode = "LIC-WIN11-DEV-$(Get-Random -Minimum 1000 -Maximum 9999)"
$licBody = @{
    itemId = $itemWin11.id
    licenseCode = $licCode
    licenseType = "USER"
    entitlementQty = 20.000
    startDate = (Get-Date -Format "yyyy-MM-dd")
    endDate = (Get-Date).AddYears(1).ToString("yyyy-MM-dd")
    poNumberSnapshot = "PO-NICSI-2026-001"
    licenseKeySecretRef = "vault://lic-keys/win11-volume-key"
} | ConvertTo-Json

$license = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/licenses" -Headers $adminHeaders -Body $licBody
$licId = $license.id
Write-Host "   Registered Software License: $($license.licenseCode) (Entitlements: $($license.entitlementQty))" -ForegroundColor Green

$allocBody = @{
    allocationType = "USER"
    userId = $empUserId
    quantity = 2.000
    remarks = "Allocated for Development Workstation"
} | ConvertTo-Json

$alloc = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/store/licenses/$licId/allocate" -Headers $adminHeaders -Body $allocBody
Write-Host "   Allocated License Seats: 2 Seats to John Employee (Available: $($alloc.availableQty))" -ForegroundColor Green

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "DEV TRANSACTIONS SEEDING COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
