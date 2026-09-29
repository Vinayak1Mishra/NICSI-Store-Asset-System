package com.nicsi.store.grn.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class GrnPostDto {

    public record PostRequest(
            List<LineSerialRequest> lineSerials,
            String remarks
    ) {}

    public record LineSerialRequest(
            UUID grnItemId,
            List<String> serialNumbers
    ) {}

    public record PostResponse(
            UUID grnId,
            String grnNo,
            String status,
            Instant postedAt,
            UUID postedBy,
            int totalPostedLines,
            int totalAssetsCreated,
            List<PostedLineResponse> postedLines,
            List<UUID> generatedAssetIds
    ) {}

    public record PostedLineResponse(
            UUID grnItemId,
            UUID itemId,
            String itemCode,
            String itemName,
            BigDecimal acceptedQty,
            BigDecimal unitRate,
            String locationCode,
            String transactionNo,
            int assetsCreated
    ) {}
}
