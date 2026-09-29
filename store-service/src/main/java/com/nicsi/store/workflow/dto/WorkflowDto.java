package com.nicsi.store.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkflowDto {

    private WorkflowDto() {}

    public record StepResponse(
        UUID id,
        int stepNo,
        String stepCode,
        String stepName,
        String approverType,
        String approverRoleCode,
        Integer slaHours,
        boolean allowReject,
        boolean allowReturn
    ) {}

    public record ActionResponse(
        UUID id,
        int stepNo,
        String actionType,
        UUID actorUserId,
        String actorRoleCode,
        String comments,
        Instant actionTime,
        String metadata
    ) {}

    public record InstanceResponse(
        UUID id,
        UUID workflowDefinitionId,
        String workflowCode,
        String workflowName,
        String entityType,
        UUID entityId,
        String status,
        Integer currentStepNo,
        Instant startedAt,
        Instant completedAt,
        UUID startedBy,
        List<StepResponse> steps,
        List<ActionResponse> actions
    ) {}

    public record ActionRequest(
        @NotBlank String actionType,
        String comments,
        String metadata
    ) {}
}
