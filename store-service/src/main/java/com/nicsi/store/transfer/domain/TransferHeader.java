package com.nicsi.store.transfer.domain;

import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transfer_header", schema = "store")
public class TransferHeader {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "transfer_no", nullable = false, unique = true, length = 60)
    private String transferNo;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate = LocalDate.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_store_id", nullable = false)
    private StoreSite sourceStore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_store_id", nullable = false)
    private StoreSite destinationStore;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "dispatch_date")
    private LocalDate dispatchDate;

    @Column(name = "receive_date")
    private LocalDate receiveDate;

    @Column(columnDefinition = "text")
    private String remarks;

    @OneToMany(mappedBy = "transferHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<TransferItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "updated_by")
    private UUID updatedBy;
    @Version @Column(name = "version", nullable = false)
    private Long version = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = Instant.now(); }

    public void addItem(TransferItem item) { items.add(item); item.setTransferHeader(this); }

    // Getters & Setters
    public UUID getId() { return id; }
    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String v) { this.transferNo = v; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate v) { this.transferDate = v; }
    public StoreSite getSourceStore() { return sourceStore; }
    public void setSourceStore(StoreSite v) { this.sourceStore = v; }
    public StoreSite getDestinationStore() { return destinationStore; }
    public void setDestinationStore(StoreSite v) { this.destinationStore = v; }
    public UUID getRequestedByUserId() { return requestedByUserId; }
    public void setRequestedByUserId(UUID v) { this.requestedByUserId = v; }
    public UUID getApprovedByUserId() { return approvedByUserId; }
    public void setApprovedByUserId(UUID v) { this.approvedByUserId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public LocalDate getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(LocalDate v) { this.dispatchDate = v; }
    public LocalDate getReceiveDate() { return receiveDate; }
    public void setReceiveDate(LocalDate v) { this.receiveDate = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public List<TransferItem> getItems() { return items; }
    public void setItems(List<TransferItem> v) { this.items = v; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID v) { this.updatedBy = v; }
    public Long getVersion() { return version; }
}
