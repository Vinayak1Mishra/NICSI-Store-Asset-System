package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class StoreSiteDto {
    private StoreSiteDto() {}
    
    public record CreateRequest(
        @NotBlank @Size(max = 30) String storeCode,
        @NotBlank @Size(max = 150) String storeName,
        UUID officeLocationId,
        @Size(max = 50) String officeCodeSnapshot,
        @Size(max = 200) String officeNameSnapshot,
        String address,
        @NotBlank @Size(max = 30) String storeType
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 150) String storeName,
        UUID officeLocationId,
        @Size(max = 50) String officeCodeSnapshot,
        @Size(max = 200) String officeNameSnapshot,
        String address,
        @NotBlank @Size(max = 30) String storeType,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, String storeCode, String storeName,
        UUID officeLocationId, String officeCodeSnapshot, String officeNameSnapshot,
        String address, String storeType,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
