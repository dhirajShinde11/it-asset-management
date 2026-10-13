package com.company.itam.controller;

import com.company.itam.dto.ConsumableCheckoutDto;
import com.company.itam.dto.ConsumableRequestDto;
import com.company.itam.model.Consumable;
import com.company.itam.model.ConsumableCheckout;
import com.company.itam.service.ConsumableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consumables")
@Tag(name = "Consumables Management", description = "Consumable supplies inventory & employee dispensing")
@CrossOrigin(origins = "*")
public class ConsumableController {

    private final ConsumableService consumableService;

    public ConsumableController(ConsumableService consumableService) {
        this.consumableService = consumableService;
    }

    @GetMapping
    @Operation(summary = "List all consumables with remaining stock")
    public ResponseEntity<List<Consumable>> getAllConsumables() {
        return ResponseEntity.ok(consumableService.getAllConsumables());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get consumable by ID")
    public ResponseEntity<Consumable> getConsumableById(@PathVariable Long id) {
        return ResponseEntity.ok(consumableService.getConsumableById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new consumable item in catalog")
    public ResponseEntity<Consumable> createConsumable(@Valid @RequestBody ConsumableRequestDto dto) {
        return new ResponseEntity<>(consumableService.createConsumable(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update consumable details or stock quantity")
    public ResponseEntity<Consumable> updateConsumable(@PathVariable Long id, @Valid @RequestBody ConsumableRequestDto dto) {
        return ResponseEntity.ok(consumableService.updateConsumable(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete consumable from catalog")
    public ResponseEntity<Map<String, String>> deleteConsumable(@PathVariable Long id) {
        consumableService.deleteConsumable(id);
        return ResponseEntity.ok(Map.of("message", "Consumable deleted successfully"));
    }

    @PostMapping("/{id}/giveout")
    @Operation(summary = "Dispense / give out consumable units to a user")
    public ResponseEntity<ConsumableCheckout> giveOutConsumable(@PathVariable Long id, @Valid @RequestBody ConsumableCheckoutDto dto) {
        return ResponseEntity.ok(consumableService.giveOutConsumable(id, dto));
    }

    @GetMapping("/{id}/checkouts")
    @Operation(summary = "List all recipients of this consumable")
    public ResponseEntity<List<ConsumableCheckout>> getCheckouts(@PathVariable Long id) {
        return ResponseEntity.ok(consumableService.getCheckoutsForConsumable(id));
    }
}
