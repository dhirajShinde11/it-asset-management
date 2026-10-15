package com.company.itam.dto;

import com.company.itam.model.Asset;
import com.company.itam.model.MaintenanceRecord;
import com.company.itam.model.SoftwareLicense;
import java.util.ArrayList;
import java.util.List;

public class EmployeeOverviewDto {

    private UserResponseDto profile;
    private List<Asset> assignedAssets = new ArrayList<>();
    private List<SoftwareLicense> departmentLicenses = new ArrayList<>();
    private List<MaintenanceRecord> serviceHistory = new ArrayList<>();
    private List<com.company.itam.model.Ticket> tickets = new ArrayList<>();

    public List<com.company.itam.model.Ticket> getTickets() {
        return tickets;
    }

    public void setTickets(List<com.company.itam.model.Ticket> tickets) {
        this.tickets = tickets != null ? tickets : new ArrayList<>();
    }

    public UserResponseDto getProfile() {
        return profile;
    }

    public void setProfile(UserResponseDto profile) {
        this.profile = profile;
    }

    public List<Asset> getAssignedAssets() {
        return assignedAssets;
    }

    public void setAssignedAssets(List<Asset> assignedAssets) {
        this.assignedAssets = assignedAssets;
    }

    public List<SoftwareLicense> getDepartmentLicenses() {
        return departmentLicenses;
    }

    public void setDepartmentLicenses(List<SoftwareLicense> departmentLicenses) {
        this.departmentLicenses = departmentLicenses;
    }

    public List<MaintenanceRecord> getServiceHistory() {
        return serviceHistory;
    }

    public void setServiceHistory(List<MaintenanceRecord> serviceHistory) {
        this.serviceHistory = serviceHistory;
    }
}
