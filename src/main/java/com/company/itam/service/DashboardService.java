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
        List<Asset> allAssets = assetRepository.findAll();
        DashboardSummaryDto summary = new DashboardSummaryDto();

        summary.setTotalAssets(allAssets.size());
        summary.setAvailableAssets(allAssets.stream().filter(a -> a.getStatus() == AssetStatus.AVAILABLE).count());
        summary.setAssignedAssets(allAssets.stream().filter(a -> a.getStatus() == AssetStatus.ASSIGNED).count());
        summary.setMaintenanceAssets(allAssets.stream().filter(a -> a.getStatus() == AssetStatus.UNDER_MAINTENANCE).count());
        summary.setRetiredAssets(allAssets.stream().filter(a -> a.getStatus() == AssetStatus.RETIRED).count());

        BigDecimal totalVal = allAssets.stream()
                .map(Asset::getPurchaseCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summary.setTotalAssetValue(totalVal);

        LocalDate today = LocalDate.now();
        LocalDate in60Days = today.plusDays(60);
        long expiringCount = allAssets.stream()
                .filter(a -> a.getWarrantyExpiryDate() != null)
                .filter(a -> !a.getWarrantyExpiryDate().isBefore(today) && !a.getWarrantyExpiryDate().isAfter(in60Days))
                .count();
        summary.setExpiringWarrantiesCount(expiringCount);

        // Assets by Category
        Map<String, Long> byCategory = new HashMap<>();
        for (AssetCategory cat : AssetCategory.values()) {
            long count = allAssets.stream().filter(a -> a.getCategory() == cat).count();
            if (count > 0) {
                byCategory.put(cat.name(), count);
            }
        }
        summary.setAssetsByCategory(byCategory);

        // Assets by Department
        Map<String, Long> byDept = allAssets.stream()
                .filter(a -> a.getAssignedDepartment() != null && !a.getAssignedDepartment().trim().isEmpty())
                .collect(Collectors.groupingBy(Asset::getAssignedDepartment, Collectors.counting()));
        summary.setAssetsByDepartment(byDept);

        // Extended Inventory Metrics
        List<Accessory> accessories = accessoryRepository.findAll();
        summary.setTotalAccessories(accessories.size());
        summary.setLowStockAccessoriesCount(accessories.stream()
                .filter(a -> a.getRemainingQuantity() <= (a.getMinQuantity() != null ? a.getMinQuantity() : 5))
                .count());

        List<Consumable> consumables = consumableRepository.findAll();
        summary.setTotalConsumables(consumables.size());
        summary.setLowStockConsumablesCount(consumables.stream()
                .filter(c -> c.getRemainingQuantity() <= (c.getMinQuantity() != null ? c.getMinQuantity() : 5))
                .count());

        summary.setTotalComponents(componentRepository.count());
        summary.setRecentActivity(activityLogRepository.findTop50ByOrderByTimestampDesc());

        return summary;
    }
}
