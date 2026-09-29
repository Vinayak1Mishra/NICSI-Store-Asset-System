package com.nicsi.store.requisition.dto;

import com.nicsi.store.workflow.dto.WorkflowDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RequisitionDto {

    private RequisitionDto() {}

    public record LineRequest(
        @NotNull UUID itemId,
        @NotNull @DecimalMin("0.001") BigDecimal requestedQty,
        @DecimalMin("0.00") BigDecimal estimatedUnitRate,
        @Size(max = 2000) String specification,
        @Size(max = 2000) String justification,
        @Size(max = 300) String preferredMakeModel
    ) {}

    public record CreateRequest(
        UUID departmentId,
        @Size(max = 50) String departmentCodeSnapshot,
        @Size(max = 200) String departmentNameSnapshot,
        UUID divisionId,
        @Size(max = 200) String divisionNameSnapshot,
        UUID projectId,
        @Size(max = 60) String projectCodeSnapshot,
        @Size(max = 250) String projectNameSnapshot,
        @Size(max = 2000) String purpose,
        @NotBlank @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        LocalDate requiredByDate,
        @NotEmpty @Valid List<LineRequest> items
    ) {}

    public record UpdateRequest(
        UUID departmentId,
        @Size(max = 50) String departmentCodeSnapshot,
        @Size(max = 200) String departmentNameSnapshot,
        UUID divisionId,
        @Size(max = 200) String divisionNameSnapshot,
        UUID projectId,
        @Size(max = 60) String projectCodeSnapshot,
        @Size(max = 250) String projectNameSnapshot,
        @Size(max = 2000) String purpose,
        @NotBlank @Pattern(regexp = "LOW|NORMAL|HIGH|URGENT") String priority,
        LocalDate requiredByDate,
        @NotEmpty @Valid List<LineRequest> items,
        @NotNull Long version
    ) {}

    public record LineResponse(
        UUID id,
        int lineNo,
        UUID itemId,
        String itemCode,
        String itemName,
        String uomCode,
        BigDecimal requestedQty,
        BigDecimal approvedQty,
        BigDecimal issuedQty,
        BigDecimal estimatedUnitRate,
        String specification,
        String justification,
        String preferredMakeModel,
        String lineStatus,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        long version
    ) {}

    public record Response(
        UUID id,
        String requisitionNo,
        LocalDate requisitionDate,
        UUID requesterUserId,
        String requesterNameSnapshot,
        UUID departmentId,
        String departmentCodeSnapshot,
        String departmentNameSnapshot,
        UUID divisionId,
        String divisionNameSnapshot,
        UUID projectId,
        String projectCodeSnapshot,
        String projectNameSnapshot,
        String purpose,
        String priority,
        LocalDate requiredByDate,
        String status,
        BigDecimal totalEstimatedAmount,
        Instant submittedAt,
        Instant closedAt,
        Instant createdAt,
        UUID createdBy,
        Instant updatedAt,
        UUID updatedBy,
        long version,
        List<LineResponse> items,
        WorkflowDto.InstanceResponse workflow
    ) {}

    public record LineDecision(
        @NotNull Integer lineNo,
        @DecimalMin("0.000") BigDecimal approvedQty,
        String remarks
    ) {}

    public record DecisionRequest(
        @NotBlank @Pattern(regexp = "APPROVE|REJECT|RETURN") String action,
        @Size(max = 2000) String comments,
        List<LineDecision> lineDecisions
    ) {}

    public record CancelRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {}
}
