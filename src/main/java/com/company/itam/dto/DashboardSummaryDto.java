package com.company.itam.dto;

import com.company.itam.model.ActivityLog;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DashboardSummaryDto {
    private long totalAssets;
    private long availableAssets;
    private long assignedAssets;
    private long maintenanceAssets;
    private long retiredAssets;
    private BigDecimal totalAssetValue;
    private long expiringWarrantiesCount;
    private Map<String, Long> assetsByCategory;
    private Map<String, Long> assetsByDepartment;

    // Snipe-IT Metrics
    private long totalAccessories;
    private long totalConsumables;
    private long totalComponents;
    private long lowStockAccessoriesCount;
    private long lowStockConsumablesCount;
    private List<ActivityLog> recentActivity = new ArrayList<>();

    public long getTotalAssets() {
        return totalAssets;
    }

    public void setTotalAssets(long totalAssets) {
        this.totalAssets = totalAssets;
    }

    public long getAvailableAssets() {
        return availableAssets;
    }

    public void setAvailableAssets(long availableAssets) {
        this.availableAssets = availableAssets;
    }

    public long getAssignedAssets() {
        return assignedAssets;
    }

    public void setAssignedAssets(long assignedAssets) {
        this.assignedAssets = assignedAssets;
    }

    public long getMaintenanceAssets() {
        return maintenanceAssets;
    }

    public void setMaintenanceAssets(long maintenanceAssets) {
        this.maintenanceAssets = maintenanceAssets;
    }

    public long getRetiredAssets() {
        return retiredAssets;
    }

    public void setRetiredAssets(long retiredAssets) {
        this.retiredAssets = retiredAssets;
    }

    public BigDecimal getTotalAssetValue() {
        return totalAssetValue;
    }

    public void setTotalAssetValue(BigDecimal totalAssetValue) {
        this.totalAssetValue = totalAssetValue;
    }

    public long getExpiringWarrantiesCount() {
        return expiringWarrantiesCount;
    }

    public void setExpiringWarrantiesCount(long expiringWarrantiesCount) {
        this.expiringWarrantiesCount = expiringWarrantiesCount;
    }

    public Map<String, Long> getAssetsByCategory() {
        return assetsByCategory;
    }

    public void setAssetsByCategory(Map<String, Long> assetsByCategory) {
        this.assetsByCategory = assetsByCategory;
    }

    public Map<String, Long> getAssetsByDepartment() {
        return assetsByDepartment;
    }

    public void setAssetsByDepartment(Map<String, Long> assetsByDepartment) {
        this.assetsByDepartment = assetsByDepartment;
    }

    public long getTotalAccessories() {
        return totalAccessories;
    }

    public void setTotalAccessories(long totalAccessories) {
        this.totalAccessories = totalAccessories;
    }

    public long getTotalConsumables() {
        return totalConsumables;
    }

    public void setTotalConsumables(long totalConsumables) {
        this.totalConsumables = totalConsumables;
    }

    public long getTotalComponents() {
        return totalComponents;
    }

    public void setTotalComponents(long totalComponents) {
        this.totalComponents = totalComponents;
    }

    public long getLowStockAccessoriesCount() {
        return lowStockAccessoriesCount;
    }

    public void setLowStockAccessoriesCount(long lowStockAccessoriesCount) {
        this.lowStockAccessoriesCount = lowStockAccessoriesCount;
    }

    public long getLowStockConsumablesCount() {
        return lowStockConsumablesCount;
    }

    public void setLowStockConsumablesCount(long lowStockConsumablesCount) {
        this.lowStockConsumablesCount = lowStockConsumablesCount;
    }

    public List<ActivityLog> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<ActivityLog> recentActivity) {
        this.recentActivity = recentActivity != null ? recentActivity : new ArrayList<>();
    }
}
