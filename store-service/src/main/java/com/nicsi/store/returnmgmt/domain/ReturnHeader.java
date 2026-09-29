package com.nicsi.store.returnmgmt.domain;

import com.nicsi.store.master.domain.StoreSite;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Return Header — controlling document for item & asset returns to the store.
 * Maps to store.return_header.
 *
 * Statuses: DRAFT -> SUBMITTED -> RECEIVED / INSPECTED -> POSTED (or REJECTED / CANCELLED)
 */
@Entity
@Table(name = "return_header", schema = "store")
public class ReturnHeader {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "return_no", nullable = false, unique = true, length = 60)
    private String returnNo;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate = LocalDate.now();

    @Column(name = "returned_by_user_id")
    private UUID returnedByUserId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "project_id")
    private UUID projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreSite store;

    @Column(name = "received_by_user_id")
    private UUID receivedByUserId;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(columnDefinition = "text")
    private String remarks;

    @OneToMany(mappedBy = "returnHeader", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<ReturnItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    public ReturnHeader() {}

    public void addItem(ReturnItem item) {
        items.add(item);
        item.setReturnHeader(this);
    }

    public void removeItem(ReturnItem item) {
        items.remove(item);
        item.setReturnHeader(null);
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public void setReturnNo(String returnNo) {
        this.returnNo = returnNo;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public UUID getReturnedByUserId() {
        return returnedByUserId;
    }

    public void setReturnedByUserId(UUID returnedByUserId) {
        this.returnedByUserId = returnedByUserId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public StoreSite getStore() {
        return store;
    }

    public void setStore(StoreSite store) {
        this.store = store;
    }

    public UUID getReceivedByUserId() {
        return receivedByUserId;
    }

    public void setReceivedByUserId(UUID receivedByUserId) {
        this.receivedByUserId = receivedByUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    public List<ReturnItem> getItems() {
        return items;
    }

    public void setItems(List<ReturnItem> items) {
        this.items = items;
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
