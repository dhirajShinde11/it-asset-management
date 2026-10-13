package com.company.itam.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public class AssetAssignmentDto {

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    private String employeeEmail;

    private String department;

    private LocalDate assignedDate = LocalDate.now();

    private LocalDate expectedReturnDate;

    private String checkoutNotes;

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeEmail() {
        return employeeEmail;
    }

    public void setEmployeeEmail(String employeeEmail) {
        this.employeeEmail = employeeEmail;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public LocalDate getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(LocalDate expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public String getCheckoutNotes() {
        return checkoutNotes;
    }

    public void setCheckoutNotes(String checkoutNotes) {
        this.checkoutNotes = checkoutNotes;
    }
}
