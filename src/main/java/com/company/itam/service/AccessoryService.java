package com.company.itam.service;

import com.company.itam.dto.AccessoryCheckoutDto;
import com.company.itam.dto.AccessoryRequestDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.Accessory;
import com.company.itam.model.AccessoryCheckout;
import com.company.itam.model.ActivityAction;
import com.company.itam.model.ItemType;
import com.company.itam.repository.AccessoryCheckoutRepository;
import com.company.itam.repository.AccessoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccessoryService {

    private final AccessoryRepository accessoryRepository;
    private final AccessoryCheckoutRepository checkoutRepository;
    private final ActivityLogService activityLogService;

    public AccessoryService(AccessoryRepository accessoryRepository,
                            AccessoryCheckoutRepository checkoutRepository,
                            ActivityLogService activityLogService) {
        this.accessoryRepository = accessoryRepository;
        this.checkoutRepository = checkoutRepository;
        this.activityLogService = activityLogService;
    }

    public List<Accessory> getAllAccessories() {
        return accessoryRepository.findAll();
    }

    public Accessory getAccessoryById(Long id) {
        return accessoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Accessory not found with id: " + id));
    }

    @Transactional
    public Accessory createAccessory(AccessoryRequestDto dto) {
        Accessory a = new Accessory();
        a.setName(dto.getName().trim());
        a.setCategory(dto.getCategory());
        a.setManufacturer(dto.getManufacturer());
        a.setModelNumber(dto.getModelNumber());
        a.setTotalQuantity(dto.getTotalQuantity() != null ? dto.getTotalQuantity() : 1);
        a.setRemainingQuantity(a.getTotalQuantity());
        a.setMinQuantity(dto.getMinQuantity() != null ? dto.getMinQuantity() : 5);
        a.setPurchaseCost(dto.getPurchaseCost());
        a.setPurchaseDate(dto.getPurchaseDate());
        a.setLocation(dto.getLocation());
        a.setNotes(dto.getNotes());
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());

        Accessory saved = accessoryRepository.save(a);
        activityLogService.log(ActivityAction.CREATED, ItemType.ACCESSORY, saved.getId(), null, saved.getName(),
                null, null, "IT Admin", "Added new accessory stock: " + saved.getTotalQuantity() + " units");
        return saved;
    }

    @Transactional
    public Accessory updateAccessory(Long id, AccessoryRequestDto dto) {
        Accessory a = getAccessoryById(id);
        int diff = dto.getTotalQuantity() - a.getTotalQuantity();
        a.setName(dto.getName().trim());
        a.setCategory(dto.getCategory());
        a.setManufacturer(dto.getManufacturer());
        a.setModelNumber(dto.getModelNumber());
        a.setTotalQuantity(dto.getTotalQuantity());
        a.setRemainingQuantity(Math.max(0, a.getRemainingQuantity() + diff));
        if (dto.getMinQuantity() != null) a.setMinQuantity(dto.getMinQuantity());
        a.setPurchaseCost(dto.getPurchaseCost());
        a.setPurchaseDate(dto.getPurchaseDate());
        a.setLocation(dto.getLocation());
        a.setNotes(dto.getNotes());
        a.setUpdatedAt(LocalDateTime.now());

        Accessory updated = accessoryRepository.save(a);
        activityLogService.log(ActivityAction.UPDATED, ItemType.ACCESSORY, updated.getId(), null, updated.getName(),
                null, null, "IT Admin", "Updated accessory inventory");
        return updated;
    }

    @Transactional
    public void deleteAccessory(Long id) {
        Accessory a = getAccessoryById(id);
        accessoryRepository.delete(a);
        activityLogService.log(ActivityAction.DELETED, ItemType.ACCESSORY, id, null, a.getName(),
                null, null, "IT Admin", "Deleted accessory from catalog");
    }

    @Transactional
    public AccessoryCheckout checkoutAccessory(Long accessoryId, AccessoryCheckoutDto dto) {
        Accessory a = getAccessoryById(accessoryId);
        int qty = dto.getQuantity() != null && dto.getQuantity() > 0 ? dto.getQuantity() : 1;

        if (a.getRemainingQuantity() < qty) {
            throw new IllegalArgumentException("Cannot checkout " + qty + " units. Only " + a.getRemainingQuantity() + " available in stock.");
        }

        a.setRemainingQuantity(a.getRemainingQuantity() - qty);
        accessoryRepository.save(a);

        AccessoryCheckout checkout = new AccessoryCheckout();
        checkout.setAccessory(a);
        checkout.setUserId(dto.getUserId());
        checkout.setUserName(dto.getUserName());
        checkout.setUserEmail(dto.getUserEmail().trim().toLowerCase());
        checkout.setUserDepartment(dto.getUserDepartment());
        checkout.setQuantity(qty);
        checkout.setAssignedDate(dto.getAssignedDate());
        checkout.setNotes(dto.getNotes());
        checkout.setCreatedAt(LocalDateTime.now());

        AccessoryCheckout saved = checkoutRepository.save(checkout);
        activityLogService.log(ActivityAction.CHECKOUT, ItemType.ACCESSORY, a.getId(), null, a.getName(),
                dto.getUserName(), dto.getUserEmail(), "IT Admin",
                "Checked out " + qty + " unit(s) to " + dto.getUserName() + (dto.getNotes() != null ? " (" + dto.getNotes() + ")" : ""));
        return saved;
    }

    @Transactional
    public void checkinAccessory(Long checkoutId) {
        AccessoryCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Accessory checkout record not found with id: " + checkoutId));

        Accessory a = checkout.getAccessory();
        if (a != null) {
            a.setRemainingQuantity(Math.min(a.getTotalQuantity(), a.getRemainingQuantity() + checkout.getQuantity()));
            accessoryRepository.save(a);
        }

        activityLogService.log(ActivityAction.CHECKIN, ItemType.ACCESSORY, a != null ? a.getId() : null, null,
                a != null ? a.getName() : "Accessory", checkout.getUserName(), checkout.getUserEmail(), "IT Admin",
                "Checked in " + checkout.getQuantity() + " unit(s) returned by " + checkout.getUserName());

        checkoutRepository.delete(checkout);
    }

    public List<AccessoryCheckout> getCheckoutsForAccessory(Long accessoryId) {
        return checkoutRepository.findByAccessory_Id(accessoryId);
    }

    public List<AccessoryCheckout> getCheckoutsForUser(String userEmail) {
        return checkoutRepository.findByUserEmailIgnoreCase(userEmail);
    }
}
