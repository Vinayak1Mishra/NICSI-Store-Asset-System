package com.nicsi.store.grn.controller;

import com.nicsi.store.common.idempotency.IdempotencyInterceptor;
import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.grn.dto.GrnPostDto;
import com.nicsi.store.grn.service.GrnPostingService;
import com.nicsi.store.grn.service.GrnService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/store/grns")
public class GrnController {

    private final GrnService grnService;
    private final GrnPostingService grnPostingService;

    public GrnController(GrnService grnService, GrnPostingService grnPostingService) {
        this.grnService = grnService;
        this.grnPostingService = grnPostingService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnDto.Response> createGrn(@Valid @RequestBody GrnDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(grnService.createGrn(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnDto.Response> updateGrn(
            @PathVariable UUID id,
            @Valid @RequestBody GrnDto.UpdateRequest request
    ) {
        return ResponseEntity.ok(grnService.updateGrn(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnDto.Response> submitGrn(@PathVariable UUID id) {
        return ResponseEntity.ok(grnService.submitGrn(id));
    }

    @PostMapping("/{id}/post")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnPostDto.PostResponse> postGrn(
            @PathVariable UUID id,
            @RequestBody(required = false) GrnPostDto.PostRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        return ResponseEntity.ok(grnPostingService.postGrn(id, request, idempotencyKey));
    }

    @PostMapping("/{id}/post-stock")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.GRN_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnPostDto.PostResponse> postGrnStockAlias(
            @PathVariable UUID id,
            @RequestBody(required = false) GrnPostDto.PostRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        return ResponseEntity.ok(grnPostingService.postGrn(id, request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<GrnDto.SummaryResponse>> listGrns(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(grnService.search(storeId, status, search, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnDto.Response> getGrn(@PathVariable UUID id) {
        return ResponseEntity.ok(grnService.getGrn(id));
    }

    @GetMapping("/by-no/{grnNo}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.STORE_DASHBOARD_VIEW + "', '" + Permissions.GRN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<GrnDto.Response> getGrnByNo(@PathVariable String grnNo) {
        return ResponseEntity.ok(grnService.getGrnByNo(grnNo));
    }
}
