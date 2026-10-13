package com.company.itam.dto;

import com.company.itam.model.UserRole;
import com.company.itam.model.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

public class UserResponseDto {

    private Long id;
    private String fullName;
    private String email;
    private String department;
    private String jobTitle;
    private String phoneNumber;
    private UserRole role;
    private UserStatus status;
    private String temporaryPassword;
    private long assignedAssetsCount;
    private List<String> assignedAssetTags;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public long getAssignedAssetsCount() {
        return assignedAssetsCount;
    }

    public void setAssignedAssetsCount(long assignedAssetsCount) {
        this.assignedAssetsCount = assignedAssetsCount;
    }

    public List<String> getAssignedAssetTags() {
        return assignedAssetTags;
    }

    public void setAssignedAssetTags(List<String> assignedAssetTags) {
        this.assignedAssetTags = assignedAssetTags;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
