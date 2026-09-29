package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ItemDto {
    private ItemDto() {}
    
    public record CreateRequest(
        @NotBlank @Size(max = 60) String itemCode,
        @NotBlank @Size(max = 200) String itemName,
        @NotNull UUID categoryId,
        UUID subcategoryId,
        @NotNull UUID baseUomId,
        @NotBlank @Size(max = 30) String itemType,
        @NotBlank @Size(max = 20) String trackingType,
        @Size(max = 500) String shortDescription,
        String specification,
        @Size(max = 150) String manufacturerDefault,
        @Size(max = 150) String modelDefault,
        @Size(max = 30) String hsnSacCode,
        @Min(0) BigDecimal standardRate,
        @Min(0) Integer usefulLifeMonths,
        @Min(0) Integer warrantyMonths,
        @NotNull Boolean returnable,
        @NotNull Boolean warrantyApplicable,
        @NotNull Boolean expiryTracking,
        @NotNull Boolean assetRequired
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 60) String itemCode,
        @NotBlank @Size(max = 200) String itemName,
        @NotNull UUID categoryId,
        UUID subcategoryId,
        @NotNull UUID baseUomId,
        @NotBlank @Size(max = 30) String itemType,
        @NotBlank @Size(max = 20) String trackingType,
        @Size(max = 500) String shortDescription,
        String specification,
        @Size(max = 150) String manufacturerDefault,
        @Size(max = 150) String modelDefault,
        @Size(max = 30) String hsnSacCode,
        @Min(0) BigDecimal standardRate,
        @Min(0) Integer usefulLifeMonths,
        @Min(0) Integer warrantyMonths,
        @NotNull Boolean returnable,
        @NotNull Boolean warrantyApplicable,
        @NotNull Boolean expiryTracking,
        @NotNull Boolean assetRequired,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, String itemCode, String itemName,
        UUID categoryId, String categoryCode, String categoryName,
        UUID subcategoryId, String subcategoryCode, String subcategoryName,
        UUID baseUomId, String uomCode, String uomName,
        String itemType, String trackingType,
        String shortDescription, String specification,
        String manufacturerDefault, String modelDefault,
        String hsnSacCode, BigDecimal standardRate,
        Integer usefulLifeMonths, Integer warrantyMonths,
        boolean returnable, boolean warrantyApplicable,
        boolean expiryTracking, boolean assetRequired,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
