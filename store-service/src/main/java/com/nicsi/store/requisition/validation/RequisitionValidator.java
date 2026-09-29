package com.nicsi.store.requisition.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.requisition.dto.RequisitionDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
public class RequisitionValidator {

    private final ItemRepository itemRepository;

    public RequisitionValidator(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public Map<UUID, Item> validateCreate(RequisitionDto.CreateRequest request) {
        validateCommon(request.requiredByDate(), request.items());
        return loadAndValidateItems(request.items());
    }

    public Map<UUID, Item> validateUpdate(RequisitionDto.UpdateRequest request) {
        validateCommon(request.requiredByDate(), request.items());
        return loadAndValidateItems(request.items());
    }

    private void validateCommon(LocalDate requiredByDate, List<RequisitionDto.LineRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("REQUISITION_EMPTY_ITEMS", "Requisition must contain at least one item line", HttpStatus.BAD_REQUEST);
        }
        if (items.size() > 50) {
            throw new BusinessException("REQUISITION_MAX_ITEMS_EXCEEDED", "Requisition cannot exceed 50 items per request", HttpStatus.BAD_REQUEST);
        }
        if (requiredByDate != null && requiredByDate.isBefore(LocalDate.now())) {
            throw new BusinessException("REQUISITION_INVALID_REQUIRED_DATE", "Required-by date cannot be in the past", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> itemIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            RequisitionDto.LineRequest line = items.get(i);
            int lineNo = i + 1;
            if (line.itemId() == null) {
                throw new BusinessException("REQUISITION_ITEM_ID_REQUIRED", "Item ID is required for line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (!itemIds.add(line.itemId())) {
                throw new BusinessException("REQUISITION_DUPLICATE_ITEM", "Duplicate item found on line " + lineNo + ". Each item may only appear once per requisition.", HttpStatus.BAD_REQUEST);
            }
            if (line.requestedQty() == null || line.requestedQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("REQUISITION_INVALID_QTY", "Requested quantity must be greater than zero on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (line.estimatedUnitRate() != null && line.estimatedUnitRate().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("REQUISITION_INVALID_RATE", "Estimated unit rate cannot be negative on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private Map<UUID, Item> loadAndValidateItems(List<RequisitionDto.LineRequest> lines) {
        Map<UUID, Item> itemMap = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            RequisitionDto.LineRequest line = lines.get(i);
            int lineNo = i + 1;
            Item item = itemRepository.findById(line.itemId())
                    .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found with id: " + line.itemId(), HttpStatus.NOT_FOUND));

            if (!item.isActive()) {
                throw new BusinessException("ITEM_INACTIVE", "Item '" + item.getItemName() + "' (" + item.getItemCode() + ") is inactive and cannot be requisitioned", HttpStatus.BAD_REQUEST);
            }

            Uom uom = item.getBaseUom();
            if (uom != null) {
                validateQuantityAgainstUom(line.requestedQty(), uom, lineNo);
            }
            itemMap.put(line.itemId(), item);
        }
        return itemMap;
    }

    public void validateQuantityAgainstUom(BigDecimal qty, Uom uom, int lineNo) {
        if (qty == null) return;
        BigDecimal stripped = qty.stripTrailingZeros();
        int scale = stripped.scale() < 0 ? 0 : stripped.scale();

        if (!uom.isDecimalAllowed() && scale > 0) {
            throw new BusinessException(
                    "UOM_DECIMAL_NOT_ALLOWED",
                    "Decimal quantities are not allowed for UOM '" + uom.getUomCode() + "' on line " + lineNo,
                    HttpStatus.BAD_REQUEST
            );
        }

        if (uom.isDecimalAllowed() && scale > uom.getDecimalScale()) {
            throw new BusinessException(
                    "UOM_SCALE_EXCEEDED",
                    "Quantity scale (" + scale + ") exceeds allowed scale of " + uom.getDecimalScale() + " for UOM '" + uom.getUomCode() + "' on line " + lineNo,
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
