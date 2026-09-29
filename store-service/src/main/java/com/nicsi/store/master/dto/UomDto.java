package com.nicsi.store.master.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UomDto {
    private UomDto() {}
    
    public record CreateRequest(
        @NotBlank @Size(max = 20) String uomCode,
        @NotBlank @Size(max = 80) String uomName,
        @NotBlank @Size(max = 20) String uomType,
        Boolean decimalAllowed,
        @Min(0) @Max(6) Integer decimalScale,
        @Size(max = 255) String description
    ) {}
    
    public record UpdateRequest(
        @NotBlank @Size(max = 80) String uomName,
        @NotBlank @Size(max = 20) String uomType,
        Boolean decimalAllowed,
        @Min(0) @Max(6) Integer decimalScale,
        @Size(max = 255) String description,
        @NotNull Long version
    ) {}
    
    public record Response(
        UUID id, String uomCode, String uomName, String uomType,
        boolean decimalAllowed, int decimalScale, String description,
        boolean active, Instant createdAt, UUID createdBy,
        Instant updatedAt, UUID updatedBy, long version
    ) {}
}
