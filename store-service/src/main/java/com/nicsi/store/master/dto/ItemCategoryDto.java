package com.nicsi.store.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class ItemCategoryDto {
    private ItemCategoryDto() {}
    
    public record CreateRequest(
        @NotBlank @Size(max = 30) String categoryCode,
        @NotBlank @Size(max = 120) String categoryName,
        @Size(max = 500) String description,
        @NotNull Integer sortOrder
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 120) String categoryName,
        @Size(max = 500) String description,
        @NotNull Integer sortOrder,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, String categoryCode, String categoryName,
        String description, int sortOrder,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
