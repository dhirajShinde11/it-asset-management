package com.company.itam.dto.bulk;

import java.util.ArrayList;
import java.util.List;

public class BulkUserImportRequest {
    private List<BulkUserRowDto> users = new ArrayList<>();
    private boolean updateExisting = false;
    private boolean sendWelcomeEmail = true;

    public BulkUserImportRequest() {}

    public List<BulkUserRowDto> getUsers() { return users; }
    public void setUsers(List<BulkUserRowDto> users) { this.users = users; }

    public boolean isUpdateExisting() { return updateExisting; }
    public void setUpdateExisting(boolean updateExisting) { this.updateExisting = updateExisting; }

    public boolean isSendWelcomeEmail() { return sendWelcomeEmail; }
    public void setSendWelcomeEmail(boolean sendWelcomeEmail) { this.sendWelcomeEmail = sendWelcomeEmail; }
}
