package com.company.itam.service;

import com.company.itam.dto.AssetAssignmentDto;
import com.company.itam.dto.AssetRequestDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.AppUser;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetCategory;
import com.company.itam.model.AssetStatus;
import com.company.itam.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AssetService {

    private final AssetRepository assetRepository;
    private final com.company.itam.repository.UserRepository userRepository;
    private final EmailService emailService;
    private final ActivityLogService activityLogService;

    public AssetService(AssetRepository assetRepository,
                        com.company.itam.repository.UserRepository userRepository,
                        EmailService emailService,
                        ActivityLogService activityLogService) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.activityLogService = activityLogService;
    }

    @Transactional(readOnly = true)
    public List<Asset> getAllAssets(String query, AssetCategory category, AssetStatus status) {
        return getAllAssets(query, category, status, null);
    }

    @Transactional(readOnly = true)
    public List<Asset> getAllAssets(String query, AssetCategory category, AssetStatus status, String department) {
        String cleanQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        String cleanDept = (department != null && !department.trim().isEmpty()) ? department.trim() : null;
        return assetRepository.filterAssets(cleanQuery, category, status, cleanDept);
    }

    @Transactional(readOnly = true)
    public Asset getAssetById(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public Asset getAssetByTag(String tag) {
        return assetRepository.findByAssetTag(tag)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found with tag: " + tag));
    }

    public Asset createAsset(AssetRequestDto dto) {
        if (assetRepository.existsByAssetTag(dto.getAssetTag())) {
            throw new IllegalArgumentException("Asset with tag '" + dto.getAssetTag() + "' already exists");
        }

        Asset asset = new Asset();
        mapDtoToEntity(dto, asset);

        if (asset.getAssignedEmployeeName() != null && !asset.getAssignedEmployeeName().trim().isEmpty()) {
            asset.setStatus(AssetStatus.ASSIGNED);
            if (asset.getAssignedDate() == null) {
                asset.setAssignedDate(LocalDate.now());
            }
        }

        Asset saved = assetRepository.save(asset);
        activityLogService.log(com.company.itam.model.ActivityAction.CREATED, com.company.itam.model.ItemType.ASSET,
                saved.getId(), saved.getAssetTag(), saved.getName(), saved.getAssignedEmployeeName(),
                saved.getAssignedEmployeeEmail(), "IT Admin", "Registered new hardware asset");
        return saved;
    }

    public Asset updateAsset(Long id, AssetRequestDto dto) {
        Asset asset = getAssetById(id);

        if (!asset.getAssetTag().equalsIgnoreCase(dto.getAssetTag()) &&
                assetRepository.existsByAssetTag(dto.getAssetTag())) {
            throw new IllegalArgumentException("Asset with tag '" + dto.getAssetTag() + "' already exists");
        }

        mapDtoToEntity(dto, asset);
        Asset updated = assetRepository.save(asset);
        activityLogService.log(com.company.itam.model.ActivityAction.UPDATED, com.company.itam.model.ItemType.ASSET,
                updated.getId(), updated.getAssetTag(), updated.getName(), updated.getAssignedEmployeeName(),
                updated.getAssignedEmployeeEmail(), "IT Admin", "Updated asset specifications/details");
        return updated;
    }

    public Asset assignAsset(Long id, AssetAssignmentDto dto) {
        Asset asset = getAssetById(id);
        asset.setAssignedEmployeeName(dto.getEmployeeName());
        asset.setAssignedEmployeeEmail(dto.getEmployeeEmail() != null ? dto.getEmployeeEmail().trim().toLowerCase() : null);
        asset.setAssignedDepartment(dto.getDepartment());
        asset.setAssignedDate(dto.getAssignedDate() != null ? dto.getAssignedDate() : LocalDate.now());
        asset.setExpectedReturnDate(dto.getExpectedReturnDate());
        asset.setCheckoutNotes(dto.getCheckoutNotes());
        asset.setStatus(AssetStatus.ASSIGNED);
        Asset saved = assetRepository.save(asset);

        activityLogService.log(com.company.itam.model.ActivityAction.CHECKOUT, com.company.itam.model.ItemType.ASSET,
                saved.getId(), saved.getAssetTag(), saved.getName(), dto.getEmployeeName(),
                dto.getEmployeeEmail(), "IT Admin",
                "Checked out to " + dto.getEmployeeName() +
                        (dto.getExpectedReturnDate() != null ? " (Due: " + dto.getExpectedReturnDate() + ")" : "") +
                        (dto.getCheckoutNotes() != null ? " - " + dto.getCheckoutNotes() : ""));

        // Notify user via email (registered user or direct email recipient)
        if (dto.getEmployeeEmail() != null && !dto.getEmployeeEmail().isBlank()) {
            AppUser recipient = userRepository.findByEmailIgnoreCase(dto.getEmployeeEmail().trim())
                    .orElseGet(() -> {
                        AppUser temp = new AppUser();
                        temp.setEmail(dto.getEmployeeEmail().trim());
                        temp.setFullName(dto.getEmployeeName() != null ? dto.getEmployeeName() : dto.getEmployeeEmail());
                        temp.setDepartment(dto.getDepartment());
                        return temp;
                    });
            emailService.sendAssetAssignedEmail(recipient, saved);
        }

        return saved;
    }

    public Asset checkinAsset(Long id, com.company.itam.dto.AssetCheckinDto dto) {
        Asset asset = getAssetById(id);
        String previousUser = asset.getAssignedEmployeeName();
        String previousEmail = asset.getAssignedEmployeeEmail();

        asset.setStatus(dto.getStatus() != null ? dto.getStatus() : AssetStatus.AVAILABLE);
        if (dto.getCondition() != null) {
            asset.setCondition(dto.getCondition());
        }
        asset.setCheckinNotes(dto.getCheckinNotes());
        asset.setAssignedEmployeeName(null);
        asset.setAssignedEmployeeEmail(null);
        asset.setAssignedDepartment(null);
        asset.setAssignedDate(null);
        asset.setExpectedReturnDate(null);

        Asset saved = assetRepository.save(asset);
        activityLogService.log(com.company.itam.model.ActivityAction.CHECKIN, com.company.itam.model.ItemType.ASSET,
                saved.getId(), saved.getAssetTag(), saved.getName(), previousUser, previousEmail, "IT Admin",
                "Checked in to inventory [Status: " + saved.getStatus() + ", Condition: " + saved.getCondition() + "]" +
                        (dto.getCheckinNotes() != null ? " - " + dto.getCheckinNotes() : ""));
        return saved;
    }

    public Asset unassignAsset(Long id) {
        return checkinAsset(id, new com.company.itam.dto.AssetCheckinDto());
    }

    public Asset auditAsset(Long id, String notes) {
        Asset asset = getAssetById(id);
        asset.setLastAuditDate(LocalDate.now());
        asset.setNextAuditDate(LocalDate.now().plusMonths(6));
        Asset saved = assetRepository.save(asset);

        activityLogService.log(com.company.itam.model.ActivityAction.AUDIT, com.company.itam.model.ItemType.ASSET,
                saved.getId(), saved.getAssetTag(), saved.getName(), saved.getAssignedEmployeeName(),
                saved.getAssignedEmployeeEmail(), "IT Admin",
                "Physical audit verified" + (notes != null ? " - " + notes : ""));
        return saved;
    }

    public void deleteAsset(Long id) {
        Asset asset = getAssetById(id);
        assetRepository.delete(asset);
        activityLogService.log(com.company.itam.model.ActivityAction.DELETED, com.company.itam.model.ItemType.ASSET,
                id, asset.getAssetTag(), asset.getName(), null, null, "IT Admin", "Deleted asset permanently");
    }

    private void mapDtoToEntity(AssetRequestDto dto, Asset asset) {
        asset.setAssetTag(dto.getAssetTag());
        asset.setName(dto.getName());
        asset.setCategory(dto.getCategory());
        asset.setManufacturer(dto.getManufacturer());
        asset.setModel(dto.getModel());
        asset.setSerialNumber(dto.getSerialNumber());
        if (dto.getStatus() != null) {
            asset.setStatus(dto.getStatus());
        }
        if (dto.getCondition() != null) {
            asset.setCondition(dto.getCondition());
        }
        asset.setPurchaseCost(dto.getPurchaseCost());
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setWarrantyExpiryDate(dto.getWarrantyExpiryDate());
        asset.setLocation(dto.getLocation());
        asset.setAssignedEmployeeName(dto.getAssignedEmployeeName());
        asset.setAssignedEmployeeEmail(dto.getAssignedEmployeeEmail());
        asset.setAssignedDepartment(dto.getAssignedDepartment());
        asset.setAssignedDate(dto.getAssignedDate());
        asset.setSpecifications(dto.getSpecifications());
        asset.setNotes(dto.getNotes());
    }

    @Transactional
    public Asset updateAssetStatus(Long id, AssetStatus newStatus, String notes) {
        Asset asset = getAssetById(id);
        AssetStatus oldStatus = asset.getStatus();
        asset.setStatus(newStatus);
        if (newStatus == AssetStatus.AVAILABLE || newStatus == AssetStatus.RETIRED || newStatus == AssetStatus.LOST_OR_STOLEN) {
            asset.setAssignedEmployeeName(null);
            asset.setAssignedEmployeeEmail(null);
            asset.setAssignedDepartment(null);
            asset.setAssignedDate(null);
        }
        Asset saved = assetRepository.save(asset);
        activityLogService.log(
            com.company.itam.model.ActivityAction.UPDATED,
            com.company.itam.model.ItemType.ASSET,
            asset.getId(),
            asset.getAssetTag(),
            asset.getName(),
            asset.getAssignedEmployeeName(),
            asset.getAssignedEmployeeEmail(),
            "IT Admin",
            "Status changed from " + oldStatus + " to " + newStatus + (notes != null ? " (" + notes + ")" : "")
        );
        return saved;
    }
}
