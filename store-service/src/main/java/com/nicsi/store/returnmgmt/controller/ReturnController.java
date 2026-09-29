package com.nicsi.store.returnmgmt.controller;

import com.nicsi.store.common.idempotency.IdempotencyInterceptor;
import com.nicsi.store.common.idempotency.IdempotentPost;
import com.nicsi.store.common.security.Permissions;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.returnmgmt.dto.ReturnDto;
import com.nicsi.store.returnmgmt.service.ReturnService;
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
@RequestMapping({"/api/store/returns", "/api/returns"})
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<ReturnDto.Response> createReturn(@Valid @RequestBody ReturnDto.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.createReturn(request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_CREATE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<ReturnDto.Response> submitReturn(@PathVariable UUID id) {
        return ResponseEntity.ok(returnService.submitReturn(id));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_RECEIVE + "', '" + Permissions.RETURN_APPROVE + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<ReturnDto.Response> receiveReturn(
            @PathVariable UUID id,
            @RequestBody ReturnDto.ReceiveRequest request
    ) {
        return ResponseEntity.ok(returnService.receiveAndInspectReturn(id, request));
    }

    @PostMapping("/{id}/post")
    @IdempotentPost
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_POST + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<ReturnDto.Response> postReturn(
            @PathVariable UUID id,
            @RequestBody(required = false) ReturnDto.PostRequest request,
            HttpServletRequest httpRequest
    ) {
        String idempotencyKey = (String) httpRequest.getAttribute(IdempotencyInterceptor.IDEMPOTENCY_KEY_ATTRIBUTE);
        return ResponseEntity.ok(returnService.postReturn(id, request, idempotencyKey));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<ReturnDto.Response> getReturn(@PathVariable UUID id) {
        return ResponseEntity.ok(returnService.getReturn(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + Permissions.RETURN_VIEW + "', '" + Permissions.STORE_ADMIN + "')")
    public ResponseEntity<PageResponse<ReturnDto.SummaryResponse>> listReturns(
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(returnService.search(storeId, status, search, pageable));
    }
}
