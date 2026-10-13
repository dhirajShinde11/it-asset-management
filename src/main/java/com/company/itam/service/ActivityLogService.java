package com.company.itam.service;

import com.company.itam.model.ActivityAction;
import com.company.itam.model.ActivityLog;
import com.company.itam.model.ItemType;
import com.company.itam.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);
    private final ActivityLogRepository activityLogRepository;

    public ActivityLogService(ActivityLogRepository activityLogRepository) {
        this.activityLogRepository = activityLogRepository;
    }

    @Transactional
    public ActivityLog log(ActivityAction action, ItemType itemType, Long itemId, String itemTag, String itemName,
                           String targetUserName, String targetUserEmail, String performedBy, String notes) {
        ActivityLog entry = new ActivityLog(action, itemType, itemId, itemTag, itemName, targetUserName, targetUserEmail, performedBy, notes);
        ActivityLog saved = activityLogRepository.save(entry);
        log.info("AUDIT LOG: [{}] {} '{}' ({}) -> Target: {} ({}) | By: {}",
                action, itemType, itemName, itemTag != null ? itemTag : "N/A",
                targetUserName != null ? targetUserName : "N/A",
                targetUserEmail != null ? targetUserEmail : "N/A",
                performedBy != null ? performedBy : "Admin");
        return saved;
    }

    public List<ActivityLog> getAllLogs() {
        return activityLogRepository.findAllByOrderByTimestampDesc();
    }

    public List<ActivityLog> getRecentLogs() {
        return activityLogRepository.findTop50ByOrderByTimestampDesc();
    }

    public List<ActivityLog> getLogsByItemType(ItemType type) {
        return activityLogRepository.findByItemTypeOrderByTimestampDesc(type);
    }

    public List<ActivityLog> getLogsByUser(String email) {
        return activityLogRepository.findByTargetUserEmailIgnoreCaseOrderByTimestampDesc(email);
    }
}
