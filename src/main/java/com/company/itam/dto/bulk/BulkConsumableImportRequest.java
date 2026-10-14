package com.company.itam.dto.bulk;

import java.util.ArrayList;
import java.util.List;

public class BulkConsumableImportRequest {
    private List<BulkConsumableRowDto> consumables = new ArrayList<>();
    private boolean updateExisting = false;

    public BulkConsumableImportRequest() {}

    public List<BulkConsumableRowDto> getConsumables() { return consumables; }
    public void setConsumables(List<BulkConsumableRowDto> consumables) { this.consumables = consumables; }

    public boolean isUpdateExisting() { return updateExisting; }
    public void setUpdateExisting(boolean updateExisting) { this.updateExisting = updateExisting; }
}
