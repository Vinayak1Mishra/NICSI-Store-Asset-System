package com.nicsi.store.master.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.dto.UomDto;
import com.nicsi.store.master.repository.UomRepository;
import com.nicsi.store.master.validation.MasterInUseValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UomService {

    private final UomRepository repo;
    private final AuditService auditService;
    private final MasterInUseValidator inUseValidator;

    public UomService(UomRepository repo, AuditService auditService, MasterInUseValidator inUseValidator) {
        this.repo = repo;
        this.auditService = auditService;
        this.inUseValidator = inUseValidator;
    }

    @Transactional
    public UomDto.Response create(UomDto.CreateRequest req) {
        if (repo.existsByUomCodeIgnoreCase(req.uomCode())) {
            throw new BusinessException("DUPLICATE_CODE", "UOM code already exists", HttpStatus.CONFLICT);
        }

        Uom uom = new Uom();
        uom.setUomCode(req.uomCode());
        uom.setUomName(req.uomName());
        uom.setUomType(req.uomType());
        uom.setDecimalAllowed(Boolean.TRUE.equals(req.decimalAllowed()));
        uom.setDecimalScale(req.decimalScale() != null ? req.decimalScale().shortValue() : (short) 0);
        uom.setDescription(req.description());
        uom.setActive(true);
        uom.setCreatedBy(CurrentUserHolder.getUserId());
        uom.setUpdatedBy(CurrentUserHolder.getUserId());

        uom = repo.save(uom);
        UomDto.Response response = toResponse(uom);

        auditService.record(new AuditEvent("STORE", "CREATE", "UOM", uom.getId(), uom.getUomCode(), null, response, null));
        return response;
    }

    @Transactional
    public UomDto.Response update(UUID id, UomDto.UpdateRequest req) {
        Uom uom = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "UOM not found", HttpStatus.NOT_FOUND));
        if (req.version() != null && !req.version().equals(uom.getVersion())) {
            throw new BusinessException("OPTIMISTIC_LOCK_CONFLICT", "UOM was updated by another transaction", HttpStatus.CONFLICT);
        }
        UomDto.Response oldValue = toResponse(uom);

        uom.setUomName(req.uomName());
        uom.setUomType(req.uomType());
        uom.setDecimalAllowed(Boolean.TRUE.equals(req.decimalAllowed()));
        uom.setDecimalScale(req.decimalScale() != null ? req.decimalScale().shortValue() : (short) 0);
        uom.setDescription(req.description());
        uom.setUpdatedBy(CurrentUserHolder.getUserId());

        uom = repo.saveAndFlush(uom);
        UomDto.Response newValue = toResponse(uom);

        auditService.record(new AuditEvent("STORE", "UPDATE", "UOM", uom.getId(), uom.getUomCode(), oldValue, newValue, null));
        return newValue;
    }

    @Transactional
    public void updateStatus(UUID id, boolean active) {
        Uom uom = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "UOM not found", HttpStatus.NOT_FOUND));
        UomDto.Response oldValue = toResponse(uom);

        if (!active) {
            inUseValidator.validateUomDeactivation(id);
        }

        uom.setActive(active);
        uom.setUpdatedBy(CurrentUserHolder.getUserId());
        uom = repo.save(uom);
        UomDto.Response newValue = toResponse(uom);

        String action = active ? "ACTIVATE" : "DEACTIVATE";
        auditService.record(new AuditEvent("STORE", action, "UOM", uom.getId(), uom.getUomCode(), oldValue, newValue, null));
    }

    public PageResponse<UomDto.Response> list(String search, Boolean active, Pageable pageable) {
        Page<Uom> page;
        if (search != null && !search.trim().isEmpty()) {
            page = repo.findByUomCodeContainingIgnoreCaseOrUomNameContainingIgnoreCase(search.trim(), search.trim(), pageable);
        } else if (Boolean.TRUE.equals(active)) {
            page = repo.findByActiveTrue(pageable);
        } else {
            page = repo.findAll(pageable);
        }
        return PageResponse.from(page.map(this::toResponse));
    }

    public UomDto.Response getById(UUID id) {
        Uom uom = repo.findById(id).orElseThrow(() -> new BusinessException("NOT_FOUND", "UOM not found", HttpStatus.NOT_FOUND));
        return toResponse(uom);
    }

    private UomDto.Response toResponse(Uom uom) {
        return new UomDto.Response(
            uom.getId(),
            uom.getUomCode(),
            uom.getUomName(),
            uom.getUomType(),
            uom.isDecimalAllowed(),
            uom.getDecimalScale(),
            uom.getDescription(),
            uom.isActive(),
            uom.getCreatedAt(),
            uom.getCreatedBy(),
            uom.getUpdatedAt(),
            uom.getUpdatedBy(),
            uom.getVersion() != null ? uom.getVersion() : 0L
        );
    }
}
