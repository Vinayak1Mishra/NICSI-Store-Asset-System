package com.nicsi.store.inventory.service;

import com.nicsi.store.asset.repository.AssetRepository;
import com.nicsi.store.inventory.domain.StockBalance;
import com.nicsi.store.inventory.dto.InventoryDto;
import com.nicsi.store.inventory.repository.StockBalanceRepository;
import com.nicsi.store.inventory.repository.StockTransactionRepository;
import com.nicsi.store.master.domain.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReconciliationService {

    private final StockBalanceRepository stockBalanceRepository;
    private final StockTransactionRepository stockTransactionRepository;
    private final AssetRepository assetRepository;

    public ReconciliationService(
            StockBalanceRepository stockBalanceRepository,
            StockTransactionRepository stockTransactionRepository,
            AssetRepository assetRepository
    ) {
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockTransactionRepository = stockTransactionRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Executes mathematical reconciliation between:
     * 1. Immutable ledger sum (quantityIn - quantityOut) vs stock_balance.on_hand_qty
     * 2. Physical serialised stock (on_hand_qty) vs digital active assets count (AVAILABLE)
     */
    @Transactional(readOnly = true)
    public InventoryDto.ReconciliationResponse reconcile() {
        List<StockBalance> balances = stockBalanceRepository.findAllForReconciliation();
        long totalTxnCount = stockTransactionRepository.count();
        long totalAssetCount = assetRepository.count();

        List<InventoryDto.Discrepancy> discrepancies = new ArrayList<>();

        for (StockBalance sb : balances) {
            Item item = sb.getItem();
            UUID lotId = sb.getLot() != null ? sb.getLot().getId() : null;

            // Check 1: Ledger vs Stock Balance
            BigDecimal calculatedLedgerNet = stockTransactionRepository.calculateNetBalance(
                    item.getId(), sb.getStore().getId(), sb.getLocation().getId(), lotId
            );
            if (calculatedLedgerNet == null) calculatedLedgerNet = BigDecimal.ZERO;
            BigDecimal onHand = sb.getOnHandQty() != null ? sb.getOnHandQty() : BigDecimal.ZERO;

            if (calculatedLedgerNet.compareTo(onHand) != 0) {
                discrepancies.add(new InventoryDto.Discrepancy(
                        "LEDGER_BALANCE_MISMATCH",
                        item.getId(),
                        item.getItemCode(),
                        item.getItemName(),
                        sb.getStore().getId(),
                        sb.getStore().getStoreCode(),
                        sb.getLocation().getId(),
                        sb.getLocation().getLocationCode(),
                        calculatedLedgerNet,
                        onHand,
                        onHand,
                        null,
                        "Ledger total (" + calculatedLedgerNet + ") does not match stock balance on hand (" + onHand + ")"
                ));
            }

            // Check 2: Serialised stock vs Active Asset count
            boolean isSerialised = "SERIAL".equalsIgnoreCase(item.getTrackingType()) || item.isAssetRequired();
            if (isSerialised) {
                long activeAssets = assetRepository.countByItemIdAndStoreIdAndLocationIdAndAssetStatus(
                        item.getId(), sb.getStore().getId(), sb.getLocation().getId(), "AVAILABLE"
                );
                if (BigDecimal.valueOf(activeAssets).compareTo(onHand) != 0) {
                    discrepancies.add(new InventoryDto.Discrepancy(
                            "SERIALISED_ASSET_COUNT_MISMATCH",
                            item.getId(),
                            item.getItemCode(),
                            item.getItemName(),
                            sb.getStore().getId(),
                            sb.getStore().getStoreCode(),
                            sb.getLocation().getId(),
                            sb.getLocation().getLocationCode(),
                            calculatedLedgerNet,
                            onHand,
                            onHand,
                            activeAssets,
                            "Active available asset count (" + activeAssets + ") does not match on-hand stock (" + onHand + ")"
                    ));
                }
            }
        }

        return new InventoryDto.ReconciliationResponse(
                discrepancies.isEmpty(),
                Instant.now(),
                balances.size(),
                (int) totalTxnCount,
                (int) totalAssetCount,
                discrepancies
        );
    }
}
