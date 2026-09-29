package com.nicsi.store.requisition.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.requisition.dto.RequisitionDto;
import com.nicsi.store.requisition.service.RequisitionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/requisitions")
public class RequisitionController {

    private final RequisitionService requisitionService;

    public RequisitionController(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @PostMapping
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> createRequisition(@Valid @RequestBody RequisitionDto.CreateRequest request) {
        RequisitionDto.Response created = requisitionService.createRequisition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_VIEW + "', '" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<RequisitionDto.Response>> listRequisitions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) UUID requesterUserId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.from(requisitionService.search(
                search, status, priority, requesterUserId, departmentId, fromDate, toDate, pageable
        )));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_VIEW + "', '" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> getRequisition(@PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.getRequisition(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> updateRequisition(
            @PathVariable UUID id,
            @Valid @RequestBody RequisitionDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(requisitionService.updateRequisition(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> submitRequisition(@PathVariable UUID id) {
        return ResponseEntity.ok(requisitionService.submitRequisition(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('" + Permissions.REQUISITION_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<RequisitionDto.Response> cancelRequisition(
            @PathVariable UUID id,
            @RequestBody(required = false) RequisitionDto.CancelRequest request
    ) {
        return ResponseEntity.ok(requisitionService.cancelRequisition(id, request));
    }
}
