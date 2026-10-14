package com.company.itam.controller;

import com.company.itam.dto.AssetAssignmentDto;
import com.company.itam.dto.AssetRequestDto;
import com.company.itam.dto.MaintenanceRequestDto;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetCategory;
import com.company.itam.model.AssetStatus;
import com.company.itam.model.MaintenanceRecord;
import com.company.itam.repository.MaintenanceRecordRepository;
import com.company.itam.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
@CrossOrigin(origins = "*")
@Tag(name = "Asset Management", description = "Endpoints for managing IT hardware and software assets")
public class AssetController {

    private final AssetService assetService;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final com.company.itam.service.ActivityLogService activityLogService;

    public AssetController(AssetService assetService,
                           MaintenanceRecordRepository maintenanceRepository,
                           com.company.itam.service.ActivityLogService activityLogService) {
        this.assetService = assetService;
        this.maintenanceRepository = maintenanceRepository;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "Get all assets with optional filtering and keyword search")
    public ResponseEntity<List<Asset>> getAllAssets(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AssetCategory category,
            @RequestParam(required = false) AssetStatus status,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(assetService.getAllAssets(query, category, status, department));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<Asset> getAssetById(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getAssetById(id));
    }

    @GetMapping("/tag/{tag}")
    @Operation(summary = "Get asset by unique tag")
    public ResponseEntity<Asset> getAssetByTag(@PathVariable String tag) {
        return ResponseEntity.ok(assetService.getAssetByTag(tag));
    }

    @PostMapping
    @Operation(summary = "Create a new IT asset")
    public ResponseEntity<Asset> createAsset(@Valid @RequestBody AssetRequestDto dto) {
        Asset created = assetService.createAsset(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing IT asset")
    public ResponseEntity<Asset> updateAsset(@PathVariable Long id, @Valid @RequestBody AssetRequestDto dto) {
        return ResponseEntity.ok(assetService.updateAsset(id, dto));
    }

    @RequestMapping(value = {"/{id}/assign", "/{id}/checkout"}, method = {RequestMethod.PATCH, RequestMethod.POST})
    @Operation(summary = "Check out / assign an asset to an employee")
    public ResponseEntity<Asset> assignAsset(@PathVariable Long id, @Valid @RequestBody AssetAssignmentDto dto) {
        return ResponseEntity.ok(assetService.assignAsset(id, dto));
    }

    @PostMapping("/{id}/checkin")
    @Operation(summary = "Check in an asset with condition check and return notes")
    public ResponseEntity<Asset> checkinAsset(@PathVariable Long id, @RequestBody(required = false) com.company.itam.dto.AssetCheckinDto dto) {
        return ResponseEntity.ok(assetService.checkinAsset(id, dto != null ? dto : new com.company.itam.dto.AssetCheckinDto()));
    }

    @PatchMapping("/{id}/unassign")
    @Operation(summary = "Unassign an asset and return it to available inventory")
    public ResponseEntity<Asset> unassignAsset(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.unassignAsset(id));
    }

    @PostMapping("/{id}/audit")
    @Operation(summary = "Perform physical audit verification on an asset")
    public ResponseEntity<Asset> auditAsset(@PathVariable Long id, @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(assetService.auditAsset(id, notes));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an asset")
    public ResponseEntity<Void> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Bulk delete multiple assets by IDs")
    public ResponseEntity<java.util.Map<String, Object>> bulkDeleteAssets(@RequestBody List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try {
                assetService.deleteAsset(id);
                count++;
            } catch (Exception ignored) {}
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("deletedCount", count);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/bulk-checkin")
    @Operation(summary = "Bulk check-in multiple assets back to available inventory")
    public ResponseEntity<java.util.Map<String, Object>> bulkCheckinAssets(@RequestBody List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            try {
                com.company.itam.dto.AssetCheckinDto dto = new com.company.itam.dto.AssetCheckinDto();
                dto.setCheckinNotes("Bulk checkin by Admin");
                assetService.checkinAsset(id, dto);
                count++;
            } catch (Exception ignored) {}
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("checkedInCount", count);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/bulk-status")
    @Operation(summary = "Bulk update status for multiple assets")
    public ResponseEntity<java.util.Map<String, Object>> bulkUpdateStatus(@RequestBody java.util.Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) req.get("ids");
        String statusStr = (String) req.get("status");
        int count = 0;
        if (rawIds != null && statusStr != null) {
            com.company.itam.model.AssetStatus newStatus = com.company.itam.model.AssetStatus.valueOf(statusStr);
            for (Object item : rawIds) {
                try {
                    Long id = Long.valueOf(item.toString());
                    assetService.updateAssetStatus(id, newStatus, "Bulk status change");
                    count++;
                } catch (Exception ignored) {}
            }
        }
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("updatedCount", count);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/{id}/maintenance")
    @Operation(summary = "Get maintenance history for an asset")
    public ResponseEntity<List<MaintenanceRecord>> getAssetMaintenance(@PathVariable Long id) {
        assetService.getAssetById(id); // Validate asset exists
        return ResponseEntity.ok(maintenanceRepository.findByAssetIdOrderByServiceDateDesc(id));
    }

    @PostMapping("/{id}/maintenance")
    @Operation(summary = "Add a maintenance record to an asset")
    public ResponseEntity<MaintenanceRecord> addMaintenance(
            @PathVariable Long id,
            @Valid @RequestBody MaintenanceRequestDto dto) {
        Asset asset = assetService.getAssetById(id);
        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(asset);
        record.setTitle(dto.getTitle());
        record.setDescription(dto.getDescription());
        record.setServiceDate(dto.getServiceDate());
        record.setCost(dto.getCost());
        record.setServiceProvider(dto.getServiceProvider());
        if (dto.getStatus() != null) {
            record.setStatus(dto.getStatus());
        }

        // If under active maintenance, update asset status
        if ("IN_PROGRESS".equalsIgnoreCase(dto.getStatus()) || "SCHEDULED".equalsIgnoreCase(dto.getStatus())) {
            asset.setStatus(AssetStatus.UNDER_MAINTENANCE);
        }

        MaintenanceRecord saved = maintenanceRepository.save(record);
        activityLogService.log(com.company.itam.model.ActivityAction.MAINTENANCE, com.company.itam.model.ItemType.ASSET,
                asset.getId(), asset.getAssetTag(), asset.getName(), asset.getAssignedEmployeeName(),
                asset.getAssignedEmployeeEmail(), "IT Admin",
                "Logged maintenance record: " + dto.getTitle() + (dto.getCost() != null ? " ($" + dto.getCost() + ")" : ""));
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }
}
