package com.nicsi.store.inventory.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockReservation;
import com.nicsi.store.inventory.dto.InventoryDto;
import com.nicsi.store.inventory.repository.InventoryLotRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockReservationRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import com.nicsi.store.master.repository.StorageLocationRepository;
import com.nicsi.store.master.repository.StoreSiteRepository;
import com.nicsi.store.requisition.domain.RequisitionItem;
import com.nicsi.store.requisition.repository.RequisitionItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReservationService {

    private final StockReservationRepository reservationRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final RequisitionItemRepository requisitionItemRepository;
    private final StoreSiteRepository storeSiteRepository;
    private final StorageLocationRepository storageLocationRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;

    public ReservationService(
            StockReservationRepository reservationRepository,
            StockBalanceRepository stockBalanceRepository,
            RequisitionItemRepository requisitionItemRepository,
            StoreSiteRepository storeSiteRepository,
            StorageLocationRepository storageLocationRepository,
            InventoryLotRepository inventoryLotRepository,
            DocumentNumberService documentNumberService,
            AuditService auditService
    ) {
        this.reservationRepository = reservationRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.requisitionItemRepository = requisitionItemRepository;
        this.storeSiteRepository = storeSiteRepository;
        this.storageLocationRepository = storageLocationRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
    }

    /**
     * Reserves stock for an approved requisition item.
     * Executes atomically under Propagation.REQUIRED.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public InventoryDto.ReservationResponse reserveStock(
            UUID requisitionItemId, UUID storeId, UUID locationId, UUID lotId, BigDecimal qty, Instant expiresAt
    ) {
        RequisitionItem reqItem = requisitionItemRepository.findById(requisitionItemId)
                .orElseThrow(() -> new BusinessException("REQUISITION_ITEM_NOT_FOUND", "Requisition item not found: " + requisitionItemId, HttpStatus.NOT_FOUND));

        Item item = reqItem.getItem();
        StoreSite store = storeSiteRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException("STORE_NOT_FOUND", "Store not found: " + storeId, HttpStatus.NOT_FOUND));
        StorageLocation location = storageLocationRepository.findById(locationId)
                .orElseThrow(() -> new BusinessException("LOCATION_NOT_FOUND", "Location not found: " + locationId, HttpStatus.NOT_FOUND));

        InventoryLot lot = lotId != null ? inventoryLotRepository.findById(lotId).orElse(null) : null;

        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Reservation quantity must be greater than zero", HttpStatus.BAD_REQUEST);
        }

        // Lock stock balance row
        StockBalance balance = stockBalanceRepository.findByDimensionsForUpdate(item.getId(), store.getId(), location.getId(), lotId)
                .orElseThrow(() -> new BusinessException("INSUFFICIENT_STOCK", "No stock balance found for reservation at location: " + location.getLocationCode(), HttpStatus.BAD_REQUEST));

        BigDecimal onHand = balance.getOnHandQty() != null ? balance.getOnHandQty() : BigDecimal.ZERO;
        BigDecimal currentReserved = balance.getReservedQty() != null ? balance.getReservedQty() : BigDecimal.ZERO;
        BigDecimal available = onHand.subtract(currentReserved);

        if (available.compareTo(qty) < 0) {
            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Insufficient available stock for item " + item.getItemCode() + ". Available: " + available + ", Requested reservation: " + qty,
                    HttpStatus.BAD_REQUEST
            );
        }

        // Update reserved quantity on balance
        balance.setReservedQty(currentReserved.add(qty));
        balance.setUpdatedAt(Instant.now());
        stockBalanceRepository.save(balance);

        UUID currentUserId = CurrentUserHolder.getUserId();
        String resNo = documentNumberService.nextNumber("STOCK_RES", "RES");

        StockReservation reservation = new StockReservation();
        reservation.setReservationNo(resNo);
        reservation.setRequisitionItem(reqItem);
        reservation.setItem(item);
        reservation.setStore(store);
        reservation.setLocation(location);
        reservation.setLot(lot);
        reservation.setReservedQty(qty);
        reservation.setConsumedQty(BigDecimal.ZERO);
        reservation.setStatus("ACTIVE");
        reservation.setExpiresAt(expiresAt);
        reservation.setCreatedBy(currentUserId);

        reqItem.setLineStatus("RESERVED");
        requisitionItemRepository.save(reqItem);

        StockReservation saved = reservationRepository.save(reservation);

        auditService.record(new AuditEvent(
                "INVENTORY", "RESERVE", "STOCK_RESERVATION",
                saved.getId(), saved.getReservationNo(), null,
                "Stock reserved: " + qty + " of " + item.getItemCode() + " for Requisition " + reqItem.getRequisition().getRequisitionNo(),
                "Stock reservation created"
        ));

        return toResponse(saved);
    }

    /**
     * Consumes reserved stock during issue.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void consumeReservation(UUID reservationId, BigDecimal qtyToConsume) {
        StockReservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId, HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equals(res.getStatus()) && !"PARTIALLY_CONSUMED".equals(res.getStatus())) {
            throw new BusinessException("INVALID_RESERVATION_STATUS", "Reservation is not active: " + res.getStatus(), HttpStatus.BAD_REQUEST);
        }

        BigDecimal remaining = res.getReservedQty().subtract(res.getConsumedQty());
        if (qtyToConsume.compareTo(remaining) > 0) {
            throw new BusinessException("EXCEEDED_RESERVATION", "Cannot consume " + qtyToConsume + ", only " + remaining + " remaining", HttpStatus.BAD_REQUEST);
        }

        StockBalance balance = stockBalanceRepository.findByDimensionsForUpdate(
                res.getItem().getId(), res.getStore().getId(), res.getLocation().getId(), res.getLot() != null ? res.getLot().getId() : null
        ).orElseThrow(() -> new BusinessException("BALANCE_NOT_FOUND", "Balance not found for reservation", HttpStatus.INTERNAL_SERVER_ERROR));

        // Decrement reserved_qty on balance
        BigDecimal newReserved = balance.getReservedQty().subtract(qtyToConsume);
        balance.setReservedQty(newReserved.compareTo(BigDecimal.ZERO) >= 0 ? newReserved : BigDecimal.ZERO);
        balance.setUpdatedAt(Instant.now());
        stockBalanceRepository.save(balance);

        BigDecimal newConsumed = res.getConsumedQty().add(qtyToConsume);
        res.setConsumedQty(newConsumed);
        if (newConsumed.compareTo(res.getReservedQty()) >= 0) {
            res.setStatus("CONSUMED");
        } else {
            res.setStatus("PARTIALLY_CONSUMED");
        }
        reservationRepository.save(res);
    }

    /**
     * Releases active reservation back to available stock.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public InventoryDto.ReservationResponse releaseReservation(UUID reservationId, String reason) {
        StockReservation res = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException("RESERVATION_NOT_FOUND", "Reservation not found: " + reservationId, HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equals(res.getStatus()) && !"PARTIALLY_CONSUMED".equals(res.getStatus())) {
            throw new BusinessException("INVALID_RESERVATION_STATUS", "Only ACTIVE or PARTIALLY_CONSUMED reservation can be released. Current: " + res.getStatus(), HttpStatus.BAD_REQUEST);
        }

        BigDecimal unconsumed = res.getReservedQty().subtract(res.getConsumedQty());
        if (unconsumed.compareTo(BigDecimal.ZERO) > 0) {
            stockBalanceRepository.findByDimensionsForUpdate(
                    res.getItem().getId(), res.getStore().getId(), res.getLocation().getId(), res.getLot() != null ? res.getLot().getId() : null
            ).ifPresent(balance -> {
                BigDecimal newReserved = balance.getReservedQty().subtract(unconsumed);
                balance.setReservedQty(newReserved.compareTo(BigDecimal.ZERO) >= 0 ? newReserved : BigDecimal.ZERO);
                balance.setUpdatedAt(Instant.now());
                stockBalanceRepository.save(balance);
            });
        }

        UUID currentUserId = CurrentUserHolder.getUserId();
        res.setStatus("RELEASED");
        res.setReleasedAt(Instant.now());
        res.setReleasedBy(currentUserId);
        StockReservation saved = reservationRepository.save(res);

        RequisitionItem reqItem = res.getRequisitionItem();
        if (reqItem != null && "RESERVED".equals(reqItem.getLineStatus())) {
            reqItem.setLineStatus("APPROVED");
            requisitionItemRepository.save(reqItem);
        }

        auditService.record(new AuditEvent(
                "INVENTORY", "RELEASE", "STOCK_RESERVATION",
                saved.getId(), saved.getReservationNo(), "ACTIVE", "RELEASED",
                "Reservation released: " + unconsumed + " units of " + res.getItem().getItemCode() + ". Reason: " + reason
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryDto.ReservationResponse> search(UUID storeId, UUID itemId, String status, Pageable pageable) {
        Page<StockReservation> page = reservationRepository.search(storeId, itemId, status, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    public InventoryDto.ReservationResponse toResponse(StockReservation r) {
        return new InventoryDto.ReservationResponse(
                r.getId(),
                r.getReservationNo(),
                r.getRequisitionItem().getId(),
                r.getRequisitionItem().getRequisition().getId(),
                r.getRequisitionItem().getRequisition().getRequisitionNo(),
                r.getItem().getId(),
                r.getItem().getItemCode(),
                r.getItem().getItemName(),
                r.getItem().getBaseUom() != null ? r.getItem().getBaseUom().getUomCode() : null,
                r.getStore().getId(),
                r.getStore().getStoreCode(),
                r.getStore().getStoreName(),
                r.getLocation() != null ? r.getLocation().getId() : null,
                r.getLocation() != null ? r.getLocation().getLocationCode() : null,
                r.getReservedQty(),
                r.getConsumedQty(),
                r.getStatus(),
                r.getExpiresAt(),
                r.getCreatedAt(),
                r.getCreatedBy(),
                r.getReleasedAt(),
                r.getReleasedBy()
        );
    }
}
