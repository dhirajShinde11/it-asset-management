package com.company.itam.dto.bulk;

import java.util.ArrayList;
import java.util.List;

public class BulkAccessoryImportRequest {
    private List<BulkAccessoryRowDto> accessories = new ArrayList<>();
    private boolean updateExisting = false;

    public BulkAccessoryImportRequest() {}

    public List<BulkAccessoryRowDto> getAccessories() { return accessories; }
    public void setAccessories(List<BulkAccessoryRowDto> accessories) { this.accessories = accessories; }

    public boolean isUpdateExisting() { return updateExisting; }
    public void setUpdateExisting(boolean updateExisting) { this.updateExisting = updateExisting; }
}
