package com.nicsi.store.issue.controller;

import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.idempotency.IdempotencyInterceptor;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.issue.dto.IssueDto;
import com.nicsi.store.issue.service.IssueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for the Issue module.
 *
 * POST /api/store/issues                      — create draft
 * POST /api/store/issues/{id}/submit          — submit for approval
 * POST /api/store/issues/{id}/approve         — approve (ISSUE_APPROVE)
 * POST /api/store/issues/{id}/reject          — reject
 * POST /api/store/issues/{id}/post            — post to stock (ISSUE_POST, maker-checker, idempotent)
 * POST /api/store/issues/{id}/acknowledge     — digital acknowledgement by recipient
 * GET  /api/store/issues/{id}                 — get issue
 * GET  /api/store/issues                      — search / list
 */
@RestController
@RequestMapping({"/api/store/issues", "/api/issues"})
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.Response> createIssue(
            @Valid @RequestBody IssueDto.CreateRequest request
    ) {
        IssueDto.Response response = issueService.createIssue(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.Response> submitIssue(@PathVariable UUID id) {
        return ResponseEntity.ok(issueService.submitIssue(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.Response> approveIssue(@PathVariable UUID id) {
        return ResponseEntity.ok(issueService.approveIssue(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.Response> rejectIssue(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "") String remarks
    ) {
        return ResponseEntity.ok(issueService.rejectIssue(id, remarks));
    }

    /**
     * Post an issue to inventory. Requires Idempotency-Key header.
     * Enforces maker-checker: the poster must differ from the creator.
     */
    @PostMapping("/{id}/post")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.PostResult> postIssue(
            @PathVariable UUID id,
            @RequestBody(required = false) IssueDto.PostRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        IssueDto.PostResult result = issueService.postIssue(id, request, idempotencyKey);
        return ResponseEntity.ok(result);
    }

    /**
     * Digital acknowledgement of issued items by the recipient.
     */
    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<IssueDto.Response> acknowledgeIssue(
            @PathVariable UUID id,
            @Valid @RequestBody IssueDto.AcknowledgeRequest request
    ) {
        return ResponseEntity.ok(issueService.acknowledgeIssue(id, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_CREATE + "', '" + Permissions.STORE_ADMIN + "')")  // any issue participant can view
    public ResponseEntity<IssueDto.Response> getIssue(@PathVariable UUID id) {
        return ResponseEntity.ok(issueService.getIssue(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.ISSUE_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<IssueDto.SummaryResponse>> searchIssues(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID issuedToUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issueDate"));
        return ResponseEntity.ok(issueService.search(storeId, status, issuedToUserId, pageable));
    }
}
