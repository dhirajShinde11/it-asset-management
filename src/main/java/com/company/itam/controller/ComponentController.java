package com.company.itam.controller;

import com.company.itam.dto.ComponentAssignDto;
import com.company.itam.dto.ComponentRequestDto;
import com.company.itam.model.Component;
import com.company.itam.model.ComponentAssignment;
import com.company.itam.service.ComponentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/components")
@Tag(name = "Components Management", description = "Modular component parts tracking & asset installation")
@CrossOrigin(origins = "*")
public class ComponentController {

    private final ComponentService componentService;

    public ComponentController(ComponentService componentService) {
        this.componentService = componentService;
    }

    @GetMapping
    @Operation(summary = "List all internal components and parts")
    public ResponseEntity<List<Component>> getAllComponents() {
        return ResponseEntity.ok(componentService.getAllComponents());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get component by ID")
    public ResponseEntity<Component> getComponentById(@PathVariable Long id) {
        return ResponseEntity.ok(componentService.getComponentById(id));
    }

    @PostMapping
    @Operation(summary = "Register new component part in catalog")
    public ResponseEntity<Component> createComponent(@Valid @RequestBody ComponentRequestDto dto) {
        return new ResponseEntity<>(componentService.createComponent(dto), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update component details or stock quantity")
    public ResponseEntity<Component> updateComponent(@PathVariable Long id, @Valid @RequestBody ComponentRequestDto dto) {
        return ResponseEntity.ok(componentService.updateComponent(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete component from catalog")
    public ResponseEntity<Map<String, String>> deleteComponent(@PathVariable Long id) {
        componentService.deleteComponent(id);
        return ResponseEntity.ok(Map.of("message", "Component deleted successfully"));
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Install / assign component into a parent hardware asset")
    public ResponseEntity<ComponentAssignment> assignToAsset(@PathVariable Long id, @Valid @RequestBody ComponentAssignDto dto) {
        return ResponseEntity.ok(componentService.assignToAsset(id, dto));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @Operation(summary = "Remove component from asset and return to stock")
    public ResponseEntity<Map<String, String>> removeFromAsset(@PathVariable Long assignmentId) {
        componentService.removeFromAsset(assignmentId);
        return ResponseEntity.ok(Map.of("message", "Component uninstalled from asset and returned to stock"));
    }

    @GetMapping("/{id}/assignments")
    @Operation(summary = "List all assets containing this component")
    public ResponseEntity<List<ComponentAssignment>> getAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(componentService.getAssignmentsForComponent(id));
    }

    @GetMapping("/asset/{assetId}")
    @Operation(summary = "List all components installed in a specific asset")
    public ResponseEntity<List<ComponentAssignment>> getComponentsInAsset(@PathVariable Long assetId) {
        return ResponseEntity.ok(componentService.getAssignmentsForAsset(assetId));
    }
}
