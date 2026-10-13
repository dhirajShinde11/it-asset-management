package com.company.itam.controller;

import com.company.itam.model.MaintenanceRecord;
import com.company.itam.repository.MaintenanceRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/maintenance")
@CrossOrigin(origins = "*")
@Tag(name = "Maintenance Records", description = "Endpoints for inspecting service history across all assets")
public class MaintenanceController {

    private final MaintenanceRecordRepository maintenanceRepository;

    public MaintenanceController(MaintenanceRecordRepository maintenanceRepository) {
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping
    @Operation(summary = "Get all maintenance logs chronologically")
    public ResponseEntity<List<MaintenanceRecord>> getAllLogs() {
        return ResponseEntity.ok(maintenanceRepository.findAllByOrderByServiceDateDesc());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a maintenance record")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        maintenanceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
