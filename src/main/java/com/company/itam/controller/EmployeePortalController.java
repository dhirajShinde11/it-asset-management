package com.company.itam.controller;

import com.company.itam.dto.EmployeeOverviewDto;
import com.company.itam.dto.EmployeeServiceRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetStatus;
import com.company.itam.model.MaintenanceRecord;
import com.company.itam.model.SoftwareLicense;
import com.company.itam.repository.AssetRepository;
import com.company.itam.repository.MaintenanceRecordRepository;
import com.company.itam.repository.SoftwareLicenseRepository;
import com.company.itam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/my")
@Tag(name = "Employee Self-Service Portal", description = "Endpoints for logged-in employees to view assigned equipment, licenses, and request repairs")
@CrossOrigin(origins = "*")
public class EmployeePortalController {

    private final UserService userService;
    private final AssetRepository assetRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final MaintenanceRecordRepository maintenanceRepository;

    public EmployeePortalController(UserService userService,
                                    AssetRepository assetRepository,
                                    SoftwareLicenseRepository licenseRepository,
                                    MaintenanceRecordRepository maintenanceRepository) {
        this.userService = userService;
        this.assetRepository = assetRepository;
        this.licenseRepository = licenseRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get employee dashboard overview including assigned equipment and team software")
    public ResponseEntity<EmployeeOverviewDto> getOverview(@RequestParam String email) {
        UserResponseDto user = userService.getUserByEmail(email);

        List<Asset> assignedAssets = assetRepository.findByAssignedEmployeeEmailOrName(email, user != null ? user.getFullName() : null);

        // Fetch maintenance tickets associated with their assigned assets
        List<MaintenanceRecord> serviceHistory = new ArrayList<>();
        for (Asset asset : assignedAssets) {
            serviceHistory.addAll(maintenanceRepository.findByAssetIdOrderByServiceDateDesc(asset.getId()));
        }

        EmployeeOverviewDto overview = new EmployeeOverviewDto();
        overview.setProfile(user);
        overview.setAssignedAssets(assignedAssets);
        overview.setDepartmentLicenses(new ArrayList<>());
        overview.setServiceHistory(serviceHistory);

        return ResponseEntity.ok(overview);
    }

    @GetMapping("/assets")
    @Operation(summary = "List all hardware assets currently assigned to employee")
    public ResponseEntity<List<Asset>> getMyAssets(@RequestParam String email) {
        UserResponseDto user = null;
        try {
            user = userService.getUserByEmail(email);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(assetRepository.findByAssignedEmployeeEmailOrName(email, user != null ? user.getFullName() : null));
    }

    @PostMapping("/service-request")
    @Operation(summary = "Submit an equipment service or repair request")
    public ResponseEntity<MaintenanceRecord> submitServiceRequest(
            @RequestParam String email,
            @Valid @RequestBody EmployeeServiceRequestDto request) {

        Asset asset = assetRepository.findById(request.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + request.getAssetId()));

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(asset);
        record.setTitle(request.getTitle());
        record.setDescription((request.getIssueDescription() != null ? request.getIssueDescription() : "")
                + " [Submitted by: " + email + (request.getContactPhone() != null ? ", Phone: " + request.getContactPhone() : "")
                + ", Urgency: " + request.getUrgency() + "]");
        record.setServiceDate(LocalDate.now());
        record.setServiceProvider("Internal IT Helpdesk");
        record.setStatus("IN_PROGRESS");

        // Shift asset to under maintenance
        asset.setStatus(AssetStatus.UNDER_MAINTENANCE);
        assetRepository.save(asset);

        MaintenanceRecord saved = maintenanceRepository.save(record);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }
}
