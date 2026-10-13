package com.company.itam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assets")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Asset tag is required")
    @Column(nullable = false, unique = true)
    private String assetTag;

    @NotBlank(message = "Asset name is required")
    @Column(nullable = false)
    private String name;

    @NotNull(message = "Category is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetCategory category;

    private String manufacturer;

    private String model;

    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false)
    private AssetCondition condition;

    private BigDecimal purchaseCost;

    private LocalDate purchaseDate;

    private LocalDate warrantyExpiryDate;

    private String location;

    // Assignment details
    private String assignedEmployeeName;

    private String assignedEmployeeEmail;

    private String assignedDepartment;

    private LocalDate assignedDate;

    private LocalDate expectedReturnDate;

    private LocalDate lastAuditDate;

    private LocalDate nextAuditDate;

    @Column(length = 1000)
    private String checkoutNotes;

    @Column(length = 1000)
    private String checkinNotes;

    @Column(length = 2000)
    private String specifications;

    @Column(length = 2000)
    private String notes;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceRecord> maintenanceRecords = new ArrayList<>();

    public Asset() {
        this.status = AssetStatus.AVAILABLE;
        this.condition = AssetCondition.GOOD;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addMaintenanceRecord(MaintenanceRecord record) {
        maintenanceRecords.add(record);
        record.setAsset(this);
    }

    public void removeMaintenanceRecord(MaintenanceRecord record) {
        maintenanceRecords.remove(record);
        record.setAsset(null);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public void setAssetTag(String assetTag) {
        this.assetTag = assetTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public void setCategory(AssetCategory category) {
        this.category = category;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public void setStatus(AssetStatus status) {
        this.status = status;
    }

    public AssetCondition getCondition() {
        return condition;
    }

    public void setCondition(AssetCondition condition) {
        this.condition = condition;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public LocalDate getWarrantyExpiryDate() {
        return warrantyExpiryDate;
    }

    public void setWarrantyExpiryDate(LocalDate warrantyExpiryDate) {
        this.warrantyExpiryDate = warrantyExpiryDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getAssignedEmployeeName() {
        return assignedEmployeeName;
    }

    public void setAssignedEmployeeName(String assignedEmployeeName) {
        this.assignedEmployeeName = assignedEmployeeName;
    }

    public String getAssignedEmployeeEmail() {
        return assignedEmployeeEmail;
    }

    public void setAssignedEmployeeEmail(String assignedEmployeeEmail) {
        this.assignedEmployeeEmail = assignedEmployeeEmail;
    }

    public String getAssignedDepartment() {
        return assignedDepartment;
    }

    public void setAssignedDepartment(String assignedDepartment) {
        this.assignedDepartment = assignedDepartment;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public String getSpecifications() {
        return specifications;
    }

    public void setSpecifications(String specifications) {
        this.specifications = specifications;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDate getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(LocalDate expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public LocalDate getLastAuditDate() {
        return lastAuditDate;
    }

    public void setLastAuditDate(LocalDate lastAuditDate) {
        this.lastAuditDate = lastAuditDate;
    }

    public LocalDate getNextAuditDate() {
        return nextAuditDate;
    }

    public void setNextAuditDate(LocalDate nextAuditDate) {
        this.nextAuditDate = nextAuditDate;
    }

    public String getCheckoutNotes() {
        return checkoutNotes;
    }

    public void setCheckoutNotes(String checkoutNotes) {
        this.checkoutNotes = checkoutNotes;
    }

    public String getCheckinNotes() {
        return checkinNotes;
    }

    public void setCheckinNotes(String checkinNotes) {
        this.checkinNotes = checkinNotes;
    }

    public List<MaintenanceRecord> getMaintenanceRecords() {
        return maintenanceRecords;
    }

    public void setMaintenanceRecords(List<MaintenanceRecord> maintenanceRecords) {
        this.maintenanceRecords = maintenanceRecords;
    }
}
