package com.nicsi.store.inventory.domain;

import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_transaction", schema = "store")
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "transaction_no", nullable = false, unique = true, length = 70, updatable = false)
    private String transactionNo;

    @Column(name = "movement_group_id", nullable = false, updatable = false)
    private UUID movementGroupId = UUID.randomUUID();

    @Column(name = "transaction_type", nullable = false, length = 30, updatable = false)
    private String transactionType;

    @Column(name = "transaction_time", nullable = false, updatable = false)
    private Instant transactionTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private StoreSite store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false, updatable = false)
    private StorageLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", updatable = false)
    private InventoryLot lot;

    @Column(name = "quantity_in", nullable = false, precision = 18, scale = 3, updatable = false)
    private BigDecimal quantityIn = BigDecimal.ZERO;

    @Column(name = "quantity_out", nullable = false, precision = 18, scale = 3, updatable = false)
    private BigDecimal quantityOut = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4, updatable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 20, scale = 2, updatable = false)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Column(name = "reference_no", length = 100, updatable = false)
    private String referenceNo;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(name = "custodian_user_id", updatable = false)
    private UUID custodianUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_txn_id", updatable = false)
    private StockTransaction reversalOfTransaction;

    @Column(name = "idempotency_key", length = 150, unique = true, updatable = false)
    private String idempotencyKey;

    @Column(columnDefinition = "text", updatable = false)
    private String remarks;

    @Column(name = "posted_by", nullable = false, updatable = false)
    private UUID postedBy;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    @PrePersist
    protected void onCreate() {
        if (transactionTime == null) transactionTime = Instant.now();
        if (postedAt == null) postedAt = Instant.now();
        if (movementGroupId == null) movementGroupId = UUID.randomUUID();
        if (quantityIn == null) quantityIn = BigDecimal.ZERO;
        if (quantityOut == null) quantityOut = BigDecimal.ZERO;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        if (totalCost == null) {
            BigDecimal qty = quantityIn.compareTo(BigDecimal.ZERO) > 0 ? quantityIn : quantityOut;
            totalCost = qty.multiply(unitCost).setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTransactionNo() {
        return transactionNo;
    }

    public void setTransactionNo(String transactionNo) {
        this.transactionNo = transactionNo;
    }

    public UUID getMovementGroupId() {
        return movementGroupId;
    }

    public void setMovementGroupId(UUID movementGroupId) {
        this.movementGroupId = movementGroupId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Instant getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(Instant transactionTime) {
        this.transactionTime = transactionTime;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public StoreSite getStore() {
        return store;
    }

    public void setStore(StoreSite store) {
        this.store = store;
    }

    public StorageLocation getLocation() {
        return location;
    }

    public void setLocation(StorageLocation location) {
        this.location = location;
    }

    public InventoryLot getLot() {
        return lot;
    }

    public void setLot(InventoryLot lot) {
        this.lot = lot;
    }

    public BigDecimal getQuantityIn() {
        return quantityIn;
    }

    public void setQuantityIn(BigDecimal quantityIn) {
        this.quantityIn = quantityIn;
    }

    public BigDecimal getQuantityOut() {
        return quantityOut;
    }

    public void setQuantityOut(BigDecimal quantityOut) {
        this.quantityOut = quantityOut;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(UUID referenceId) {
        this.referenceId = referenceId;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public void setReferenceNo(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getCustodianUserId() {
        return custodianUserId;
    }

    public void setCustodianUserId(UUID custodianUserId) {
        this.custodianUserId = custodianUserId;
    }

    public StockTransaction getReversalOfTransaction() {
        return reversalOfTransaction;
    }

    public void setReversalOfTransaction(StockTransaction reversalOfTransaction) {
        this.reversalOfTransaction = reversalOfTransaction;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public UUID getPostedBy() {
        return postedBy;
    }

    public void setPostedBy(UUID postedBy) {
        this.postedBy = postedBy;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }
}
