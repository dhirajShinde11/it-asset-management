package com.company.itam.dto;

import com.company.itam.model.AssetCondition;
import com.company.itam.model.AssetStatus;

public class AssetCheckinDto {
    private AssetStatus status = AssetStatus.AVAILABLE;
    private AssetCondition condition = AssetCondition.GOOD;
    private String checkinNotes;

    public AssetStatus getStatus() { return status != null ? status : AssetStatus.AVAILABLE; }
    public void setStatus(AssetStatus status) { this.status = status; }

    public AssetCondition getCondition() { return condition != null ? condition : AssetCondition.GOOD; }
    public void setCondition(AssetCondition condition) { this.condition = condition; }

    public String getCheckinNotes() { return checkinNotes; }
    public void setCheckinNotes(String checkinNotes) { this.checkinNotes = checkinNotes; }
}
