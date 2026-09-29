package com.nicsi.store.verification.domain;

import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "physical_verification", schema = "store")
public class PhysicalVerification {
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "verification_no", nullable = false, unique = true, length = 60)
    private String verificationNo;

    @Column(name = "verification_name", nullable = false, length = 200)
    private String verificationName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @Column(name = "verification_type", nullable = false, length = 20)
    private String verificationType;

    @Column(name = "snapshot_time")
    private Instant snapshotTime;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "committee_reference", length = 150)
    private String committeeReference;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @OneToMany(mappedBy = "verification", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PhysicalVerificationItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public void addItem(PhysicalVerificationItem item) { items.add(item); item.setVerification(this); }

    public UUID getId() { return id; }
    public String getVerificationNo() { return verificationNo; }
    public void setVerificationNo(String v) { this.verificationNo = v; }
    public String getVerificationName() { return verificationName; }
    public void setVerificationName(String v) { this.verificationName = v; }
    public StoreSite getStore() { return store; }
    public void setStore(StoreSite v) { this.store = v; }
    public String getVerificationType() { return verificationType; }
    public void setVerificationType(String v) { this.verificationType = v; }
    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant v) { this.snapshotTime = v; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate v) { this.startDate = v; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate v) { this.endDate = v; }
    public String getCommitteeReference() { return committeeReference; }
    public void setCommitteeReference(String v) { this.committeeReference = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public List<PhysicalVerificationItem> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID v) { this.createdBy = v; }
}
