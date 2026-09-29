package com.nicsi.store.master.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "item", schema = "store")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "item_code", length = 60, nullable = false, unique = true)
    private String itemCode;

    @Column(name = "item_name", length = 200, nullable = false)
    private String itemName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ItemCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private ItemSubcategory subcategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_uom_id", nullable = false)
    private Uom baseUom;

    @Column(name = "item_type", length = 30, nullable = false)
    private String itemType;

    @Column(name = "tracking_type", length = 20, nullable = false)
    private String trackingType;

    @Column(name = "short_description", length = 500)
    private String shortDescription;

    @Column(name = "specification", columnDefinition = "text")
    private String specification;

    @Column(name = "manufacturer_default", length = 150)
    private String manufacturerDefault;

    @Column(name = "model_default", length = 150)
    private String modelDefault;

    @Column(name = "hsn_sac_code", length = 30)
    private String hsnSacCode;

    @Column(name = "standard_rate", precision = 18, scale = 2)
    private BigDecimal standardRate;

    @Column(name = "useful_life_months")
    private Integer usefulLifeMonths;

    @Column(name = "warranty_months")
    private Integer warrantyMonths;

    @Column(name = "returnable", nullable = false)
    private boolean returnable = false;

    @Column(name = "warranty_applicable", nullable = false)
    private boolean warrantyApplicable = false;

    @Column(name = "expiry_tracking", nullable = false)
    private boolean expiryTracking = false;

    @Column(name = "asset_required", nullable = false)
    private boolean assetRequired = false;

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
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public ItemCategory getCategory() { return category; }
    public void setCategory(ItemCategory category) { this.category = category; }
    public ItemSubcategory getSubcategory() { return subcategory; }
    public void setSubcategory(ItemSubcategory subcategory) { this.subcategory = subcategory; }
    public Uom getBaseUom() { return baseUom; }
    public void setBaseUom(Uom baseUom) { this.baseUom = baseUom; }
    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }
    public String getTrackingType() { return trackingType; }
    public void setTrackingType(String trackingType) { this.trackingType = trackingType; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }
    public String getManufacturerDefault() { return manufacturerDefault; }
    public void setManufacturerDefault(String manufacturerDefault) { this.manufacturerDefault = manufacturerDefault; }
    public String getModelDefault() { return modelDefault; }
    public void setModelDefault(String modelDefault) { this.modelDefault = modelDefault; }
    public String getHsnSacCode() { return hsnSacCode; }
    public void setHsnSacCode(String hsnSacCode) { this.hsnSacCode = hsnSacCode; }
    public BigDecimal getStandardRate() { return standardRate; }
    public void setStandardRate(BigDecimal standardRate) { this.standardRate = standardRate; }
    public Integer getUsefulLifeMonths() { return usefulLifeMonths; }
    public void setUsefulLifeMonths(Integer usefulLifeMonths) { this.usefulLifeMonths = usefulLifeMonths; }
    public Integer getWarrantyMonths() { return warrantyMonths; }
    public void setWarrantyMonths(Integer warrantyMonths) { this.warrantyMonths = warrantyMonths; }
    public boolean isReturnable() { return returnable; }
    public void setReturnable(boolean returnable) { this.returnable = returnable; }
    public boolean isWarrantyApplicable() { return warrantyApplicable; }
    public void setWarrantyApplicable(boolean warrantyApplicable) { this.warrantyApplicable = warrantyApplicable; }
    public boolean isExpiryTracking() { return expiryTracking; }
    public void setExpiryTracking(boolean expiryTracking) { this.expiryTracking = expiryTracking; }
    public boolean isAssetRequired() { return assetRequired; }
    public void setAssetRequired(boolean assetRequired) { this.assetRequired = assetRequired; }
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
