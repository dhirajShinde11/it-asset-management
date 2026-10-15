package com.company.itam.dto;

import com.company.itam.model.TicketCategory;
import com.company.itam.model.TicketPriority;
import com.company.itam.model.TicketStatus;

public class TicketUpdateDto {

    private TicketStatus status;

    private TicketPriority priority;

    private TicketCategory category;

    private String assignedTo;

    private String resolutionNotes;

    public TicketUpdateDto() {}

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public TicketCategory getCategory() {
        return category;
    }

    public void setCategory(TicketCategory category) {
        this.category = category;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }
}
