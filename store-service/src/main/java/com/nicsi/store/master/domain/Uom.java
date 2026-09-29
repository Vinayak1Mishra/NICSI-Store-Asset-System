package com.nicsi.store.master.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uom", schema = "store")
public class Uom {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "uom_code", length = 20, nullable = false, unique = true)
    private String uomCode;

    @Column(name = "uom_name", length = 80, nullable = false)
    private String uomName;

    @Column(name = "uom_type", length = 20, nullable = false)
    private String uomType;

    @Column(name = "decimal_allowed", nullable = false)
    private boolean decimalAllowed = false;

    @Column(name = "decimal_scale", nullable = false)
    private short decimalScale = 0;

    @Column(name = "description", length = 255)
    private String description;

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
    public String getUomCode() { return uomCode; }
    public void setUomCode(String uomCode) { this.uomCode = uomCode; }
    public String getUomName() { return uomName; }
    public void setUomName(String uomName) { this.uomName = uomName; }
    public String getUomType() { return uomType; }
    public void setUomType(String uomType) { this.uomType = uomType; }
    public boolean isDecimalAllowed() { return decimalAllowed; }
    public void setDecimalAllowed(boolean decimalAllowed) { this.decimalAllowed = decimalAllowed; }
    public short getDecimalScale() { return decimalScale; }
    public void setDecimalScale(short decimalScale) { this.decimalScale = decimalScale; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
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
