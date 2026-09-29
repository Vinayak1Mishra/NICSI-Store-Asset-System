package com.nicsi.store.license.domain;

import com.nicsi.store.master.domain.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "software_license", schema = "store")
public class SoftwareLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "license_code", nullable = false, length = 80, unique = true)
    private String licenseCode;

    @Column(name = "vendor_id", columnDefinition = "uuid")
    private UUID vendorId;

    @Column(name = "license_type", nullable = false, length = 30)
    private String licenseType;

    @Column(name = "entitlement_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal entitlementQty;

    @Column(name = "allocated_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal allocatedQty = BigDecimal.ZERO;

    @Column(name = "available_qty", precision = 18, scale = 3, insertable = false, updatable = false)
    private BigDecimal availableQty;

    @Column(name = "license_key_secret_ref", length = 300)
    private String licenseKeySecretRef;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "po_number_snapshot", length = 100)
    private String poNumberSnapshot;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, columnDefinition = "uuid")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "softwareLicense", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SoftwareLicenseAllocation> allocations = new ArrayList<>();

    public SoftwareLicense() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }

    public String getLicenseCode() { return licenseCode; }
    public void setLicenseCode(String licenseCode) { this.licenseCode = licenseCode; }

    public UUID getVendorId() { return vendorId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }

    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }

    public BigDecimal getEntitlementQty() { return entitlementQty; }
    public void setEntitlementQty(BigDecimal entitlementQty) { this.entitlementQty = entitlementQty; }

    public BigDecimal getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(BigDecimal allocatedQty) { this.allocatedQty = allocatedQty; }

    public BigDecimal getAvailableQty() { return availableQty; }

    public String getLicenseKeySecretRef() { return licenseKeySecretRef; }
    public void setLicenseKeySecretRef(String licenseKeySecretRef) { this.licenseKeySecretRef = licenseKeySecretRef; }

    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getPoNumberSnapshot() { return poNumberSnapshot; }
    public void setPoNumberSnapshot(String poNumberSnapshot) { this.poNumberSnapshot = poNumberSnapshot; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<SoftwareLicenseAllocation> getAllocations() { return allocations; }
    public void setAllocations(List<SoftwareLicenseAllocation> allocations) { this.allocations = allocations; }
}
