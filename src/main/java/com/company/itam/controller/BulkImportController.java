package com.company.itam.controller;

import com.company.itam.dto.bulk.*;
import com.company.itam.service.BulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/bulk")
@CrossOrigin(origins = "*")
@Tag(name = "Bulk Upload Management", description = "Endpoints for batch importing hardware assets, user rosters, accessories, and consumables via CSV or JSON")
public class BulkImportController {

    private final BulkImportService bulkImportService;

    public BulkImportController(BulkImportService bulkImportService) {
        this.bulkImportService = bulkImportService;
    }

    @PostMapping("/assets")
    @Operation(summary = "Bulk import hardware assets via parsed JSON payload")
    public ResponseEntity<BulkImportResult> importAssetsJson(@RequestBody BulkAssetImportRequest request) {
        BulkImportResult result = bulkImportService.importAssets(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import hardware assets via direct CSV file upload")
    public ResponseEntity<BulkImportResult> importAssetsFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting,
            @RequestParam(value = "autoCreateUsers", defaultValue = "true") boolean autoCreateUsers) {
        try {
            if (file.isEmpty()) {
                BulkImportResult err = new BulkImportResult();
                err.setSuccess(false);
                err.setMessage("Uploaded CSV file is empty.");
                return ResponseEntity.badRequest().body(err);
            }

            List<BulkAssetRowDto> rows = bulkImportService.parseAssetCsv(file.getInputStream());
            BulkAssetImportRequest req = new BulkAssetImportRequest();
            req.setAssets(rows);
            req.setUpdateExisting(updateExisting);
            req.setAutoCreateUsers(autoCreateUsers);

            BulkImportResult result = bulkImportService.importAssets(req);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            BulkImportResult err = new BulkImportResult();
            err.setSuccess(false);
            err.setMessage("Failed to process CSV file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/users")
    @Operation(summary = "Bulk import employees/users via parsed JSON payload")
    public ResponseEntity<BulkImportResult> importUsersJson(@RequestBody BulkUserImportRequest request) {
        BulkImportResult result = bulkImportService.importUsers(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/users/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import employees/users via direct CSV file upload")
    public ResponseEntity<BulkImportResult> importUsersFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "updateExisting", defaultValue = "false") boolean updateExisting) {
        try {
            if (file.isEmpty()) {
                BulkImportResult err = new BulkImportResult();
                err.setSuccess(false);
                err.setMessage("Uploaded CSV file is empty.");
                return ResponseEntity.badRequest().body(err);
            }

            List<BulkUserRowDto> rows = bulkImportService.parseUserCsv(file.getInputStream());
            BulkUserImportRequest req = new BulkUserImportRequest();
            req.setUsers(rows);
            req.setUpdateExisting(updateExisting);

            BulkImportResult result = bulkImportService.importUsers(req);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            BulkImportResult err = new BulkImportResult();
            err.setSuccess(false);
            err.setMessage("Failed to process CSV file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    @PostMapping("/accessories")
    @Operation(summary = "Bulk import accessories via parsed JSON payload")
    public ResponseEntity<BulkImportResult> importAccessories(@RequestBody BulkAccessoryImportRequest request) {
        BulkImportResult result = bulkImportService.importAccessories(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/consumables")
    @Operation(summary = "Bulk import consumables via parsed JSON payload")
    public ResponseEntity<BulkImportResult> importConsumables(@RequestBody BulkConsumableImportRequest request) {
        BulkImportResult result = bulkImportService.importConsumables(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/templates/{type}")
    @Operation(summary = "Download a ready-to-use CSV template populated with sample rows")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) {
        String filename;
        String csvContent;

        switch (type.toLowerCase()) {
            case "users":
            case "employees":
                filename = "itam_users_template.csv";
                csvContent = "Full Name,Email,Department,Job Title,Phone,Role\n" +
                        "David Miller,david.m@company.com,Engineering,Senior Software Engineer,+1 555-0101,EMPLOYEE\n" +
                        "Rachel Green,rachel.g@company.com,Marketing,Brand Specialist,+1 555-0102,EMPLOYEE\n" +
                        "Thomas Wayne,thomas.w@company.com,IT Operations,Systems Administrator,+1 555-0103,ADMIN\n";
                break;

            case "accessories":
                filename = "itam_accessories_template.csv";
                csvContent = "Name,Category,Manufacturer,Model,Quantity,Cost,Location,Notes\n" +
                        "Magic Keyboard with Touch ID,Keyboards,Apple,A2449,15,199.00,IT Storage Room A,US Layout Wireless\n" +
                        "MX Master 3S Wireless Mouse,Mice,Logitech,910-006557,25,99.99,IT Storage Room A,Ergonomic Bluetooth\n" +
                        "CalDigit TS4 Thunderbolt 4 Dock,Docks,CalDigit,TS4-US,10,399.95,IT Storage Room B,230W Power Delivery\n";
                break;

            case "consumables":
                filename = "itam_consumables_template.csv";
                csvContent = "Name,Category,Manufacturer,Item Number,Quantity,Cost,Location,Notes\n" +
                        "USB-C to USB-C Cable 2M,Cables,Anker,A8856,50,15.99,Bin C-12,100W Power Delivery Braided\n" +
                        "Cat6 Ethernet Patch Cable 3ft,Cables,Monoprice,11218,100,3.50,Bin C-04,Blue jacket molded boots\n" +
                        "HP 58A Black LaserJet Toner,Toner & Ink,HP,CF258A,8,114.99,Cabinet 2,High yield cartridge\n";
                break;

            case "assets":
            default:
                filename = "itam_assets_template.csv";
                csvContent = "Asset Tag,Name,Category,Manufacturer,Model,Serial,Status,Condition,Cost,Assigned To,Email,Department,Location,Warranty\n" +
                        "LT-2024-001,MacBook Pro 16\",LAPTOP,Apple,M3 Pro 36GB,C02G12345678,ASSIGNED,EXCELLENT,2499.00,Sarah Jenkins,sarah.j@company.com,Engineering,San Francisco HQ,2027-01-15\n" +
                        "DT-2024-010,Precision 3660 Tower,DESKTOP,Dell,i9-13900K 64GB,DL99887766,AVAILABLE,GOOD,1850.00,,,IT Storage Room B,Austin Campus,2026-06-30\n" +
                        "SRV-2024-002,PowerEdge R750,SERVER,Dell,2x Xeon Silver 4314,SRV44332211,AVAILABLE,EXCELLENT,4200.00,,,Data Center Rack 04,Chicago Hub,2028-12-01\n" +
                        "MON-2024-055,UltraSharp 27\" 4K,MONITOR,Dell,U2723QE,CN00112233,ASSIGNED,EXCELLENT,580.00,Marcus Brody,marcus.b@company.com,Product Design,San Francisco HQ,2026-08-15\n" +
                        "NET-2024-008,Catalyst 9300 48-Port,NETWORKING,Cisco,C9300-48P-A,FOC22334455,AVAILABLE,GOOD,3100.00,,,MDF Room 1,San Francisco HQ,2027-04-10\n";
                break;
        }

        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}
