package com.company.itam.controller;

import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.SoftwareLicense;
import com.company.itam.repository.SoftwareLicenseRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/licenses")
@CrossOrigin(origins = "*")
@Tag(name = "Software Licenses", description = "Endpoints for managing software licenses and seat allocations")
public class SoftwareLicenseController {

    private final SoftwareLicenseRepository licenseRepository;

    public SoftwareLicenseController(SoftwareLicenseRepository licenseRepository) {
        this.licenseRepository = licenseRepository;
    }

    @GetMapping
    @Operation(summary = "Get all software licenses")
    public ResponseEntity<List<SoftwareLicense>> getAllLicenses() {
        return ResponseEntity.ok(licenseRepository.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get license by ID")
    public ResponseEntity<SoftwareLicense> getLicenseById(@PathVariable Long id) {
        return ResponseEntity.ok(licenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("License not found with id: " + id)));
    }

    @PostMapping
    @Operation(summary = "Add a new software license")
    public ResponseEntity<SoftwareLicense> createLicense(@Valid @RequestBody SoftwareLicense license) {
        return new ResponseEntity<>(licenseRepository.save(license), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing software license")
    public ResponseEntity<SoftwareLicense> updateLicense(@PathVariable Long id, @Valid @RequestBody SoftwareLicense updated) {
        SoftwareLicense existing = licenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("License not found with id: " + id));

        existing.setSoftwareName(updated.getSoftwareName());
        existing.setVendor(updated.getVendor());
        existing.setLicenseType(updated.getLicenseType());
        existing.setLicenseKey(updated.getLicenseKey());
        existing.setTotalSeats(updated.getTotalSeats());
        existing.setUsedSeats(updated.getUsedSeats());
        existing.setPurchaseDate(updated.getPurchaseDate());
        existing.setExpiryDate(updated.getExpiryDate());
        existing.setAnnualCost(updated.getAnnualCost());
        existing.setDepartment(updated.getDepartment());

        return ResponseEntity.ok(licenseRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a software license")
    public ResponseEntity<Void> deleteLicense(@PathVariable Long id) {
        if (!licenseRepository.existsById(id)) {
            throw new ResourceNotFoundException("License not found with id: " + id);
        }
        licenseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
