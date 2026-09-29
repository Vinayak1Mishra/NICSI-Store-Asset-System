package com.nicsi.store.requisition.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.requisition.dto.RequisitionDto;
import com.nicsi.store.requisition.service.RequisitionApprovalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/requisitions")
public class RequisitionApprovalController {

    private final RequisitionApprovalService approvalService;

    public RequisitionApprovalController(RequisitionApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping("/approvals/pending")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<RequisitionDto.Response>> getPendingApprovals(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.from(approvalService.getPendingApprovals(pageable)));
    }

    @PostMapping("/{id}/approvals/decision")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> processDecision(
            @PathVariable UUID id,
            @Valid @RequestBody RequisitionDto.DecisionRequest request
    ) {
        return ResponseEntity.ok(approvalService.processDecision(id, request));
    }
}
