package com.nicsi.store.asset.domain;

import com.nicsi.store.grn.domain.GrnItem;
import com.nicsi.store.master.domain.Item;
import com.nicsi.store.master.domain.StorageLocation;
import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "asset", schema = "store")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "asset_code", nullable = false, unique = true, length = 80)
    private String assetCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grn_item_id")
    private GrnItem grnItem;

    @Column(name = "issue_item_id")
    private UUID issueItemId;

    @Column(name = "serial_number", length = 150)
    private String serialNumber;

    @Column(length = 150)
    private String manufacturer;

    @Column(name = "model_number", length = 150)
    private String modelNumber;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String configuration;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", precision = 18, scale = 2)
    private BigDecimal purchaseCost;

    @Column(name = "po_number_snapshot", length = 100)
    private String poNumberSnapshot;

    @Column(name = "invoice_number_snapshot", length = 100)
    private String invoiceNumberSnapshot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private StorageLocation location;

    @Column(name = "current_custodian_user_id")
    private UUID currentCustodianUserId;

    @Column(name = "current_department_id")
    private UUID currentDepartmentId;

    @Column(name = "current_project_id")
    private UUID currentProjectId;

    @Column(name = "asset_status", nullable = false, length = 30)
    private String assetStatus = "AVAILABLE";

    @Column(name = "condition_status", nullable = false, length = 30)
    private String conditionStatus = "GOOD";

    @Column(name = "qr_code_value", nullable = false, unique = true, length = 180)
    private String qrCodeValue;

    @Column(name = "barcode_value", length = 180)
    private String barcodeValue;

    @Column(name = "warranty_start_date")
    private LocalDate warrantyStartDate;

    @Column(name = "warranty_end_date")
    private LocalDate warrantyEndDate;

    @Column(name = "capitalization_ref", length = 100)
    private String capitalizationRef;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (assetStatus == null) assetStatus = "AVAILABLE";
        if (conditionStatus == null) conditionStatus = "GOOD";
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public GrnItem getGrnItem() {
        return grnItem;
    }

    public void setGrnItem(GrnItem grnItem) {
        this.grnItem = grnItem;
    }

    public UUID getIssueItemId() {
        return issueItemId;
    }

    public void setIssueItemId(UUID issueItemId) {
        this.issueItemId = issueItemId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModelNumber() {
        return modelNumber;
    }

    public void setModelNumber(String modelNumber) {
        this.modelNumber = modelNumber;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public String getPoNumberSnapshot() {
        return poNumberSnapshot;
    }

    public void setPoNumberSnapshot(String poNumberSnapshot) {
        this.poNumberSnapshot = poNumberSnapshot;
    }

    public String getInvoiceNumberSnapshot() {
        return invoiceNumberSnapshot;
    }

    public void setInvoiceNumberSnapshot(String invoiceNumberSnapshot) {
        this.invoiceNumberSnapshot = invoiceNumberSnapshot;
    }

    public StoreSite getStore() {
        return store;
    }

    public void setStore(StoreSite store) {
        this.store = store;
    }

    public StorageLocation getLocation() {
        return location;
    }

    public void setLocation(StorageLocation location) {
        this.location = location;
    }

    public UUID getCurrentCustodianUserId() {
        return currentCustodianUserId;
    }

    public void setCurrentCustodianUserId(UUID currentCustodianUserId) {
        this.currentCustodianUserId = currentCustodianUserId;
    }

    public UUID getCurrentDepartmentId() {
        return currentDepartmentId;
    }

    public void setCurrentDepartmentId(UUID currentDepartmentId) {
        this.currentDepartmentId = currentDepartmentId;
    }

    public UUID getCurrentProjectId() {
        return currentProjectId;
    }

    public void setCurrentProjectId(UUID currentProjectId) {
        this.currentProjectId = currentProjectId;
    }

    public String getAssetStatus() {
        return assetStatus;
    }

    public void setAssetStatus(String assetStatus) {
        this.assetStatus = assetStatus;
    }

    public String getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(String conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public String getQrCodeValue() {
        return qrCodeValue;
    }

    public void setQrCodeValue(String qrCodeValue) {
        this.qrCodeValue = qrCodeValue;
    }

    public String getBarcodeValue() {
        return barcodeValue;
    }

    public void setBarcodeValue(String barcodeValue) {
        this.barcodeValue = barcodeValue;
    }

    public LocalDate getWarrantyStartDate() {
        return warrantyStartDate;
    }

    public void setWarrantyStartDate(LocalDate warrantyStartDate) {
        this.warrantyStartDate = warrantyStartDate;
    }

    public LocalDate getWarrantyEndDate() {
        return warrantyEndDate;
    }

    public void setWarrantyEndDate(LocalDate warrantyEndDate) {
        this.warrantyEndDate = warrantyEndDate;
    }

    public String getCapitalizationRef() {
        return capitalizationRef;
    }

    public void setCapitalizationRef(String capitalizationRef) {
        this.capitalizationRef = capitalizationRef;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(UUID updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
