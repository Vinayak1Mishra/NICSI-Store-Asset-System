package com.nicsi.store.asset.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class AssetDto {

    public record Response(
            UUID id,
            String assetCode,
            UUID itemId,
            String itemCode,
            String itemName,
            UUID grnItemId,
            String serialNumber,
            String manufacturer,
            String modelNumber,
            String configuration,
            LocalDate purchaseDate,
            BigDecimal purchaseCost,
            String poNumberSnapshot,
            String invoiceNumberSnapshot,
            UUID storeId,
            String storeCode,
            String storeName,
            UUID locationId,
            String locationCode,
            String locationName,
            UUID currentCustodianUserId,
            UUID currentDepartmentId,
            UUID currentProjectId,
            String assetStatus,
            String conditionStatus,
            String qrCodeValue,
            String barcodeValue,
            LocalDate warrantyStartDate,
            LocalDate warrantyEndDate,
            String capitalizationRef,
            String remarks,
            Instant createdAt,
            Long version
    ) {}

    public record SummaryResponse(
            UUID id,
            String assetCode,
            String itemCode,
            String itemName,
            String serialNumber,
            String storeCode,
            String locationCode,
            String assetStatus,
            String conditionStatus,
            String qrCodeValue,
            LocalDate purchaseDate,
            BigDecimal purchaseCost
    ) {}

    public record AssignRequest(
            String assignmentType,
            UUID assigneeUserId,
            String assigneeNameSnapshot,
            UUID departmentId,
            UUID projectId,
            UUID locationId,
            String remarks
    ) {}

    public record TransferRequest(
            UUID toStoreId,
            UUID toLocationId,
            String assignmentType,
            UUID toCustodianUserId,
            String toCustodianNameSnapshot,
            UUID toDepartmentId,
            UUID toProjectId,
            String remarks
    ) {}

    public record ReturnRequest(
            UUID returnStoreId,
            UUID returnLocationId,
            String conditionStatus,
            String disposition,
            String remarks
    ) {}

    public record RepairRequest(
            String action, // "SEND_TO_REPAIR" or "RETURN_FROM_REPAIR"
            UUID vendorId,
            String vendorNameSnapshot,
            String complaintDetail,
            Boolean warrantyClaim,
            BigDecimal repairCost,
            String partsReplaced,
            String finalCondition,
            String diagnosis,
            String remarks
    ) {}

    public record DisposeRequest(
            String disposalMethod, // "AUCTION", "E_WASTE", "SCRAP", "RETURN_TO_OEM", "TRANSFER", "OTHER"
            UUID purchaserVendorId,
            String purchaserNameSnapshot,
            BigDecimal saleAmount,
            String certificateNumber,
            String remarks
    ) {}

    public record RepairTicketResponse(
            UUID id,
            String repairNo,
            UUID assetId,
            String assetCode,
            LocalDate complaintDate,
            String complaintDetail,
            Boolean warrantyClaim,
            UUID vendorId,
            String vendorNameSnapshot,
            String dispatchChallanNo,
            LocalDate sentDate,
            LocalDate receivedDate,
            String diagnosis,
            String repairAction,
            String partsReplaced,
            BigDecimal repairCost,
            String status,
            String finalCondition
    ) {}

    public record DisposalResponse(
            UUID id,
            String disposalNo,
            LocalDate disposalDate,
            String disposalMethod,
            UUID purchaserVendorId,
            String purchaserNameSnapshot,
            BigDecimal saleAmount,
            String certificateNumber,
            String status
    ) {}
}
