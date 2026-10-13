package com.company.itam.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class ComponentAssignDto {

    @NotNull(message = "Asset ID is required")
    private Long assetId;

    private Integer quantity = 1;

    private LocalDate assignedDate = LocalDate.now();

    private String notes;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }

    public Integer getQuantity() { return quantity != null && quantity > 0 ? quantity : 1; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDate getAssignedDate() { return assignedDate != null ? assignedDate : LocalDate.now(); }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
