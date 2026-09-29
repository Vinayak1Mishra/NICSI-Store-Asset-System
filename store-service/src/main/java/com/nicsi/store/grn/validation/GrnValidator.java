package com.nicsi.store.grn.validation;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.grn.dto.GrnDto;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemRepository;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.procurementref.domain.PurchaseOrderItemRef;
import com.nicsi.store.procurementref.domain.PurchaseOrderRef;
import com.nicsi.store.procurementref.repository.PurchaseOrderItemRefRepository;
import com.nicsi.store.procurementref.repository.PurchaseOrderRefRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class GrnValidator {

    private final StoreSiteRepository storeSiteRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final ItemRepository itemRepository;
    private final PurchaseOrderRefRepository purchaseOrderRefRepository;
    private final PurchaseOrderItemRefRepository purchaseOrderItemRefRepository;

    public GrnValidator(
            StoreSiteRepository storeSiteRepository,
            StorageLocationRepository storageLocationRepository,
            ItemRepository itemRepository,
            PurchaseOrderRefRepository purchaseOrderRefRepository,
            PurchaseOrderItemRefRepository purchaseOrderItemRefRepository
    ) {
        this.storeSiteRepository = storeSiteRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.itemRepository = itemRepository;
        this.purchaseOrderRefRepository = purchaseOrderRefRepository;
        this.purchaseOrderItemRefRepository = purchaseOrderItemRefRepository;
    }

    public record ValidatedGrnContext(
            StoreSite store,
            PurchaseOrderRef purchaseOrderRef,
            Map<UUID, Item> items,
            Map<UUID, StorageLocation> locations,
            Map<UUID, PurchaseOrderItemRef> poItems
    ) {}

    public ValidatedGrnContext validateCreate(GrnDto.CreateRequest request) {
        return validate(
                request.storeId(),
                request.poRefId(),
                request.items()
        );
    }

    public ValidatedGrnContext validateUpdate(GrnDto.UpdateRequest request) {
        return validate(
                request.storeId(),
                request.poRefId(),
                request.items()
        );
    }

    private ValidatedGrnContext validate(
            UUID storeId,
            UUID poRefId,
            List<GrnDto.CreateItemRequest> itemRequests
    ) {
        if (storeId == null) {
            throw new BusinessException("GRN_STORE_REQUIRED", "Store site is required", HttpStatus.BAD_REQUEST);
        }
        StoreSite store = storeSiteRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store site not found with id: " + storeId, HttpStatus.NOT_FOUND));

        if (!store.isActive()) {
            throw new BusinessException("STORE_INACTIVE", "Store site is inactive", HttpStatus.BAD_REQUEST);
        }

        PurchaseOrderRef poRef = null;
        if (poRefId != null) {
            poRef = purchaseOrderRefRepository.findById(poRefId)
                    .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase Order not found with id: " + poRefId, HttpStatus.NOT_FOUND));
        }

        if (itemRequests == null || itemRequests.isEmpty()) {
            throw new BusinessException("GRN_EMPTY_ITEMS", "GRN must contain at least one item", HttpStatus.BAD_REQUEST);
        }

        Map<UUID, Item> itemMap = new HashMap<>();
        Map<UUID, StorageLocation> locationMap = new HashMap<>();
        Map<UUID, PurchaseOrderItemRef> poItemMap = new HashMap<>();

        for (int i = 0; i < itemRequests.size(); i++) {
            GrnDto.CreateItemRequest line = itemRequests.get(i);
            int lineNo = i + 1;

            if (line.itemId() == null) {
                throw new BusinessException("ITEM_ID_REQUIRED", "Item ID is required on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (line.receivedQty() == null || line.receivedQty().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("INVALID_RECEIVED_QTY", "Received quantity must be greater than zero on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (line.unitRate() != null && line.unitRate().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("INVALID_UNIT_RATE", "Unit rate cannot be negative on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (line.receivingLocationId() == null) {
                throw new BusinessException("RECEIVING_LOCATION_REQUIRED", "Receiving storage location is required on line " + lineNo, HttpStatus.BAD_REQUEST);
            }

            // Load and validate location
            StorageLocation location = locationMap.computeIfAbsent(line.receivingLocationId(), id ->
                    storageLocationRepository.findById(id)
                            .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Storage location not found with id: " + id, HttpStatus.NOT_FOUND))
            );
            if (!location.isActive()) {
                throw new BusinessException("LOCATION_INACTIVE", "Storage location is inactive on line " + lineNo, HttpStatus.BAD_REQUEST);
            }
            if (!location.getStore().getId().equals(store.getId())) {
                throw new BusinessException(
                        "LOCATION_STORE_MISMATCH",
                        "Storage location " + location.getLocationCode() + " does not belong to store " + store.getStoreCode(),
                        HttpStatus.BAD_REQUEST
                );
            }

            // Load and validate item
            Item item = itemMap.computeIfAbsent(line.itemId(), id ->
                    itemRepository.findById(id)
                            .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found with id: " + id, HttpStatus.NOT_FOUND))
            );
            if (!item.isActive()) {
                throw new BusinessException("ITEM_INACTIVE", "Item " + item.getItemCode() + " is inactive", HttpStatus.BAD_REQUEST);
            }

            // Decimal scale validation
            Uom uom = item.getBaseUom();
            if (uom != null) {
                if (!uom.isDecimalAllowed()) {
                    if (line.receivedQty().remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                        throw new BusinessException(
                                "UOM_DECIMAL_NOT_ALLOWED",
                                "Decimal values not allowed for UOM: " + uom.getUomCode() + " on item: " + item.getItemCode(),
                                HttpStatus.BAD_REQUEST
                        );
                    }
                } else {
                    int effectiveScale = Math.max(0, line.receivedQty().stripTrailingZeros().scale());
                    if (effectiveScale > uom.getDecimalScale()) {
                        throw new BusinessException(
                                "UOM_SCALE_EXCEEDED",
                                "Received quantity scale (" + effectiveScale + ") exceeds maximum allowed scale (" +
                                        uom.getDecimalScale() + ") for UOM: " + uom.getUomCode(),
                                HttpStatus.BAD_REQUEST
                        );
                    }
                }
            }

            // Expiry date validation
            if (item.isExpiryTracking()) {
                if (line.expiryDate() == null) {
                    throw new BusinessException(
                            "EXPIRY_DATE_REQUIRED",
                            "Expiry date is mandatory for item with expiry tracking: " + item.getItemCode(),
                            HttpStatus.BAD_REQUEST
                    );
                }
                if (line.manufactureDate() != null && line.expiryDate().isBefore(line.manufactureDate())) {
                    throw new BusinessException(
                            "INVALID_EXPIRY_DATE",
                            "Expiry date cannot be before manufacture date on line " + lineNo,
                            HttpStatus.BAD_REQUEST
                    );
                }
            }

            // PO item line validation
            if (line.poItemRefId() != null) {
                if (poRef == null) {
                    throw new BusinessException(
                            "PO_REF_REQUIRED",
                            "GRN must have a purchase order reference when PO item reference is specified on line " + lineNo,
                            HttpStatus.BAD_REQUEST
                    );
                }
                PurchaseOrderItemRef poItem = poItemMap.computeIfAbsent(line.poItemRefId(), id ->
                        purchaseOrderItemRefRepository.findById(id)
                                .orElseThrow(() -> new BusinessException("PO_ITEM_NOT_FOUND", "PO item not found with id: " + id, HttpStatus.NOT_FOUND))
                );

                if (!poItem.getPurchaseOrderRef().getId().equals(poRef.getId())) {
                    throw new BusinessException(
                            "PO_ITEM_PO_MISMATCH",
                            "PO line does not belong to referenced PO: " + poRef.getPoNumber(),
                            HttpStatus.BAD_REQUEST
                    );
                }

                if (poItem.getItem() != null && !poItem.getItem().getId().equals(item.getId())) {
                    throw new BusinessException(
                            "PO_ITEM_ITEM_MISMATCH",
                            "GRN item does not match referenced PO item on line " + lineNo,
                            HttpStatus.BAD_REQUEST
                    );
                }

                BigDecimal totalPotentialReceived = poItem.getReceivedQty().add(line.receivedQty());
                if (totalPotentialReceived.compareTo(poItem.getOrderedQty()) > 0) {
                    throw new BusinessException(
                            "EXCEEDS_PO_QUANTITY",
                            "Received quantity (" + line.receivedQty() + ") + already received (" +
                                    poItem.getReceivedQty() + ") exceeds ordered quantity (" + poItem.getOrderedQty() +
                                    ") for PO item line " + poItem.getPoLineNo(),
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
        }

        return new ValidatedGrnContext(store, poRef, itemMap, locationMap, poItemMap);
    }
}
