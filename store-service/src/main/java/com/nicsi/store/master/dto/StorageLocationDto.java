package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StorageLocationDto {
    private StorageLocationDto() {}
    
    public record CreateRequest(
        @NotNull UUID storeId,
        UUID parentLocationId,
        @NotBlank @Size(max = 50) String locationCode,
        @NotBlank @Size(max = 150) String locationName,
        @NotBlank @Size(max = 20) String locationType,
        @Size(max = 120) String barcodeValue
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 150) String locationName,
        @NotBlank @Size(max = 20) String locationType,
        @Size(max = 120) String barcodeValue,
        UUID parentLocationId,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, UUID storeId, String storeCode, String storeName,
        UUID parentLocationId, String parentLocationCode, String parentLocationName,
        String locationCode, String locationName, String locationType,
        String barcodeValue,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
    
    public record TreeResponse(
        UUID id, String locationCode, String locationName,
        String locationType, String barcodeValue, boolean active,
        List<TreeResponse> children
    ) {}
}
