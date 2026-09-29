package com.nicsi.store.workflow.service;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUser;
import com.nicsi.store.workflow.domain.WorkflowAction;
import com.nicsi.store.workflow.domain.WorkflowDefinition;
import com.nicsi.store.workflow.domain.WorkflowInstance;
import com.nicsi.store.workflow.domain.WorkflowStepDefinition;
import com.nicsi.store.workflow.dto.WorkflowDto;
import com.nicsi.store.workflow.repository.WorkflowActionRepository;
import com.nicsi.store.workflow.repository.WorkflowDefinitionRepository;
import com.nicsi.store.workflow.repository.WorkflowInstanceRepository;
import com.nicsi.store.workflow.repository.WorkflowStepDefinitionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WorkflowEngine {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowStepDefinitionRepository stepDefinitionRepository;
    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowActionRepository actionRepository;

    public WorkflowEngine(
            WorkflowDefinitionRepository definitionRepository,
            WorkflowStepDefinitionRepository stepDefinitionRepository,
            WorkflowInstanceRepository instanceRepository,
            WorkflowActionRepository actionRepository
    ) {
        this.definitionRepository = definitionRepository;
        this.stepDefinitionRepository = stepDefinitionRepository;
        this.instanceRepository = instanceRepository;
        this.actionRepository = actionRepository;
    }

    @Transactional
    public WorkflowInstance startWorkflow(String entityType, UUID entityId, UUID startedBy, String workflowCode) {
        Optional<WorkflowInstance> existing = instanceRepository.findByEntityTypeAndEntityId(entityType, entityId);
        WorkflowInstance instance;

        WorkflowDefinition definition;
        if (workflowCode != null && !workflowCode.isBlank()) {
            definition = definitionRepository.findByWorkflowCodeAndActiveTrue(workflowCode)
                    .orElseThrow(() -> new BusinessException("WORKFLOW_DEF_NOT_FOUND", "Workflow definition not found for code: " + workflowCode, HttpStatus.NOT_FOUND));
        } else {
            definition = definitionRepository.findByEntityTypeAndActiveTrue(entityType)
                    .orElseThrow(() -> new BusinessException("WORKFLOW_DEF_NOT_FOUND", "No active workflow definition for entity type: " + entityType, HttpStatus.NOT_FOUND));
        }

        if (existing.isPresent()) {
            instance = existing.get();
            if ("RUNNING".equals(instance.getStatus())) {
                throw new BusinessException("WORKFLOW_ALREADY_RUNNING", "A workflow instance is already running for this entity", HttpStatus.CONFLICT);
            }
            // Re-starting a returned or rejected workflow
            instance.setWorkflowDefinition(definition);
            instance.setStatus("RUNNING");
            instance.setCurrentStepNo(1);
            instance.setCompletedAt(null);
            instance = instanceRepository.save(instance);
        } else {
            instance = new WorkflowInstance();
            instance.setWorkflowDefinition(definition);
            instance.setEntityType(entityType);
            instance.setEntityId(entityId);
            instance.setStartedBy(startedBy);
            instance.setStatus("RUNNING");
            instance.setCurrentStepNo(1);
            instance = instanceRepository.save(instance);
        }

        // Record initial submission action
        WorkflowAction action = new WorkflowAction();
        action.setWorkflowInstance(instance);
        action.setStepNo(1);
        action.setActionType("SUBMIT");
        action.setActorUserId(startedBy);
        action.setActorRoleCode(null);
        action.setComments("Workflow initiated");
        actionRepository.save(action);

        return instance;
    }

    @Transactional
    public WorkflowInstance processAction(
            String entityType,
            UUID entityId,
            String actionType,
            CurrentUser actor,
            String comments,
            String metadata
    ) {
        WorkflowInstance instance = instanceRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .orElseThrow(() -> new BusinessException("WORKFLOW_INSTANCE_NOT_FOUND", "No workflow instance found for " + entityType + " " + entityId, HttpStatus.NOT_FOUND));

        if (!"RUNNING".equalsIgnoreCase(instance.getStatus())) {
            throw new BusinessException("WORKFLOW_NOT_RUNNING", "Workflow is not running. Current status: " + instance.getStatus(), HttpStatus.CONFLICT);
        }

        UUID defId = instance.getWorkflowDefinition().getId();
        int currentStepNo = instance.getCurrentStepNo() != null ? instance.getCurrentStepNo() : 1;

        WorkflowStepDefinition currentStep = stepDefinitionRepository.findByWorkflowDefinitionIdAndStepNo(defId, currentStepNo)
                .orElseThrow(() -> new BusinessException("WORKFLOW_STEP_NOT_FOUND", "Step " + currentStepNo + " not defined for workflow", HttpStatus.INTERNAL_SERVER_ERROR));

        verifyActorAuthorization(currentStep, actor);

        String upperAction = actionType.toUpperCase().trim();
        Integer nextStepNo = currentStepNo;

        switch (upperAction) {
            case "APPROVE" -> {
                List<WorkflowStepDefinition> allSteps = stepDefinitionRepository.findByWorkflowDefinitionIdOrderByStepNoAsc(defId);
                Optional<WorkflowStepDefinition> nextStep = allSteps.stream()
                        .filter(s -> s.getStepNo() > currentStepNo)
                        .findFirst();

                if (nextStep.isPresent()) {
                    instance.setCurrentStepNo(nextStep.get().getStepNo());
                    nextStepNo = nextStep.get().getStepNo();
                } else {
                    instance.setStatus("APPROVED");
                    instance.setCompletedAt(Instant.now());
                }
            }
            case "REJECT" -> {
                if (!currentStep.isAllowReject()) {
                    throw new BusinessException("WORKFLOW_REJECT_DISALLOWED", "Rejection is not permitted at step " + currentStep.getStepName(), HttpStatus.BAD_REQUEST);
                }
                instance.setStatus("REJECTED");
                instance.setCompletedAt(Instant.now());
            }
            case "RETURN" -> {
                if (!currentStep.isAllowReturn()) {
                    throw new BusinessException("WORKFLOW_RETURN_DISALLOWED", "Return is not permitted at step " + currentStep.getStepName(), HttpStatus.BAD_REQUEST);
                }
                instance.setStatus("RETURNED");
                instance.setCompletedAt(Instant.now());
            }
            case "CANCEL" -> {
                instance.setStatus("CANCELLED");
                instance.setCompletedAt(Instant.now());
            }
            default -> throw new BusinessException("WORKFLOW_INVALID_ACTION", "Invalid workflow action: " + actionType, HttpStatus.BAD_REQUEST);
        }

        // Save action record
        WorkflowAction action = new WorkflowAction();
        action.setWorkflowInstance(instance);
        action.setStepNo(currentStepNo);
        action.setActionType(upperAction);
        action.setActorUserId(actor.userId());
        action.setActorRoleCode(actor.roles() != null && !actor.roles().isEmpty() ? actor.roles().iterator().next() : null);
        action.setComments(comments);
        action.setMetadata(metadata);
        actionRepository.save(action);

        return instanceRepository.save(instance);
    }

    public void verifyActorAuthorization(WorkflowStepDefinition step, CurrentUser actor) {
        if (actor == null) {
            throw new BusinessException("UNAUTHENTICATED", "Authentication required to perform workflow actions", HttpStatus.UNAUTHORIZED);
        }
        if (actor.hasRole("ROLE_ADMIN") || actor.hasRole("ROLE_STORE_MANAGER")) {
            return; // Admins and Store Managers can act/override any step
        }
        String requiredRole = step.getApproverRoleCode();
        if (requiredRole != null && !actor.hasRole(requiredRole)) {
            throw new BusinessException(
                    "WORKFLOW_FORBIDDEN",
                    "User does not have required role '" + requiredRole + "' for step '" + step.getStepName() + "'",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    @Transactional
    public void cancelWorkflow(String entityType, UUID entityId, UUID actorUserId, String reason) {
        instanceRepository.findByEntityTypeAndEntityId(entityType, entityId).ifPresent(instance -> {
            if ("RUNNING".equalsIgnoreCase(instance.getStatus())) {
                instance.setStatus("CANCELLED");
                instance.setCompletedAt(Instant.now());
                instanceRepository.save(instance);

                WorkflowAction action = new WorkflowAction();
                action.setWorkflowInstance(instance);
                action.setStepNo(instance.getCurrentStepNo() != null ? instance.getCurrentStepNo() : 1);
                action.setActionType("CANCEL");
                action.setActorUserId(actorUserId);
                action.setComments(reason != null ? reason : "Requisition cancelled by requester");
                actionRepository.save(action);
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowInstance> findInstance(String entityType, UUID entityId) {
        return instanceRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public WorkflowDto.InstanceResponse toInstanceResponse(WorkflowInstance instance) {
        if (instance == null) return null;

        UUID defId = instance.getWorkflowDefinition().getId();
        List<WorkflowStepDefinition> steps = stepDefinitionRepository.findByWorkflowDefinitionIdOrderByStepNoAsc(defId);
        List<WorkflowAction> actions = actionRepository.findByWorkflowInstanceIdOrderByActionTimeAsc(instance.getId());

        List<WorkflowDto.StepResponse> stepDtos = steps.stream()
                .map(s -> new WorkflowDto.StepResponse(
                        s.getId(),
                        s.getStepNo(),
                        s.getStepCode(),
                        s.getStepName(),
                        s.getApproverType(),
                        s.getApproverRoleCode(),
                        s.getSlaHours(),
                        s.isAllowReject(),
                        s.isAllowReturn()
                )).collect(Collectors.toList());

        List<WorkflowDto.ActionResponse> actionDtos = actions.stream()
                .map(a -> new WorkflowDto.ActionResponse(
                        a.getId(),
                        a.getStepNo(),
                        a.getActionType(),
                        a.getActorUserId(),
                        a.getActorRoleCode(),
                        a.getComments(),
                        a.getActionTime(),
                        a.getMetadata()
                )).collect(Collectors.toList());

        return new WorkflowDto.InstanceResponse(
                instance.getId(),
                defId,
                instance.getWorkflowDefinition().getWorkflowCode(),
                instance.getWorkflowDefinition().getWorkflowName(),
                instance.getEntityType(),
                instance.getEntityId(),
                instance.getStatus(),
                instance.getCurrentStepNo(),
                instance.getStartedAt(),
                instance.getCompletedAt(),
                instance.getStartedBy(),
                stepDtos,
                actionDtos
        );
    }
}
