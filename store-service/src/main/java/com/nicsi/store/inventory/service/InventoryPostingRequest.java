package com.nicsi.store.inventory.service;

import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockTransaction;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryPostingRequest(
        String transactionType,
        Item item,
        StoreSite store,
        StorageLocation location,
        InventoryLot lot,
        String lotNumber,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        BigDecimal quantityIn,
        BigDecimal quantityOut,
        BigDecimal unitCost,
        String referenceType,
        UUID referenceId,
        String referenceNo,
        UUID movementGroupId,
        StockTransaction reversalOfTransaction,
        String idempotencyKey,
        String remarks,
        UUID postedBy
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String transactionType;
        private Item item;
        private StoreSite store;
        private StorageLocation location;
        private InventoryLot lot;
        private String lotNumber;
        private LocalDate manufactureDate;
        private LocalDate expiryDate;
        private BigDecimal quantityIn = BigDecimal.ZERO;
        private BigDecimal quantityOut = BigDecimal.ZERO;
        private BigDecimal unitCost = BigDecimal.ZERO;
        private String referenceType;
        private UUID referenceId;
        private String referenceNo;
        private UUID movementGroupId;
        private StockTransaction reversalOfTransaction;
        private String idempotencyKey;
        private String remarks;
        private UUID postedBy;

        public Builder transactionType(String transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public Builder item(Item item) {
            this.item = item;
            return this;
        }

        public Builder store(StoreSite store) {
            this.store = store;
            return this;
        }

        public Builder location(StorageLocation location) {
            this.location = location;
            return this;
        }

        public Builder lot(InventoryLot lot) {
            this.lot = lot;
            return this;
        }

        public Builder lotNumber(String lotNumber) {
            this.lotNumber = lotNumber;
            return this;
        }

        public Builder manufactureDate(LocalDate manufactureDate) {
            this.manufactureDate = manufactureDate;
            return this;
        }

        public Builder expiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder quantityIn(BigDecimal quantityIn) {
            this.quantityIn = quantityIn;
            return this;
        }

        public Builder quantityOut(BigDecimal quantityOut) {
            this.quantityOut = quantityOut;
            return this;
        }

        public Builder unitCost(BigDecimal unitCost) {
            this.unitCost = unitCost;
            return this;
        }

        public Builder referenceType(String referenceType) {
            this.referenceType = referenceType;
            return this;
        }

        public Builder referenceId(UUID referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public Builder referenceNo(String referenceNo) {
            this.referenceNo = referenceNo;
            return this;
        }

        public Builder movementGroupId(UUID movementGroupId) {
            this.movementGroupId = movementGroupId;
            return this;
        }

        public Builder reversalOfTransaction(StockTransaction reversalOfTransaction) {
            this.reversalOfTransaction = reversalOfTransaction;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Builder remarks(String remarks) {
            this.remarks = remarks;
            return this;
        }

        public Builder postedBy(UUID postedBy) {
            this.postedBy = postedBy;
            return this;
        }

        public InventoryPostingRequest build() {
            return new InventoryPostingRequest(
                    transactionType, item, store, location, lot, lotNumber,
                    manufactureDate, expiryDate, quantityIn, quantityOut, unitCost,
                    referenceType, referenceId, referenceNo, movementGroupId,
                    reversalOfTransaction, idempotencyKey, remarks, postedBy
            );
        }
    }
}
