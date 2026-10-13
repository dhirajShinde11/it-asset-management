package com.company.itam.controller;

import com.company.itam.dto.AccessoryCheckoutDto;
import com.company.itam.dto.AccessoryRequestDto;
import com.company.itam.model.Accessory;
import com.company.itam.model.AccessoryCheckout;
import com.company.itam.service.AccessoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accessories")
@Tag(name = "Accessories Management", description = "Snipe-IT style accessories quantity tracking & checkout")
@CrossOrigin(origins = "*")
public class AccessoryController {

    private final AccessoryService accessoryService;

    public AccessoryController(AccessoryService accessoryService) {
        this.accessoryService = accessoryService;
    }

    @GetMapping
    @Operation(summary = "List all accessories with remaining quantities")
    public ResponseEntity<List<Accessory>> getAllAccessories() {
        return ResponseEntity.ok(accessoryService.getAllAccessories());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get accessory by ID")
    public ResponseEntity<Accessory> getAccessoryById(@PathVariable Long id) {
        return ResponseEntity.ok(accessoryService.getAccessoryById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new accessory batch in catalog")
    public ResponseEntity<Accessory> createAccessory(@Valid @RequestBody AccessoryRequestDto dto) {
        return new ResponseEntity<>(accessoryService.createAccessory(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update accessory details or quantity")
    public ResponseEntity<Accessory> updateAccessory(@PathVariable Long id, @Valid @RequestBody AccessoryRequestDto dto) {
        return ResponseEntity.ok(accessoryService.updateAccessory(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete accessory from catalog")
    public ResponseEntity<Map<String, String>> deleteAccessory(@PathVariable Long id) {
        accessoryService.deleteAccessory(id);
        return ResponseEntity.ok(Map.of("message", "Accessory deleted successfully"));
    }

    @PostMapping("/{id}/checkout")
    @Operation(summary = "Check out accessory units to a user")
    public ResponseEntity<AccessoryCheckout> checkoutAccessory(@PathVariable Long id, @Valid @RequestBody AccessoryCheckoutDto dto) {
        return ResponseEntity.ok(accessoryService.checkoutAccessory(id, dto));
    }

    @PostMapping("/checkin/{checkoutId}")
    @Operation(summary = "Check in / return accessory units to inventory")
    public ResponseEntity<Map<String, String>> checkinAccessory(@PathVariable Long checkoutId) {
        accessoryService.checkinAccessory(checkoutId);
        return ResponseEntity.ok(Map.of("message", "Accessory checked in and returned to available stock"));
    }

    @GetMapping("/{id}/checkouts")
    @Operation(summary = "List all users holding this accessory")
    public ResponseEntity<List<AccessoryCheckout>> getCheckouts(@PathVariable Long id) {
        return ResponseEntity.ok(accessoryService.getCheckoutsForAccessory(id));
    }
}
