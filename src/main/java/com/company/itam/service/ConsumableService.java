package com.company.itam.service;

import com.company.itam.dto.ConsumableCheckoutDto;
import com.company.itam.dto.ConsumableRequestDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.ActivityAction;
import com.company.itam.model.Consumable;
import com.company.itam.model.ConsumableCheckout;
import com.company.itam.model.ItemType;
import com.company.itam.repository.ConsumableCheckoutRepository;
import com.company.itam.repository.ConsumableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConsumableService {

    private final ConsumableRepository consumableRepository;
    private final ConsumableCheckoutRepository checkoutRepository;
    private final ActivityLogService activityLogService;

    public ConsumableService(ConsumableRepository consumableRepository,
                             ConsumableCheckoutRepository checkoutRepository,
                             ActivityLogService activityLogService) {
        this.consumableRepository = consumableRepository;
        this.checkoutRepository = checkoutRepository;
        this.activityLogService = activityLogService;
    }

    public List<Consumable> getAllConsumables() {
        return consumableRepository.findAll();
    }

    public Consumable getConsumableById(Long id) {
        return consumableRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consumable not found with id: " + id));
    }

    @Transactional
    public Consumable createConsumable(ConsumableRequestDto dto) {
        Consumable c = new Consumable();
        c.setName(dto.getName().trim());
        c.setCategory(dto.getCategory());
        c.setManufacturer(dto.getManufacturer());
        c.setItemNumber(dto.getItemNumber());
        c.setTotalQuantity(dto.getTotalQuantity() != null ? dto.getTotalQuantity() : 1);
        c.setRemainingQuantity(c.getTotalQuantity());
        c.setMinQuantity(dto.getMinQuantity() != null ? dto.getMinQuantity() : 5);
        c.setPurchaseCost(dto.getPurchaseCost());
        c.setPurchaseDate(dto.getPurchaseDate());
        c.setLocation(dto.getLocation());
        c.setNotes(dto.getNotes());
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());

        Consumable saved = consumableRepository.save(c);
        activityLogService.log(ActivityAction.CREATED, ItemType.CONSUMABLE, saved.getId(), null, saved.getName(),
                null, null, "IT Admin", "Added consumable inventory: " + saved.getTotalQuantity() + " units");
        return saved;
    }

    @Transactional
    public Consumable updateConsumable(Long id, ConsumableRequestDto dto) {
        Consumable c = getConsumableById(id);
        int diff = dto.getTotalQuantity() - c.getTotalQuantity();
        c.setName(dto.getName().trim());
        c.setCategory(dto.getCategory());
        c.setManufacturer(dto.getManufacturer());
        c.setItemNumber(dto.getItemNumber());
        c.setTotalQuantity(dto.getTotalQuantity());
        c.setRemainingQuantity(Math.max(0, c.getRemainingQuantity() + diff));
        if (dto.getMinQuantity() != null) c.setMinQuantity(dto.getMinQuantity());
        c.setPurchaseCost(dto.getPurchaseCost());
        c.setPurchaseDate(dto.getPurchaseDate());
        c.setLocation(dto.getLocation());
        c.setNotes(dto.getNotes());
        c.setUpdatedAt(LocalDateTime.now());

        Consumable updated = consumableRepository.save(c);
        activityLogService.log(ActivityAction.UPDATED, ItemType.CONSUMABLE, updated.getId(), null, updated.getName(),
                null, null, "IT Admin", "Updated consumable inventory");
        return updated;
    }

    @Transactional
    public void deleteConsumable(Long id) {
        Consumable c = getConsumableById(id);
        consumableRepository.delete(c);
        activityLogService.log(ActivityAction.DELETED, ItemType.CONSUMABLE, id, null, c.getName(),
                null, null, "IT Admin", "Deleted consumable from catalog");
    }

    @Transactional
    public ConsumableCheckout giveOutConsumable(Long consumableId, ConsumableCheckoutDto dto) {
        Consumable c = consumableRepository.findByIdForUpdate(consumableId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumable not found with id: " + consumableId));
        int qty = dto.getQuantity() != null && dto.getQuantity() > 0 ? dto.getQuantity() : 1;

        if (c.getRemainingQuantity() < qty) {
            throw new IllegalArgumentException("Cannot dispense " + qty + " units. Only " + c.getRemainingQuantity() + " available in stock.");
        }

        c.setRemainingQuantity(c.getRemainingQuantity() - qty);
        consumableRepository.save(c);

        ConsumableCheckout checkout = new ConsumableCheckout();
        checkout.setConsumable(c);
        checkout.setUserId(dto.getUserId());
        checkout.setUserName(dto.getUserName());
        checkout.setUserEmail(dto.getUserEmail().trim().toLowerCase());
        checkout.setQuantity(qty);
        checkout.setDateGiven(dto.getDateGiven());
        checkout.setNotes(dto.getNotes());
        checkout.setCreatedAt(LocalDateTime.now());

        ConsumableCheckout saved = checkoutRepository.save(checkout);
        activityLogService.log(ActivityAction.CHECKOUT, ItemType.CONSUMABLE, c.getId(), null, c.getName(),
                dto.getUserName(), dto.getUserEmail(), "IT Admin",
                "Dispensed " + qty + " unit(s) to " + dto.getUserName() + (dto.getNotes() != null ? " (" + dto.getNotes() + ")" : ""));
        return saved;
    }

    public List<ConsumableCheckout> getCheckoutsForConsumable(Long consumableId) {
        return checkoutRepository.findByConsumable_Id(consumableId);
    }

    public List<ConsumableCheckout> getCheckoutsForUser(String userEmail) {
        return checkoutRepository.findByUserEmailIgnoreCase(userEmail);
    }
}
