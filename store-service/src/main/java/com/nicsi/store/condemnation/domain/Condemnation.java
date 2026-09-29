package com.nicsi.store.condemnation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "condemnation", schema = "store")
public class Condemnation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "condemnation_no", nullable = false, unique = true, length = 60)
    private String condemnationNo;

    @Column(name = "proposal_date", nullable = false)
    private LocalDate proposalDate = LocalDate.now();

    @Column(name = "committee_reference", length = 150)
    private String committeeReference;

    @Column(name = "technical_reason", nullable = false, columnDefinition = "text")
    private String technicalReason;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @OneToMany(mappedBy = "condemnation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CondemnationItem> items = new ArrayList<>();

    public void addItem(CondemnationItem item) {
        items.add(item);
        item.setCondemnation(this);
    }

    public UUID getId() { return id; }
    public String getCondemnationNo() { return condemnationNo; }
    public void setCondemnationNo(String condemnationNo) { this.condemnationNo = condemnationNo; }
    public LocalDate getProposalDate() { return proposalDate; }
    public void setProposalDate(LocalDate proposalDate) { this.proposalDate = proposalDate; }
    public String getCommitteeReference() { return committeeReference; }
    public void setCommitteeReference(String committeeReference) { this.committeeReference = committeeReference; }
    public String getTechnicalReason() { return technicalReason; }
    public void setTechnicalReason(String technicalReason) { this.technicalReason = technicalReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public List<CondemnationItem> getItems() { return items; }
}
