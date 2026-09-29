package com.nicsi.store.disposal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class DisposalDto {

    public record CreateLine(
            @NotNull UUID assetId,
            BigDecimal realizedValue,
            String remarks
    ) {}

    public record CreateRequest(
            @NotBlank String disposalMethod,
            LocalDate disposalDate,
            UUID purchaserVendorId,
            String purchaserNameSnapshot,
            BigDecimal saleAmount,
            String certificateNumber,
            @NotEmpty List<CreateLine> items
    ) {}

    public record Response(
            UUID id,
            String disposalNo,
            LocalDate disposalDate,
            String disposalMethod,
            UUID purchaserVendorId,
            String purchaserNameSnapshot,
            BigDecimal saleAmount,
            String certificateNumber,
            String status,
            int itemCount,
            Instant approvedAt,
            UUID approvedBy,
            Instant postedAt,
            UUID postedBy,
            Instant createdAt,
            UUID createdBy,
            List<ItemResponse> items
    ) {}

    public record ItemResponse(
            UUID id,
            UUID assetId,
            String assetCode,
            String itemName,
            String serialNumber,
            BigDecimal realizedValue,
            String remarks
    ) {}
}
