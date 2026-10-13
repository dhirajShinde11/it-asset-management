package com.company.itam.controller;

import com.company.itam.model.ActivityAction;
import com.company.itam.model.ActivityLog;
import com.company.itam.model.ItemType;
import com.company.itam.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity-logs")
@Tag(name = "Activity & Audit Stream", description = "Snipe-IT real-time audit log of all check-in, check-out, and maintenance activities")
@CrossOrigin(origins = "*")
public class ActivityLogController {

    private final ActivityLogService activityLogService;

    public ActivityLogController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "List all activity log events")
    public ResponseEntity<List<ActivityLog>> getAllLogs() {
        return ResponseEntity.ok(activityLogService.getAllLogs());
    }

    @GetMapping("/recent")
    @Operation(summary = "Get the 50 most recent activity audit log entries")
    public ResponseEntity<List<ActivityLog>> getRecentLogs() {
        return ResponseEntity.ok(activityLogService.getRecentLogs());
    }

    @GetMapping("/by-type/{itemType}")
    @Operation(summary = "Filter activity log by item type (ASSET, ACCESSORY, CONSUMABLE, COMPONENT, LICENSE, USER)")
    public ResponseEntity<List<ActivityLog>> getLogsByItemType(@PathVariable ItemType itemType) {
        return ResponseEntity.ok(activityLogService.getLogsByItemType(itemType));
    }

    @GetMapping("/user/{email}")
    @Operation(summary = "Get full custody & activity history for a specific user")
    public ResponseEntity<List<ActivityLog>> getLogsByUser(@PathVariable String email) {
        return ResponseEntity.ok(activityLogService.getLogsByUser(email));
    }
}
