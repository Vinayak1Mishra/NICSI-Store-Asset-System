package com.nicsi.store.inventory.service;

import com.nicsi.store.common.error.BusinessException;
import com.nicsi.store.common.web.PageResponse;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.inventory.dto.InventoryDto;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.ItemStorePolicy;
import com.nicsi.store.master.repository.ItemStorePolicyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class StockQueryService {

    /**
     * Stand-ins for an absent ledger date bound. StockTransactionRepository.search requires both
     * bounds to be non-null because a null bound cannot be passed through an "IS NULL" predicate
     * to PostgreSQL (see the comment on that query). Instant.EPOCH predates any possible ledger
     * row, and 2999-12-31 sits comfortably inside the PostgreSQL timestamptz range, so an omitted
     * bound still returns the full history rather than truncating it.
     */
    private static final Instant LEDGER_FROM = Instant.EPOCH;
    private static final Instant LEDGER_TO = Instant.parse("2999-12-31T23:59:59Z");

    private final StockBalanceRepository stockBalanceRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final ItemStorePolicyRepository itemStorePolicyRepository;

    public StockQueryService(
            StockBalanceRepository stockBalanceRepository,
            StockTransactionRepository stockTransactionRepository,
            ItemStorePolicyRepository itemStorePolicyRepository
    ) {
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.itemStorePolicyRepository = itemStorePolicyRepository;
    }

    public PageResponse<InventoryDto.BalanceResponse> searchBalances(
            UUID storeId, UUID categoryId, String search, Boolean lowStockOnly, Pageable pageable
    ) {
        Page<StockBalance> page;
        if (Boolean.TRUE.equals(lowStockOnly)) {
            page = stockBalanceRepository.findLowStock(pageable);
        } else {
            page = stockBalanceRepository.search(storeId, categoryId, search, pageable);
        }
        return PageResponse.from(page.map(this::toBalanceResponse));
    }

    public InventoryDto.BalanceResponse getBalance(UUID id) {
        StockBalance balance = stockBalanceRepository.findById(id)
                .orElseThrow(() -> new BusinessException("BALANCE_NOT_FOUND", "Stock balance not found with id: " + id, HttpStatus.NOT_FOUND));
        return toBalanceResponse(balance);
    }

    public PageResponse<InventoryDto.LedgerResponse> searchLedger(
            UUID itemId, UUID storeId, UUID locationId, String transactionType,
            Instant fromDate, Instant toDate, String search, Pageable pageable
    ) {
    Page<StockTransaction> page = stockTransactionRepository.search(
            itemId, storeId, locationId, transactionType,
            fromDate != null ? fromDate : LEDGER_FROM,
            toDate != null ? toDate : LEDGER_TO,
            search, pageable
    );
    return PageResponse.from(page.map(this::toLedgerResponse));
    }

    public PageResponse<InventoryDto.LowStockResponse> getLowStock(Pageable pageable) {
        Page<StockBalance> page = stockBalanceRepository.findLowStock(pageable);
        return PageResponse.from(page.map(this::toLowStockResponse));
    }

    public InventoryDto.BalanceResponse toBalanceResponse(StockBalance b) {
        Item item = b.getItem();
        ItemStorePolicy policy = itemStorePolicyRepository.findByItemIdAndStoreId(item.getId(), b.getStore().getId()).orElse(null);
        BigDecimal reorderLevel = policy != null ? policy.getReorderLevelQty() : BigDecimal.ZERO;
        boolean isLow = b.getAvailableQty().compareTo(reorderLevel) <= 0;

        return new InventoryDto.BalanceResponse(
                b.getId(),
                item.getId(),
                item.getItemCode(),
                item.getItemName(),
                item.getBaseUom() != null ? item.getBaseUom().getUomCode() : null,
                item.getCategory() != null ? item.getCategory().getId() : null,
                item.getCategory() != null ? item.getCategory().getCategoryName() : null,
                b.getStore().getId(),
                b.getStore().getStoreCode(),
                b.getStore().getStoreName(),
                b.getLocation().getId(),
                b.getLocation().getLocationCode(),
                b.getLocation().getLocationName(),
                b.getLot() != null ? b.getLot().getId() : null,
                b.getLot() != null ? b.getLot().getLotNumber() : null,
                b.getLot() != null ? b.getLot().getExpiryDate() : null,
                b.getOnHandQty(),
                b.getReservedQty(),
                b.getAvailableQty(),
                b.getAvgUnitCost(),
                b.getInventoryValue(),
                reorderLevel,
                isLow,
                b.getUpdatedAt(),
                b.getVersion()
        );
    }

    public InventoryDto.LedgerResponse toLedgerResponse(StockTransaction st) {
        return new InventoryDto.LedgerResponse(
                st.getId(),
                st.getTransactionNo(),
                st.getMovementGroupId(),
                st.getTransactionType(),
                st.getTransactionTime(),
                st.getItem().getId(),
                st.getItem().getItemCode(),
                st.getItem().getItemName(),
                st.getItem().getBaseUom() != null ? st.getItem().getBaseUom().getUomCode() : null,
                st.getStore().getId(),
                st.getStore().getStoreCode(),
                st.getStore().getStoreName(),
                st.getLocation().getId(),
                st.getLocation().getLocationCode(),
                st.getLocation().getLocationName(),
                st.getLot() != null ? st.getLot().getId() : null,
                st.getLot() != null ? st.getLot().getLotNumber() : null,
                st.getQuantityIn(),
                st.getQuantityOut(),
                st.getUnitCost(),
                st.getTotalCost(),
                st.getReferenceType(),
                st.getReferenceId(),
                st.getReferenceNo(),
                st.getIdempotencyKey(),
                st.getRemarks(),
                st.getPostedBy(),
                st.getPostedAt()
        );
    }

    public InventoryDto.LowStockResponse toLowStockResponse(StockBalance b) {
        Item item = b.getItem();
        ItemStorePolicy policy = itemStorePolicyRepository.findByItemIdAndStoreId(item.getId(), b.getStore().getId()).orElse(null);
        return new InventoryDto.LowStockResponse(
                item.getId(),
                item.getItemCode(),
                item.getItemName(),
                item.getBaseUom() != null ? item.getBaseUom().getUomCode() : null,
                b.getStore().getId(),
                b.getStore().getStoreCode(),
                b.getStore().getStoreName(),
                b.getOnHandQty(),
                b.getReservedQty(),
                b.getAvailableQty(),
                policy != null ? policy.getReorderLevelQty() : BigDecimal.ZERO,
                policy != null ? policy.getReorderQty() : BigDecimal.ZERO,
                policy != null ? policy.getMinStockQty() : BigDecimal.ZERO
        );
    }
}
