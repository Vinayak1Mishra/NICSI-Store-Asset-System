package com.nicsi.store.inventory.service;

import com.nicsi.store.common.audit.AuditEvent;
import com.nicsi.store.common.audit.AuditService;
import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.numbering.DocumentNumberService;
import com.nicsi.store.common.security.CurrentUserHolder;
import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.repository.InventoryLotRepository;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemStorePolicy;
import com.nicsi.store.master.domain.Uom;
import com.nicsi.store.master.repository.ItemStorePolicyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryPostingService {

    private final StockBalanceRepository stockBalanceRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final ItemStorePolicyRepository itemStorePolicyRepository;
    private final DocumentNumberService documentNumberService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public InventoryPostingService(
            StockBalanceRepository stockBalanceRepository,
            StockTransactionRepository stockTransactionRepository,
            InventoryLotRepository inventoryLotRepository,
            ItemStorePolicyRepository itemStorePolicyRepository,
            DocumentNumberService documentNumberService,
            AuditService auditService,
            JdbcTemplate jdbcTemplate
    ) {
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.itemStorePolicyRepository = itemStorePolicyRepository;
        this.documentNumberService = documentNumberService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Authoritative single gateway for posting to store.stock_balance and appending to store.stock_transaction.
     * Executes in active transaction (Propagation.REQUIRED).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public StockTransaction post(InventoryPostingRequest request) {
        Item item = request.item();

        // 1. Point 5: LICENSE-tracked / SOFTWARE items do NOT post to stock_balance or physical ledger
        if ("SOFTWARE".equalsIgnoreCase(item.getItemType()) || "LICENSE".equalsIgnoreCase(item.getTrackingType())) {
            return null;
        }

        // 2. Fast-path idempotency replay before any validation or locking.
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<StockTransaction> existing = stockTransactionRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        BigDecimal qtyIn = request.quantityIn() != null ? request.quantityIn() : BigDecimal.ZERO;
        BigDecimal qtyOut = request.quantityOut() != null ? request.quantityOut() : BigDecimal.ZERO;

        if (qtyIn.compareTo(BigDecimal.ZERO) <= 0 && qtyOut.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Either quantityIn or quantityOut must be greater than zero", HttpStatus.BAD_REQUEST);
        }
        if (qtyIn.compareTo(BigDecimal.ZERO) > 0 && qtyOut.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("INVALID_QUANTITY", "Cannot specify both quantityIn and quantityOut in a single transaction", HttpStatus.BAD_REQUEST);
        }

        // 3. UOM decimal validation
        validateUomScale(item, qtyIn.compareTo(BigDecimal.ZERO) > 0 ? qtyIn : qtyOut);

        // 4. Resolve or create lot if lot-tracked
        InventoryLot lot = resolveLot(request);
        UUID lotId = lot != null ? lot.getId() : null;

        // 5. Allocate the transaction number BEFORE the advisory lock.
        //
        // nextNumber() is INSERT ... ON CONFLICT DO UPDATE on store.document_sequence, so it takes
        // an ExclusiveLock on that one row and holds it until commit. It and the balance advisory
        // lock below are the only two lock resources a posting holds, so their RELATIVE order must
        // be identical in every transaction or a multi-line posting can deadlock:
        //
        //   T1 lines (A,B,C): advisory(A) held, then blocks on document_sequence
        //   T2 lines (B,C,A): document_sequence held, then blocks on advisory(C)
        //
        // Acquired in the opposite order that cycle forms. Taking document_sequence FIRST makes
        // the global order document_sequence -> advisory, which is acyclic for any number of lines
        // in any order, so no multi-line ordering can deadlock. The advisory key ordering
        // (item, location, lot, store) then keeps advisory-vs-advisory consistent.
        String transactionNo = documentNumberService.nextNumber("STOCK_TXN", "TXN");

        // 6. Re-check idempotency now the sequence row is held: a competing thread may have
        // committed the same key while we were queued. Re-reading here (rather than catching
        // a constraint violation) is required because PostgreSQL marks the transaction aborted
        // after a constraint error, so any later read in the same transaction would fail.
        // A racing duplicate that loses here consumes one STOCK_TXN number, leaving a gap in the
        // sequence. Gaps are harmless (document_sequence is monotonic, not gapless) and are
        // preferable to a deadlock.
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<StockTransaction> existing = stockTransactionRepository.findByIdempotencyKey(request.idempotencyKey().trim());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // 7. Serialise every posting that touches the same balance dimension tuple.
        // findByDimensionsForUpdate below is SELECT ... FOR UPDATE, which locks NOTHING when no
        // stock_balance row exists yet. Concurrent first-ever postings into the same dimensions
        // would therefore all miss, all build a new balance, and all but one die on
        // uq_stock_balance_dims. This transaction-scoped advisory lock covers the bootstrap case
        // that the row lock cannot.
        acquireBalanceWriteLock(item.getId(), request.store().getId(), request.location().getId(), lotId);

        // 8. Acquire pessimistic write lock on stock_balance
        StockBalance balance = stockBalanceRepository.findByDimensionsForUpdate(
                item.getId(), request.store().getId(), request.location().getId(), lotId
        ).orElseGet(() -> {
            if (qtyOut.compareTo(BigDecimal.ZERO) > 0) {
                boolean allowNegative = isNegativeStockAllowed(item.getId(), request.store().getId());
                if (!allowNegative) {
                    throw new BusinessException(
                            "INSUFFICIENT_STOCK",
                            "No stock available for item " + item.getItemCode() + " at location " + request.location().getLocationCode(),
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
            StockBalance newBalance = new StockBalance();
            newBalance.setItem(item);
            newBalance.setStore(request.store());
            newBalance.setLocation(request.location());
            newBalance.setLot(lot);
            newBalance.setOnHandQty(BigDecimal.ZERO);
            newBalance.setReservedQty(BigDecimal.ZERO);
            newBalance.setAvgUnitCost(BigDecimal.ZERO);
            return newBalance;
        });

        BigDecimal currentOnHand = balance.getOnHandQty() != null ? balance.getOnHandQty() : BigDecimal.ZERO;
        BigDecimal currentAvgCost = balance.getAvgUnitCost() != null ? balance.getAvgUnitCost() : BigDecimal.ZERO;
        BigDecimal unitCost = request.unitCost() != null ? request.unitCost() : BigDecimal.ZERO;

        BigDecimal newOnHand;
        BigDecimal newAvgCost;

        if (qtyIn.compareTo(BigDecimal.ZERO) > 0) {
            // Receipt / Adjustment In / Opening
            newOnHand = currentOnHand.add(qtyIn);
            if (newOnHand.compareTo(BigDecimal.ZERO) > 0) {
                // Weighted average formula: ((old_qty * old_avg) + (qty_in * unit_cost)) / (old_qty + qty_in)
                BigDecimal currentTotalValue = currentOnHand.multiply(currentAvgCost);
                BigDecimal incomingTotalValue = qtyIn.multiply(unitCost);
                newAvgCost = currentTotalValue.add(incomingTotalValue).divide(newOnHand, 4, RoundingMode.HALF_UP);
            } else {
                newAvgCost = unitCost;
            }
        } else {
            // Issue / Adjustment Out / Disposal
            newOnHand = currentOnHand.subtract(qtyOut);
            if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
                boolean allowNegative = isNegativeStockAllowed(item.getId(), request.store().getId());
                if (!allowNegative) {
                    throw new BusinessException(
                            "INSUFFICIENT_STOCK",
                            "Insufficient stock for item " + item.getItemCode() + ". Available on hand: " + currentOnHand + ", requested out: " + qtyOut,
                            HttpStatus.BAD_REQUEST
                    );
                }
            }
            newAvgCost = currentAvgCost;
            if (unitCost.compareTo(BigDecimal.ZERO) <= 0) {
                unitCost = currentAvgCost;
            }
        }

        // Update balance
        balance.setOnHandQty(newOnHand);
        balance.setAvgUnitCost(newAvgCost);
        balance.setUpdatedAt(Instant.now());
        stockBalanceRepository.save(balance);

        // 9. Record immutable transaction row
        BigDecimal txnQty = qtyIn.compareTo(BigDecimal.ZERO) > 0 ? qtyIn : qtyOut;
        BigDecimal totalCost = txnQty.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);

        UUID posterId = request.postedBy() != null ? request.postedBy() : CurrentUserHolder.getUserId();

        StockTransaction st = new StockTransaction();
        st.setTransactionNo(transactionNo);
        st.setMovementGroupId(request.movementGroupId() != null ? request.movementGroupId() : UUID.randomUUID());
        st.setTransactionType(request.transactionType());
        st.setTransactionTime(Instant.now());
        st.setItem(item);
        st.setStore(request.store());
        st.setLocation(request.location());
        st.setLot(lot);
        st.setQuantityIn(qtyIn);
        st.setQuantityOut(qtyOut);
        st.setUnitCost(unitCost);
        st.setTotalCost(totalCost);
        st.setReferenceType(request.referenceType());
        st.setReferenceId(request.referenceId());
        st.setReferenceNo(request.referenceNo());
        st.setReversalOfTransaction(request.reversalOfTransaction());
        st.setIdempotencyKey(request.idempotencyKey());
        st.setRemarks(request.remarks());
        st.setPostedBy(posterId);
        st.setPostedAt(Instant.now());

        StockTransaction savedTxn = stockTransactionRepository.save(st);

        // 10. Audit log in same transaction
        auditService.record(new AuditEvent(
                "INVENTORY", "POST", "STOCK_TRANSACTION",
                savedTxn.getId(), savedTxn.getTransactionNo(), null,
                savedTxn.getTransactionType() + ": " + txnQty + " of " + item.getItemCode() + " at " + request.location().getLocationCode(),
                "Stock ledger posting: " + savedTxn.getTransactionType()
        ));

        return savedTxn;
    }

    private void validateUomScale(Item item, BigDecimal quantity) {
        Uom uom = item.getBaseUom();
        if (uom == null) return;
        if (!uom.isDecimalAllowed()) {
            if (quantity.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessException(
                        "DECIMAL_QTY_DISALLOWED",
                        "Decimal quantities are not allowed for UOM " + uom.getUomCode() + " on item " + item.getItemCode(),
                        HttpStatus.BAD_REQUEST
                );
            }
        } else {
            int scale = quantity.stripTrailingZeros().scale();
            if (scale > 0 && scale > uom.getDecimalScale()) {
                throw new BusinessException(
                        "EXCESSIVE_DECIMAL_SCALE",
                        "Quantity decimal scale (" + scale + ") exceeds allowed scale (" + uom.getDecimalScale() + ") for UOM " + uom.getUomCode(),
                        HttpStatus.BAD_REQUEST
                );
            }
        }
    }

    private InventoryLot resolveLot(InventoryPostingRequest request) {
        if (request.lot() != null) {
            return request.lot();
        }
        Item item = request.item();
        boolean isLotTracked = "LOT".equalsIgnoreCase(item.getTrackingType()) || item.isExpiryTracking();
        String lotNumber = request.lotNumber();

        if (isLotTracked && lotNumber != null && !lotNumber.isBlank()) {
            String trimmedLot = lotNumber.trim();
            return inventoryLotRepository.findByItemIdAndStoreIdAndLotNumber(item.getId(), request.store().getId(), trimmedLot)
                    .orElseGet(() -> {
                        InventoryLot newLot = new InventoryLot();
                        newLot.setItem(item);
                        newLot.setStore(request.store());
                        newLot.setLotNumber(trimmedLot);
                        newLot.setManufactureDate(request.manufactureDate());
                        newLot.setExpiryDate(request.expiryDate());
                        newLot.setStatus("ACTIVE");
                        return inventoryLotRepository.save(newLot);
                    });
        }
        return null;
    }

    private boolean isNegativeStockAllowed(UUID itemId, UUID storeId) {
        return itemStorePolicyRepository.findByItemIdAndStoreId(itemId, storeId)
                .map(ItemStorePolicy::isAllowNegativeStock)
                .orElse(false);
    }

    /** Sentinel used by uq_stock_balance_dims to represent "no lot". */
    private static final String ZERO_UUID = "00000000-0000-0000-0000-000000000000";

    /**
     * Acquires a transaction-scoped advisory lock covering one balance dimension tuple.
     *
     * <p>Key derivation mirrors {@code uq_stock_balance_dims} exactly -- the tuple is
     * {@code (item_id, store_id, location_id, COALESCE(lot_id, zero-uuid))} -- so a null lot
     * produces byte-for-byte the same key the expression-based unique index would, and a lot
     * null is not confused with a real all-zero lot id.
     *
     * <p><b>Ordering code.</b> The key material is concatenated in the fixed component order
     * {@code item_id, location_id, lot_id, store_id}. This is the order the row locks are taken
     * in, and it is what makes a multi-line posting deadlock-free: because every line derives a
     * key from its components in this same fixed order, a caller that locks several dimensions
     * within one transaction by sorting on {@code (item_id, location_id, lot_id, store_id)}
     * acquires them in one global order, so two concurrent multi-line postings can never hold
     * each other's locks in opposite sequence. Sorting the caller's lines by this same tuple is
     * the caller's half of that contract; a single-line posting is trivially already ordered.
     *
     * <p>Transaction-scoped ({@code pg_advisory_xact_lock}), so it is released automatically on
     * commit or rollback and cannot leak across pooled connections.
     */
    private void acquireBalanceWriteLock(UUID itemId, UUID storeId, UUID locationId, UUID lotId) {
        // pg_advisory_xact_lock returns void, so the result set is executed and discarded.
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended("
                        + "  CAST(? AS uuid)::text || ':' || "   // 1. item_id
                        + "  CAST(? AS uuid)::text || ':' || "   // 2. location_id
                        + "  COALESCE(CAST(? AS uuid)::text, CAST(? AS uuid)::text) || ':' || "  // 3. lot_id (null -> zero-uuid)
                        + "  CAST(? AS uuid)::text, 0::bigint))", // 4. store_id
                rs -> null,
                itemId, locationId, lotId, ZERO_UUID, storeId);
    }
}
