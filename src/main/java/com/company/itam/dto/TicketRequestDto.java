package com.company.itam.dto;

import com.company.itam.model.TicketCategory;
import com.company.itam.model.TicketPriority;
import jakarta.validation.constraints.NotBlank;

public class TicketRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private TicketCategory category = TicketCategory.HARDWARE;

    private TicketPriority priority = TicketPriority.MEDIUM;

    private Long assetId;

    @NotBlank(message = "Requester name is required")
    private String requesterName;

    @NotBlank(message = "Requester email is required")
    private String requesterEmail;

    private String requesterDepartment;

    private String assignedTo;

    public TicketRequestDto() {}

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public void setCategory(TicketCategory category) {
        this.category = category;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public String getRequesterEmail() {
        return requesterEmail;
    }

    public void setRequesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail;
    }

    public String getRequesterDepartment() {
        return requesterDepartment;
    }

    public void setRequesterDepartment(String requesterDepartment) {
        this.requesterDepartment = requesterDepartment;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }
}
