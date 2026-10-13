package com.company.itam.service;

import com.company.itam.dto.ComponentAssignDto;
import com.company.itam.dto.ComponentRequestDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.*;
import com.company.itam.repository.AssetRepository;
import com.company.itam.repository.ComponentAssignmentRepository;
import com.company.itam.repository.ComponentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComponentService {

    private final ComponentRepository componentRepository;
    private final ComponentAssignmentRepository assignmentRepository;
    private final AssetRepository assetRepository;
    private final ActivityLogService activityLogService;

    public ComponentService(ComponentRepository componentRepository,
                            ComponentAssignmentRepository assignmentRepository,
                            AssetRepository assetRepository,
                            ActivityLogService activityLogService) {
        this.componentRepository = componentRepository;
        this.assignmentRepository = assignmentRepository;
        this.assetRepository = assetRepository;
        this.activityLogService = activityLogService;
    }

    public List<Component> getAllComponents() {
        return componentRepository.findAll();
    }

    public Component getComponentById(Long id) {
        return componentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Component not found with id: " + id));
    }

    @Transactional
    public Component createComponent(ComponentRequestDto dto) {
        Component c = new Component();
        c.setName(dto.getName().trim());
        c.setCategory(dto.getCategory());
        c.setManufacturer(dto.getManufacturer());
        c.setSerialNumber(dto.getSerialNumber());
        c.setTotalQuantity(dto.getTotalQuantity() != null ? dto.getTotalQuantity() : 1);
        c.setRemainingQuantity(c.getTotalQuantity());
        c.setMinQuantity(dto.getMinQuantity() != null ? dto.getMinQuantity() : 2);
        c.setPurchaseCost(dto.getPurchaseCost());
        c.setLocation(dto.getLocation());
        c.setNotes(dto.getNotes());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        Component saved = componentRepository.save(c);
        activityLogService.log(ActivityAction.CREATED, ItemType.COMPONENT, saved.getId(), null, saved.getName(),
                null, null, "IT Admin", "Added component part inventory: " + saved.getTotalQuantity() + " units");
        return saved;
    }

    @Transactional
    public Component updateComponent(Long id, ComponentRequestDto dto) {
        Component c = getComponentById(id);
        int diff = dto.getTotalQuantity() - c.getTotalQuantity();
        c.setName(dto.getName().trim());
        c.setCategory(dto.getCategory());
        c.setManufacturer(dto.getManufacturer());
        c.setSerialNumber(dto.getSerialNumber());
        c.setTotalQuantity(dto.getTotalQuantity());
        c.setRemainingQuantity(Math.max(0, c.getRemainingQuantity() + diff));
        if (dto.getMinQuantity() != null) c.setMinQuantity(dto.getMinQuantity());
        c.setPurchaseCost(dto.getPurchaseCost());
        c.setLocation(dto.getLocation());
        c.setNotes(dto.getNotes());
        c.setUpdatedAt(LocalDateTime.now());

        Component updated = componentRepository.save(c);
        activityLogService.log(ActivityAction.UPDATED, ItemType.COMPONENT, updated.getId(), null, updated.getName(),
                null, null, "IT Admin", "Updated component inventory");
        return updated;
    }

    @Transactional
    public void deleteComponent(Long id) {
        Component c = getComponentById(id);
        componentRepository.delete(c);
        activityLogService.log(ActivityAction.DELETED, ItemType.COMPONENT, id, null, c.getName(),
                null, null, "IT Admin", "Deleted component from catalog");
    }

    @Transactional
    public ComponentAssignment assignToAsset(Long componentId, ComponentAssignDto dto) {
        Component c = getComponentById(componentId);
        int qty = dto.getQuantity() != null && dto.getQuantity() > 0 ? dto.getQuantity() : 1;

        if (c.getRemainingQuantity() < qty) {
            throw new IllegalArgumentException("Cannot install " + qty + " units. Only " + c.getRemainingQuantity() + " available in stock.");
        }

        Asset asset = assetRepository.findById(dto.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException("Target asset not found with id: " + dto.getAssetId()));

        c.setRemainingQuantity(c.getRemainingQuantity() - qty);
        componentRepository.save(c);

        ComponentAssignment ca = new ComponentAssignment();
        ca.setComponent(c);
        ca.setAssetId(asset.getId());
        ca.setAssetTag(asset.getAssetTag());
        ca.setAssetName(asset.getName());
        ca.setQuantity(qty);
        ca.setAssignedDate(dto.getAssignedDate());
        ca.setNotes(dto.getNotes());
        ca.setCreatedAt(LocalDateTime.now());

        ComponentAssignment saved = assignmentRepository.save(ca);
        activityLogService.log(ActivityAction.CHECKOUT, ItemType.COMPONENT, c.getId(), asset.getAssetTag(), c.getName(),
                asset.getAssignedEmployeeName(), asset.getAssignedEmployeeEmail(), "IT Admin",
                "Installed " + qty + " unit(s) into asset [" + asset.getAssetTag() + " " + asset.getName() + "]" +
                        (dto.getNotes() != null ? " (" + dto.getNotes() + ")" : ""));
        return saved;
    }

    @Transactional
    public void removeFromAsset(Long assignmentId) {
        ComponentAssignment ca = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Component assignment not found with id: " + assignmentId));

        Component c = ca.getComponent();
        if (c != null) {
            c.setRemainingQuantity(Math.min(c.getTotalQuantity(), c.getRemainingQuantity() + ca.getQuantity()));
            componentRepository.save(c);
        }

        activityLogService.log(ActivityAction.CHECKIN, ItemType.COMPONENT, c != null ? c.getId() : null, ca.getAssetTag(),
                c != null ? c.getName() : "Component", null, null, "IT Admin",
                "Removed " + ca.getQuantity() + " unit(s) from asset [" + ca.getAssetTag() + " " + ca.getAssetName() + "]");

        assignmentRepository.delete(ca);
    }

    public List<ComponentAssignment> getAssignmentsForComponent(Long componentId) {
        return assignmentRepository.findByComponent_Id(componentId);
    }

    public List<ComponentAssignment> getAssignmentsForAsset(Long assetId) {
        return assignmentRepository.findByAssetId(assetId);
    }
}
