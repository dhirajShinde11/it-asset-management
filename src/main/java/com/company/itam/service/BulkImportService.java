package com.company.itam.service;

import com.company.itam.dto.bulk.*;
import com.company.itam.model.*;
import com.company.itam.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class BulkImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkImportService.class);

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final AccessoryRepository accessoryRepository;
    private final ConsumableRepository consumableRepository;
    private final AuthService authService;
    private final ActivityLogService activityLogService;

    public BulkImportService(AssetRepository assetRepository,
                             UserRepository userRepository,
                             AccessoryRepository accessoryRepository,
                             ConsumableRepository consumableRepository,
                             AuthService authService,
                             ActivityLogService activityLogService) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.accessoryRepository = accessoryRepository;
        this.consumableRepository = consumableRepository;
        this.authService = authService;
        this.activityLogService = activityLogService;
    }

    /**
     * Bulk import hardware assets from list of row DTOs
     */
    @Transactional
    public BulkImportResult importAssets(BulkAssetImportRequest request) {
        BulkImportResult result = new BulkImportResult();
        List<BulkAssetRowDto> rows = request.getAssets();
        if (rows == null || rows.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No asset data rows provided for import.");
            return result;
        }

        result.setTotalRows(rows.size());
        int rowIndex = 1;

        for (BulkAssetRowDto row : rows) {
            rowIndex++;
            String tag = clean(row.getAssetTag());
            String name = clean(row.getName());

            if (tag == null || tag.isEmpty()) {
                result.getErrors().add("Row " + rowIndex + ": Skipped because Asset Tag is required.");
                result.setErrorCount(result.getErrorCount() + 1);
                continue;
            }

            if (name == null || name.isEmpty()) {
                // If name is missing, generate fallback name from category & tag
                name = (row.getCategory() != null ? row.getCategory() : "Hardware") + " (" + tag + ")";
            }

            Optional<Asset> existingOpt = assetRepository.findByAssetTag(tag);
            if (existingOpt.isPresent()) {
                if (!request.isUpdateExisting()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getWarnings().add("Row " + rowIndex + ": Asset Tag '" + tag + "' already exists (skipped).");
                    continue;
                }

                // Update existing asset
                Asset asset = existingOpt.get();
                populateAssetFields(asset, row, request.isAutoCreateUsers());
                assetRepository.save(asset);
                result.setUpdatedCount(result.getUpdatedCount() + 1);
            } else {
                // Create new asset
                Asset asset = new Asset();
                asset.setAssetTag(tag);
                populateAssetFields(asset, row, request.isAutoCreateUsers());
                Asset saved = assetRepository.save(asset);
                result.setCreatedCount(result.getCreatedCount() + 1);
            }
        }

        activityLogService.log(ActivityAction.CREATED, ItemType.ASSET, null, "BULK-IMPORT",
                "Bulk Asset Import", "System", null, "IT Admin",
                "Imported " + result.getTotalRows() + " assets: " + result.getCreatedCount() + " created, " +
                        result.getUpdatedCount() + " updated, " + result.getSkippedCount() + " skipped.");

        result.setSuccess(result.getErrorCount() == 0 || (result.getCreatedCount() + result.getUpdatedCount()) > 0);
        result.setMessage("Bulk asset import completed. " + result.getCreatedCount() + " created, " +
                result.getUpdatedCount() + " updated, " + result.getSkippedCount() + " skipped.");
        return result;
    }

    /**
     * Bulk import employees / organization users
     */
    @Transactional
    public BulkImportResult importUsers(BulkUserImportRequest request) {
        BulkImportResult result = new BulkImportResult();
        List<BulkUserRowDto> rows = request.getUsers();
        if (rows == null || rows.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No user data rows provided for import.");
            return result;
        }

        result.setTotalRows(rows.size());
        int rowIndex = 1;

        for (BulkUserRowDto row : rows) {
            rowIndex++;
            String email = clean(row.getEmail());
            String fullName = clean(row.getFullName());

            if (email == null || email.isEmpty() || !email.contains("@")) {
                result.getErrors().add("Row " + rowIndex + ": Invalid or missing email address.");
                result.setErrorCount(result.getErrorCount() + 1);
                continue;
            }

            if (fullName == null || fullName.isEmpty()) {
                fullName = email.split("@")[0];
            }

            String normalizedEmail = email.toLowerCase();
            Optional<AppUser> existingOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

            if (existingOpt.isPresent()) {
                if (!request.isUpdateExisting()) {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    result.getWarnings().add("Row " + rowIndex + ": User '" + normalizedEmail + "' already exists (skipped).");
                    continue;
                }

                AppUser user = existingOpt.get();
                user.setFullName(fullName);
                if (clean(row.getDepartment()) != null) user.setDepartment(clean(row.getDepartment()));
                if (clean(row.getJobTitle()) != null) user.setJobTitle(clean(row.getJobTitle()));
                if (clean(row.getPhoneNumber()) != null) user.setPhoneNumber(clean(row.getPhoneNumber()));
                if (clean(row.getRole()) != null) user.setRole(parseRole(row.getRole()));
                userRepository.save(user);
                result.setUpdatedCount(result.getUpdatedCount() + 1);
            } else {
                AppUser user = new AppUser();
                user.setEmail(normalizedEmail);
                user.setFullName(fullName);
                user.setDepartment(clean(row.getDepartment()));
                user.setJobTitle(clean(row.getJobTitle()));
                user.setPhoneNumber(clean(row.getPhoneNumber()));
                user.setRole(parseRole(row.getRole()));
                user.setStatus(UserStatus.ACTIVE);

                String pwd = clean(row.getInitialPassword());
                if (pwd == null || pwd.isEmpty()) {
                    pwd = "Pass@" + (1000 + (int)(Math.random() * 9000));
                }
                user.setPassword(authService.hashPassword(pwd));
                user.setTemporaryPassword(pwd);

                userRepository.save(user);
                result.setCreatedCount(result.getCreatedCount() + 1);
            }
        }

        activityLogService.log(ActivityAction.CREATED, ItemType.USER, null, "BULK-USER-IMPORT",
                "Bulk User Import", "Directory", null, "IT Admin",
                "Imported " + result.getTotalRows() + " users: " + result.getCreatedCount() + " created, " +
                        result.getUpdatedCount() + " updated, " + result.getSkippedCount() + " skipped.");

        result.setSuccess(result.getErrorCount() == 0 || (result.getCreatedCount() + result.getUpdatedCount()) > 0);
        result.setMessage("Bulk user onboarding completed. " + result.getCreatedCount() + " created, " +
                result.getUpdatedCount() + " updated, " + result.getSkippedCount() + " skipped.");
        return result;
    }

    /**
     * Bulk import accessories
     */
    @Transactional
    public BulkImportResult importAccessories(BulkAccessoryImportRequest request) {
        BulkImportResult result = new BulkImportResult();
        List<BulkAccessoryRowDto> rows = request.getAccessories();
        if (rows == null || rows.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No accessory rows provided.");
            return result;
        }

        result.setTotalRows(rows.size());
        for (BulkAccessoryRowDto row : rows) {
            String name = clean(row.getName());
            if (name == null || name.isEmpty()) {
                result.setErrorCount(result.getErrorCount() + 1);
                continue;
            }

            Accessory acc = new Accessory();
            acc.setName(name);
            acc.setCategory(clean(row.getCategory()) != null ? clean(row.getCategory()) : "General");
            acc.setManufacturer(clean(row.getManufacturer()));
            acc.setModelNumber(clean(row.getModelNumber()));
            int qty = (row.getTotalQuantity() != null && row.getTotalQuantity() > 0) ? row.getTotalQuantity() : 1;
            acc.setTotalQuantity(qty);
            acc.setRemainingQuantity(qty);
            acc.setPurchaseCost(parseCost(row.getPurchaseCost()));
            acc.setLocation(clean(row.getLocation()));
            acc.setNotes(clean(row.getNotes()));

            accessoryRepository.save(acc);
            result.setCreatedCount(result.getCreatedCount() + 1);
        }

        result.setSuccess(true);
        result.setMessage("Bulk accessories imported: " + result.getCreatedCount() + " created.");
        return result;
    }

    /**
     * Bulk import consumables
     */
    @Transactional
    public BulkImportResult importConsumables(BulkConsumableImportRequest request) {
        BulkImportResult result = new BulkImportResult();
        List<BulkConsumableRowDto> rows = request.getConsumables();
        if (rows == null || rows.isEmpty()) {
            result.setSuccess(false);
            result.setMessage("No consumable rows provided.");
            return result;
        }

        result.setTotalRows(rows.size());
        for (BulkConsumableRowDto row : rows) {
            String name = clean(row.getName());
            if (name == null || name.isEmpty()) {
                result.setErrorCount(result.getErrorCount() + 1);
                continue;
            }

            Consumable con = new Consumable();
            con.setName(name);
            con.setCategory(clean(row.getCategory()) != null ? clean(row.getCategory()) : "General");
            con.setManufacturer(clean(row.getManufacturer()));
            con.setItemNumber(clean(row.getItemNumber()));
            int qty = (row.getTotalQuantity() != null && row.getTotalQuantity() > 0) ? row.getTotalQuantity() : 1;
            con.setTotalQuantity(qty);
            con.setRemainingQuantity(qty);
            con.setPurchaseCost(parseCost(row.getPurchaseCost()));
            con.setLocation(clean(row.getLocation()));
            con.setNotes(clean(row.getNotes()));

            consumableRepository.save(con);
            result.setCreatedCount(result.getCreatedCount() + 1);
        }

        result.setSuccess(true);
        result.setMessage("Bulk consumables imported: " + result.getCreatedCount() + " created.");
        return result;
    }

    /**
     * Parse raw CSV stream into List of BulkAssetRowDto
     */
    public List<BulkAssetRowDto> parseAssetCsv(InputStream inputStream) throws Exception {
        List<List<String>> table = parseCsvTable(inputStream);
        if (table.isEmpty()) return Collections.emptyList();

        List<String> headers = table.get(0);
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerMap.put(normalizeHeader(headers.get(i)), i);
        }

        List<BulkAssetRowDto> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cols = table.get(r);
            if (cols.isEmpty() || (cols.size() == 1 && cols.get(0).trim().isEmpty())) continue;

            BulkAssetRowDto dto = new BulkAssetRowDto();
            dto.setAssetTag(getValue(cols, headerMap, "assettag", "tag", "asset_tag"));
            dto.setName(getValue(cols, headerMap, "name", "assetname", "asset_name"));
            dto.setCategory(getValue(cols, headerMap, "category", "type"));
            dto.setManufacturer(getValue(cols, headerMap, "manufacturer", "make", "brand"));
            dto.setModel(getValue(cols, headerMap, "model", "modelnumber", "model_number"));
            dto.setSerialNumber(getValue(cols, headerMap, "serial", "serialnumber", "serial_number", "serial#"));
            dto.setStatus(getValue(cols, headerMap, "status", "state"));
            dto.setCondition(getValue(cols, headerMap, "condition"));
            dto.setPurchaseCost(getValue(cols, headerMap, "cost", "purchasecost", "purchase_cost", "price"));
            dto.setPurchaseDate(getValue(cols, headerMap, "purchasedate", "purchase_date", "date"));
            dto.setWarrantyExpiryDate(getValue(cols, headerMap, "warranty", "warrantyexpiry", "warrantyexpirydate", "warranty_expiry"));
            dto.setLocation(getValue(cols, headerMap, "location", "site"));
            dto.setAssignedEmployeeName(getValue(cols, headerMap, "assignedto", "assignee", "employee", "assigned_to", "employeename"));
            dto.setAssignedEmployeeEmail(getValue(cols, headerMap, "email", "assignedemail", "employeeemail", "assigned_email"));
            dto.setAssignedDepartment(getValue(cols, headerMap, "department", "dept"));
            dto.setSpecifications(getValue(cols, headerMap, "specifications", "specs"));
            dto.setNotes(getValue(cols, headerMap, "notes", "comments", "description"));

            if (dto.getAssetTag() != null && !dto.getAssetTag().isEmpty()) {
                rows.add(dto);
            }
        }
        return rows;
    }

    /**
     * Parse raw CSV stream into List of BulkUserRowDto
     */
    public List<BulkUserRowDto> parseUserCsv(InputStream inputStream) throws Exception {
        List<List<String>> table = parseCsvTable(inputStream);
        if (table.isEmpty()) return Collections.emptyList();

        List<String> headers = table.get(0);
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerMap.put(normalizeHeader(headers.get(i)), i);
        }

        List<BulkUserRowDto> rows = new ArrayList<>();
        for (int r = 1; r < table.size(); r++) {
            List<String> cols = table.get(r);
            if (cols.isEmpty() || (cols.size() == 1 && cols.get(0).trim().isEmpty())) continue;

            BulkUserRowDto dto = new BulkUserRowDto();
            dto.setFullName(getValue(cols, headerMap, "fullname", "name", "full_name", "employee_name"));
            dto.setEmail(getValue(cols, headerMap, "email", "emailaddress", "user_email"));
            dto.setDepartment(getValue(cols, headerMap, "department", "dept"));
            dto.setJobTitle(getValue(cols, headerMap, "jobtitle", "title", "role_title", "position"));
            dto.setPhoneNumber(getValue(cols, headerMap, "phone", "phonenumber", "telephone"));
            dto.setRole(getValue(cols, headerMap, "role", "userrole"));
            dto.setInitialPassword(getValue(cols, headerMap, "password", "initialpassword"));

            if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
                rows.add(dto);
            }
        }
        return rows;
    }

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------

    private void populateAssetFields(Asset asset, BulkAssetRowDto row, boolean autoCreateUsers) {
        asset.setName(clean(row.getName()));
        asset.setCategory(parseCategory(row.getCategory()));
        asset.setManufacturer(clean(row.getManufacturer()));
        asset.setModel(clean(row.getModel()));
        asset.setSerialNumber(clean(row.getSerialNumber()));

        String assigneeName = clean(row.getAssignedEmployeeName());
        String assigneeEmail = clean(row.getAssignedEmployeeEmail());
        String assignedDept = clean(row.getAssignedDepartment());

        asset.setStatus(parseStatus(row.getStatus(), assigneeEmail != null ? assigneeEmail : assigneeName));
        asset.setCondition(parseCondition(row.getCondition()));
        asset.setPurchaseCost(parseCost(row.getPurchaseCost()));
        asset.setPurchaseDate(parseDate(row.getPurchaseDate()));
        asset.setWarrantyExpiryDate(parseDate(row.getWarrantyExpiryDate()));
        asset.setLocation(clean(row.getLocation()));

        if (assigneeEmail != null || assigneeName != null) {
            asset.setAssignedEmployeeName(assigneeName != null ? assigneeName : assigneeEmail);
            asset.setAssignedEmployeeEmail(assigneeEmail != null ? assigneeEmail.toLowerCase() : null);
            asset.setAssignedDepartment(assignedDept);
            if (asset.getAssignedDate() == null) {
                asset.setAssignedDate(LocalDate.now());
            }

            if (autoCreateUsers && assigneeEmail != null && assigneeEmail.contains("@")) {
                ensureUserExists(assigneeEmail.toLowerCase(), assigneeName, assignedDept);
            }
        }

        if (clean(row.getSpecifications()) != null) {
            asset.setSpecifications(clean(row.getSpecifications()));
        }
        if (clean(row.getNotes()) != null) {
            asset.setNotes(clean(row.getNotes()));
        }
    }

    private void ensureUserExists(String email, String name, String dept) {
        if (!userRepository.existsByEmailIgnoreCase(email)) {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setFullName(name != null && !name.isEmpty() ? name : email.split("@")[0]);
            u.setDepartment(dept);
            u.setRole(UserRole.EMPLOYEE);
            u.setStatus(UserStatus.ACTIVE);
            String pwd = "Pass@" + (1000 + (int)(Math.random() * 9000));
            u.setPassword(authService.hashPassword(pwd));
            u.setTemporaryPassword(pwd);
            userRepository.save(u);
            log.info("Auto-registered employee profile for bulk imported asset: {}", email);
        }
    }

    private AssetCategory parseCategory(String raw) {
        if (raw == null || raw.trim().isEmpty()) return AssetCategory.LAPTOP;
        String val = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (AssetCategory cat : AssetCategory.values()) {
            if (cat.name().equalsIgnoreCase(val)) return cat;
        }
        if (val.contains("LAPTOP") || val.contains("NOTEBOOK") || val.contains("MACBOOK")) return AssetCategory.LAPTOP;
        if (val.contains("DESKTOP") || val.contains("PC") || val.contains("TOWER") || val.contains("WORKSTATION")) return AssetCategory.DESKTOP;
        if (val.contains("SERVER") || val.contains("RACK")) return AssetCategory.SERVER;
        if (val.contains("NET") || val.contains("SWITCH") || val.contains("ROUTER") || val.contains("FIREWALL")) return AssetCategory.NETWORKING;
        if (val.contains("MONITOR") || val.contains("SCREEN") || val.contains("DISPLAY")) return AssetCategory.MONITOR;
        if (val.contains("MOBILE") || val.contains("PHONE") || val.contains("IPAD") || val.contains("TABLET")) return AssetCategory.MOBILE_DEVICE;
        if (val.contains("PERIPHERAL")) return AssetCategory.PERIPHERAL;
        if (val.contains("ACCESSORY")) return AssetCategory.ACCESSORY;
        return AssetCategory.LAPTOP;
    }

    private AssetStatus parseStatus(String raw, String assignee) {
        if (raw != null && !raw.trim().isEmpty()) {
            String val = raw.trim().toUpperCase().replace("-", "_").replace(" ", "_");
            for (AssetStatus st : AssetStatus.values()) {
                if (st.name().equalsIgnoreCase(val)) return st;
            }
            if (val.contains("ASSIGN") || val.contains("DEPLOY")) return AssetStatus.ASSIGNED;
            if (val.contains("AVAIL") || val.contains("READY") || val.contains("STOCK")) return AssetStatus.AVAILABLE;
            if (val.contains("MAINT") || val.contains("REPAIR")) return AssetStatus.UNDER_MAINTENANCE;
            if (val.contains("RESER")) return AssetStatus.RESERVED;
            if (val.contains("RETIR") || val.contains("DECOMM")) return AssetStatus.RETIRED;
            if (val.contains("LOST") || val.contains("STOLEN")) return AssetStatus.LOST_OR_STOLEN;
        }
        return (assignee != null && !assignee.trim().isEmpty()) ? AssetStatus.ASSIGNED : AssetStatus.AVAILABLE;
    }

    private AssetCondition parseCondition(String raw) {
        if (raw == null || raw.trim().isEmpty()) return AssetCondition.GOOD;
        String val = raw.trim().toUpperCase();
        for (AssetCondition cond : AssetCondition.values()) {
            if (cond.name().equalsIgnoreCase(val)) return cond;
        }
        if (val.contains("NEW") || val.contains("MINT")) return AssetCondition.NEW;
        if (val.contains("EXCELL")) return AssetCondition.EXCELLENT;
        if (val.contains("GOOD")) return AssetCondition.GOOD;
        if (val.contains("FAIR") || val.contains("POOR") || val.contains("USED")) return AssetCondition.FAIR;
        if (val.contains("DAMAG") || val.contains("BROK")) return AssetCondition.DAMAGED;
        if (val.contains("DECOMM")) return AssetCondition.DECOMMISSIONED;
        return AssetCondition.GOOD;
    }

    private UserRole parseRole(String raw) {
        if (raw == null || raw.trim().isEmpty()) return UserRole.EMPLOYEE;
        String val = raw.trim().toUpperCase();
        if (val.contains("ADMIN")) return UserRole.ADMIN;
        return UserRole.EMPLOYEE;
    }

    private BigDecimal parseCost(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            String cleaned = raw.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return null;
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String cleanDate = raw.trim();
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("MM-dd-yyyy")
        );
        for (DateTimeFormatter fmt : formatters) {
            try {
                return LocalDate.parse(cleanDate, fmt);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String clean(String str) {
        if (str == null) return null;
        String t = str.trim();
        return t.isEmpty() ? null : t;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String getValue(List<String> cols, Map<String, Integer> headerMap, String... keys) {
        for (String k : keys) {
            Integer idx = headerMap.get(k);
            if (idx != null && idx < cols.size()) {
                String val = clean(cols.get(idx));
                if (val != null) return val;
            }
        }
        return null;
    }

    /**
     * Robust RFC-4180 CSV parser handling quoted tokens, commas, and newlines
     */
    public List<List<String>> parseCsvTable(InputStream is) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            StringBuilder currentField = new StringBuilder();
            List<String> currentRow = new ArrayList<>();
            boolean inQuotes = false;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Strip UTF-8 BOM if on the first line
                if (firstLine) {
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    firstLine = false;
                }

                char[] chars = line.toCharArray();
                for (int i = 0; i < chars.length; i++) {
                    char c = chars[i];
                    if (c == '\"') {
                        if (inQuotes && i + 1 < chars.length && chars[i + 1] == '\"') {
                            currentField.append('\"');
                            i++; // skip escaped quote
                        } else {
                            inQuotes = !inQuotes;
                        }
                    } else if (c == ',' && !inQuotes) {
                        currentRow.add(currentField.toString().trim());
                        currentField.setLength(0);
                    } else {
                        currentField.append(c);
                    }
                }

                if (!inQuotes) {
                    currentRow.add(currentField.toString().trim());
                    currentField.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                } else {
                    currentField.append("\n");
                }
            }
        }
        return rows;
    }
}
