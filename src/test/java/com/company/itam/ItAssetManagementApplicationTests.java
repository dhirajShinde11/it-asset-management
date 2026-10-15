package com.company.itam;

import com.company.itam.dto.AssetRequestDto;
import com.company.itam.dto.AuthResponseDto;
import com.company.itam.dto.LoginRequestDto;
import com.company.itam.dto.UserRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetCategory;
import com.company.itam.model.AssetCondition;
import com.company.itam.model.AssetStatus;
import com.company.itam.model.UserRole;
import com.company.itam.service.AssetService;
import com.company.itam.service.AuthService;
import com.company.itam.service.DashboardService;
import com.company.itam.service.EmailService;
import com.company.itam.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ItAssetManagementApplicationTests {

    @Autowired
    private AssetService assetService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailService emailService;

    @Test
    void contextLoads() {
        assertNotNull(assetService);
        assertNotNull(dashboardService);
        assertNotNull(userService);
        assertNotNull(authService);
        assertNotNull(emailService);
    }

    @Test
    void testPreloadedAssets() {
        List<Asset> assets = assetService.getAllAssets(null, null, null);
        assertNotNull(assets, "Asset list should be non-null");
    }

    @Test
    void testCreateAsset() {
        String testTag = "TEST-" + System.currentTimeMillis();
        AssetRequestDto dto = new AssetRequestDto();
        dto.setAssetTag(testTag);
        dto.setName("Unit Test Laptop");
        dto.setCategory(AssetCategory.LAPTOP);
        dto.setManufacturer("Framework");
        dto.setModel("Framework 16");
        dto.setSerialNumber("FW-" + testTag);
        dto.setStatus(AssetStatus.AVAILABLE);
        dto.setCondition(AssetCondition.NEW);
        dto.setPurchaseCost(new BigDecimal("1999.00"));
        dto.setPurchaseDate(LocalDate.now());
        dto.setWarrantyExpiryDate(LocalDate.now().plusYears(2));
        dto.setLocation("HQ Lab 3");

        Asset created = assetService.createAsset(dto);
        assertNotNull(created.getId());
        assertEquals(testTag, created.getAssetTag());

        // Fetch back
        Asset fetched = assetService.getAssetById(created.getId());
        assertEquals("Unit Test Laptop", fetched.getName());

        // Clean up test asset
        try {
            assetService.deleteAsset(created.getId());
        } catch (Exception ignored) {}
    }

    @Test
    void testUserOnboardingAndAuthentication() {
        String testEmail = "john.doe." + System.currentTimeMillis() + "@company.com";
        // 1. Admin adds user with organization email
        UserRequestDto userDto = new UserRequestDto();
        userDto.setFullName("John Doe");
        userDto.setEmail(testEmail);
        userDto.setDepartment("Cybersecurity");
        userDto.setJobTitle("Security Engineer");
        userDto.setRole(UserRole.EMPLOYEE);
        userDto.setSendWelcomeEmail(true);

        UserResponseDto createdUser = userService.createUser(userDto);
        assertNotNull(createdUser.getId());
        assertEquals(testEmail, createdUser.getEmail());
        assertNotNull(createdUser.getTemporaryPassword());

        // 2. User logs in with received temporary password
        LoginRequestDto loginDto = new LoginRequestDto();
        loginDto.setEmail(testEmail);
        loginDto.setPassword(createdUser.getTemporaryPassword());

        AuthResponseDto auth = authService.login(loginDto);
        assertTrue(auth.isSuccess());
        assertEquals("John Doe", auth.getFullName());
        assertEquals(UserRole.EMPLOYEE, auth.getRole());

        // 3. Verify email notification in outbox
        var emails = emailService.getAllNotifications();
        boolean foundWelcome = emails.stream()
                .anyMatch(e -> e.getRecipientEmail().equalsIgnoreCase(testEmail));
        assertTrue(foundWelcome, "Outbox should record welcome email dispatch");

        // Clean up test user
        try {
            userService.deleteUser(createdUser.getId());
        } catch (Exception ignored) {}
    }

    @Test
    void testDashboardMetrics() {
        var summary = dashboardService.getSummary();
        assertNotNull(summary);
        assertTrue(summary.getTotalAssets() >= 0);
        assertNotNull(summary.getTotalAssetValue());
        assertTrue(summary.getTotalAssetValue().compareTo(BigDecimal.ZERO) >= 0);
    }
}
