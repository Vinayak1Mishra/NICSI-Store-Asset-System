package com.nicsi.store.master.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "store_site", schema = "store")
public class StoreSite {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "store_code", length = 30, nullable = false, unique = true)
    private String storeCode;

    @Column(name = "store_name", length = 150, nullable = false)
    private String storeName;

    @Column(name = "office_location_id")
    private UUID officeLocationId;

    @Column(name = "office_code_snapshot", length = 50)
    private String officeCodeSnapshot;

    @Column(name = "office_name_snapshot", length = 200)
    private String officeNameSnapshot;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "store_type", length = 30, nullable = false)
    private String storeType = "GENERAL";

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
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String storeCode) { this.storeCode = storeCode; }
    public String getStoreName() { return storeName; }
    public void setStoreName(String storeName) { this.storeName = storeName; }
    public UUID getOfficeLocationId() { return officeLocationId; }
    public void setOfficeLocationId(UUID officeLocationId) { this.officeLocationId = officeLocationId; }
    public String getOfficeCodeSnapshot() { return officeCodeSnapshot; }
    public void setOfficeCodeSnapshot(String officeCodeSnapshot) { this.officeCodeSnapshot = officeCodeSnapshot; }
    public String getOfficeNameSnapshot() { return officeNameSnapshot; }
    public void setOfficeNameSnapshot(String officeNameSnapshot) { this.officeNameSnapshot = officeNameSnapshot; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStoreType() { return storeType; }
    public void setStoreType(String storeType) { this.storeType = storeType; }
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
