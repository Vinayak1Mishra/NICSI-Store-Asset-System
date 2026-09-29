package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ItemSubcategoryDto {
    private ItemSubcategoryDto() {}
    
    public record CreateRequest(
        @NotBlank @Size(max = 30) String subcategoryCode,
        @NotBlank @Size(max = 120) String subcategoryName,
        @Size(max = 500) String description,
        @NotNull Integer sortOrder
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 120) String subcategoryName,
        @Size(max = 500) String description,
        @NotNull Integer sortOrder,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, UUID categoryId, String categoryCode,
        String subcategoryCode, String subcategoryName,
        String description, int sortOrder,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
