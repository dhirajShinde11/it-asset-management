package com.company.itam.service;

import com.company.itam.dto.DashboardSummaryDto;
import com.company.itam.model.Accessory;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetCategory;
import com.company.itam.model.AssetStatus;
import com.company.itam.model.Consumable;
import com.company.itam.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetRepository assetRepository;
    private final AccessoryRepository accessoryRepository;
    private final ConsumableRepository consumableRepository;
    private final ComponentRepository componentRepository;
    private final ActivityLogRepository activityLogRepository;

    public DashboardService(AssetRepository assetRepository,
                            AccessoryRepository accessoryRepository,
                            ConsumableRepository consumableRepository,
                            ComponentRepository componentRepository,
                            ActivityLogRepository activityLogRepository) {
        this.assetRepository = assetRepository;
        this.accessoryRepository = accessoryRepository;
        this.consumableRepository = consumableRepository;
        this.componentRepository = componentRepository;
        this.activityLogRepository = activityLogRepository;
    }

    public DashboardSummaryDto getSummary() {
        DashboardSummaryDto summary = new DashboardSummaryDto();

        summary.setTotalAssets(assetRepository.count());
        summary.setAvailableAssets(assetRepository.countByStatus(AssetStatus.AVAILABLE));
        summary.setAssignedAssets(assetRepository.countByStatus(AssetStatus.ASSIGNED));
        summary.setMaintenanceAssets(assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE));
        summary.setRetiredAssets(assetRepository.countByStatus(AssetStatus.RETIRED));

        BigDecimal totalVal = assetRepository.sumTotalAssetValue();
        summary.setTotalAssetValue(totalVal != null ? totalVal : BigDecimal.ZERO);

        LocalDate today = LocalDate.now();
        LocalDate in60Days = today.plusDays(60);
        summary.setExpiringWarrantiesCount(assetRepository.countByWarrantyExpiryDateBetween(today, in60Days));

        // Assets by Category - database group-by
        Map<String, Long> byCategory = new HashMap<>();
        List<Object[]> catRows = assetRepository.countGroupByCategory();
        for (Object[] row : catRows) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                byCategory.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        summary.setAssetsByCategory(byCategory);

        // Assets by Department - database group-by
        Map<String, Long> byDept = new HashMap<>();
        List<Object[]> deptRows = assetRepository.countGroupByDepartment();
        for (Object[] row : deptRows) {
            if (row != null && row.length >= 2 && row[0] != null && row[1] != null) {
                byDept.put(row[0].toString(), ((Number) row[1]).longValue());
            }
        }
        summary.setAssetsByDepartment(byDept);

        // Extended Inventory Metrics - direct database counts
        summary.setTotalAccessories(accessoryRepository.count());
        summary.setLowStockAccessoriesCount(accessoryRepository.countLowStock());

        summary.setTotalConsumables(consumableRepository.count());
        summary.setLowStockConsumablesCount(consumableRepository.countLowStock());

        summary.setTotalComponents(componentRepository.count());
        try {
            summary.setRecentActivity(activityLogRepository.findTop50ByOrderByTimestampDesc());
        } catch (Exception ignored) {
            summary.setRecentActivity(java.util.Collections.emptyList());
        }

        return summary;
    }
}
