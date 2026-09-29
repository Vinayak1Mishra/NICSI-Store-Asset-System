package com.nicsi.store.license.domain;

import com.nicsi.store.asset.domain.Asset;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "software_license_allocation", schema = "store")
public class SoftwareLicenseAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "software_license_id", nullable = false)
    private SoftwareLicense softwareLicense;

    @Column(name = "allocation_type", nullable = false, length = 20)
    private String allocationType;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @Column(name = "server_identifier", length = 200)
    private String serverIdentifier;

    @Column(name = "quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "allocated_at", nullable = false)
    private Instant allocatedAt = Instant.now();

    @Column(name = "allocated_by", nullable = false, columnDefinition = "uuid")
    private UUID allocatedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by", columnDefinition = "uuid")
    private UUID releasedBy;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    public SoftwareLicenseAllocation() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public SoftwareLicense getSoftwareLicense() { return softwareLicense; }
    public void setSoftwareLicense(SoftwareLicense softwareLicense) { this.softwareLicense = softwareLicense; }

    public String getAllocationType() { return allocationType; }
    public void setAllocationType(String allocationType) { this.allocationType = allocationType; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Asset getAsset() { return asset; }
    public void setAsset(Asset asset) { this.asset = asset; }

    public String getServerIdentifier() { return serverIdentifier; }
    public void setServerIdentifier(String serverIdentifier) { this.serverIdentifier = serverIdentifier; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public Instant getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(Instant allocatedAt) { this.allocatedAt = allocatedAt; }

    public UUID getAllocatedBy() { return allocatedBy; }
    public void setAllocatedBy(UUID allocatedBy) { this.allocatedBy = allocatedBy; }

    public Instant getReleasedAt() { return releasedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }

    public UUID getReleasedBy() { return releasedBy; }
    public void setReleasedBy(UUID releasedBy) { this.releasedBy = releasedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
