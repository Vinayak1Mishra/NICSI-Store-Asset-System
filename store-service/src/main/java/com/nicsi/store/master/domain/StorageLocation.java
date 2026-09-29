package com.nicsi.store.master.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_location", schema = "store")
public class StorageLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_location_id")
    private StorageLocation parentLocation;

    @Column(name = "location_code", length = 50, nullable = false)
    private String locationCode;

    @Column(name = "location_name", length = 150, nullable = false)
    private String locationName;

    @Column(name = "location_type", length = 20, nullable = false)
    private String locationType;

    @Column(name = "barcode_value", length = 120)
    private String barcodeValue;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public StoreSite getStore() { return store; }
    public void setStore(StoreSite store) { this.store = store; }
    public StorageLocation getParentLocation() { return parentLocation; }
    public void setParentLocation(StorageLocation parentLocation) { this.parentLocation = parentLocation; }
    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getLocationType() { return locationType; }
    public void setLocationType(String locationType) { this.locationType = locationType; }
    public String getBarcodeValue() { return barcodeValue; }
    public void setBarcodeValue(String barcodeValue) { this.barcodeValue = barcodeValue; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
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
}
