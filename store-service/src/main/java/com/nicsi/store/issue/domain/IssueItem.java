package com.nicsi.store.issue.domain;

import com.nicsi.store.inventory.domain.InventoryLot;
import com.nicsi.store.inventory.domain.StockReservation;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.requisition.domain.RequisitionItem;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Issue line item — one stock movement row per issued item.
 * Maps to store.issue_item.
 */
@Entity
@Table(name = "issue_item", schema = "store")
public class IssueItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    private IssueHeader issue;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_item_id")
    private RequisitionItem requisitionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private StockReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private InventoryLot lot;

    @Column(name = "issue_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal issueQty;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(columnDefinition = "text")
    private String remarks;

    // ─── Getters & Setters ────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public IssueHeader getIssue() { return issue; }
    public void setIssue(IssueHeader issue) { this.issue = issue; }

    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }

    public RequisitionItem getRequisitionItem() { return requisitionItem; }
    public void setRequisitionItem(RequisitionItem requisitionItem) { this.requisitionItem = requisitionItem; }

    public StockReservation getReservation() { return reservation; }
    public void setReservation(StockReservation reservation) { this.reservation = reservation; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public StorageLocation getLocation() { return location; }
    public void setLocation(StorageLocation location) { this.location = location; }

    public InventoryLot getLot() { return lot; }
    public void setLot(InventoryLot lot) { this.lot = lot; }

    public BigDecimal getIssueQty() { return issueQty; }
    public void setIssueQty(BigDecimal issueQty) { this.issueQty = issueQty; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
