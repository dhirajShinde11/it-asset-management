package com.company.itam.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public class AccessoryCheckoutDto {

    private Long userId;

    @NotBlank(message = "User name is required")
    private String userName;

    @NotBlank(message = "User email is required")
    private String userEmail;

    private String userDepartment;

    private Integer quantity = 1;

    private LocalDate assignedDate = LocalDate.now();

    private String notes;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public void setEmployeeName(String employeeName) { if (this.userName == null || this.userName.isBlank()) this.userName = employeeName; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setEmployeeEmail(String employeeEmail) { if (this.userEmail == null || this.userEmail.isBlank()) this.userEmail = employeeEmail; }

    public String getUserDepartment() { return userDepartment; }
    public void setUserDepartment(String userDepartment) { this.userDepartment = userDepartment; }
    public void setDepartment(String department) { if (this.userDepartment == null) this.userDepartment = department; }

    public Integer getQuantity() { return quantity != null && quantity > 0 ? quantity : 1; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDate getAssignedDate() { return assignedDate != null ? assignedDate : LocalDate.now(); }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
