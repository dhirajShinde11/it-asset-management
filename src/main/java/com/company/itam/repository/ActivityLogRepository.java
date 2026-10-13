package com.company.itam.repository;

import com.company.itam.model.ActivityAction;
import com.company.itam.model.ActivityLog;
import com.company.itam.model.ItemType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findAllByOrderByTimestampDesc();
    List<ActivityLog> findTop50ByOrderByTimestampDesc();
    List<ActivityLog> findByItemTypeOrderByTimestampDesc(ItemType itemType);
    List<ActivityLog> findByActionOrderByTimestampDesc(ActivityAction action);
    List<ActivityLog> findByTargetUserEmailIgnoreCaseOrderByTimestampDesc(String email);
    List<ActivityLog> findByItemIdAndItemTypeOrderByTimestampDesc(Long itemId, ItemType itemType);
}
