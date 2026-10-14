package com.company.itam.dto.bulk;

import java.util.ArrayList;
import java.util.List;

public class BulkAssetImportRequest {
    private List<BulkAssetRowDto> assets = new ArrayList<>();
    private boolean updateExisting = false;
    private boolean autoCreateUsers = true;
    private boolean sendWelcomeEmail = true;

    public BulkAssetImportRequest() {}

    public List<BulkAssetRowDto> getAssets() { return assets; }
    public void setAssets(List<BulkAssetRowDto> assets) { this.assets = assets; }

    public boolean isUpdateExisting() { return updateExisting; }
    public void setUpdateExisting(boolean updateExisting) { this.updateExisting = updateExisting; }

    public boolean isAutoCreateUsers() { return autoCreateUsers; }
    public void setAutoCreateUsers(boolean autoCreateUsers) { this.autoCreateUsers = autoCreateUsers; }

    public boolean isSendWelcomeEmail() { return sendWelcomeEmail; }
    public void setSendWelcomeEmail(boolean sendWelcomeEmail) { this.sendWelcomeEmail = sendWelcomeEmail; }
}
