package com.company.itam.dto;

import com.company.itam.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class UserDetailResponseDto {

    private UserResponseDto user;
    private List<Asset> assignedAssets = new ArrayList<>();
    private List<AccessoryCheckout> assignedAccessories = new ArrayList<>();
    private List<ConsumableCheckout> receivedConsumables = new ArrayList<>();
    private BigDecimal totalAssetValue = BigDecimal.ZERO;
    private List<MaintenanceRecord> serviceHistory = new ArrayList<>();
    private List<EmailNotification> emailHistory = new ArrayList<>();
    private List<ActivityLog> userActivityHistory = new ArrayList<>();

    public UserResponseDto getUser() {
        return user;
    }

    public void setUser(UserResponseDto user) {
        this.user = user;
    }

    public List<Asset> getAssignedAssets() {
        return assignedAssets;
    }

    public void setAssignedAssets(List<Asset> assignedAssets) {
        this.assignedAssets = assignedAssets != null ? assignedAssets : new ArrayList<>();
    }

    public List<AccessoryCheckout> getAssignedAccessories() {
        return assignedAccessories;
    }

    public void setAssignedAccessories(List<AccessoryCheckout> assignedAccessories) {
        this.assignedAccessories = assignedAccessories != null ? assignedAccessories : new ArrayList<>();
    }

    public List<ConsumableCheckout> getReceivedConsumables() {
        return receivedConsumables;
    }

    public void setReceivedConsumables(List<ConsumableCheckout> receivedConsumables) {
        this.receivedConsumables = receivedConsumables != null ? receivedConsumables : new ArrayList<>();
    }

    public BigDecimal getTotalAssetValue() {
        return totalAssetValue;
    }

    public void setTotalAssetValue(BigDecimal totalAssetValue) {
        this.totalAssetValue = totalAssetValue != null ? totalAssetValue : BigDecimal.ZERO;
    }

    public List<MaintenanceRecord> getServiceHistory() {
        return serviceHistory;
    }

    public void setServiceHistory(List<MaintenanceRecord> serviceHistory) {
        this.serviceHistory = serviceHistory != null ? serviceHistory : new ArrayList<>();
    }

    public List<EmailNotification> getEmailHistory() {
        return emailHistory;
    }

    public void setEmailHistory(List<EmailNotification> emailHistory) {
        this.emailHistory = emailHistory != null ? emailHistory : new ArrayList<>();
    }

    public List<ActivityLog> getUserActivityHistory() {
        return userActivityHistory;
    }

    public void setUserActivityHistory(List<ActivityLog> userActivityHistory) {
        this.userActivityHistory = userActivityHistory != null ? userActivityHistory : new ArrayList<>();
    }

    // Alias accessors for Snipe-IT compatibility
    public List<AccessoryCheckout> getAccessoryCheckouts() {
        return assignedAccessories;
    }

    public List<ConsumableCheckout> getConsumableCheckouts() {
        return receivedConsumables;
    }

    public List<ActivityLog> getActivityLogs() {
        return userActivityHistory;
    }
}
