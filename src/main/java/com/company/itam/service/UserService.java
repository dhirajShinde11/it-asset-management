package com.company.itam.service;

import com.company.itam.dto.UserRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.exception.DuplicateResourceException;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.AppUser;
import com.company.itam.model.Asset;
import com.company.itam.model.AssetStatus;
import com.company.itam.model.UserRole;
import com.company.itam.model.UserStatus;
import com.company.itam.repository.AssetRepository;
import com.company.itam.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.itam.dto.UserDetailResponseDto;
import com.company.itam.model.EmailNotification;
import com.company.itam.model.MaintenanceRecord;
import com.company.itam.repository.EmailNotificationRepository;
import com.company.itam.repository.MaintenanceRecordRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final EmailService emailService;
    private final AuthService authService;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final EmailNotificationRepository emailRepository;
    private final com.company.itam.repository.AccessoryCheckoutRepository accessoryCheckoutRepository;
    private final com.company.itam.repository.ConsumableCheckoutRepository consumableCheckoutRepository;
    private final ActivityLogService activityLogService;
    private final com.company.itam.repository.ActivityLogRepository activityLogRepository;
    private final com.company.itam.repository.TicketRepository ticketRepository;
    private final SecureRandom random = new SecureRandom();

    public UserService(UserRepository userRepository,
                       AssetRepository assetRepository,
                       EmailService emailService,
                       AuthService authService,
                       MaintenanceRecordRepository maintenanceRepository,
                       EmailNotificationRepository emailRepository,
                       com.company.itam.repository.AccessoryCheckoutRepository accessoryCheckoutRepository,
                       com.company.itam.repository.ConsumableCheckoutRepository consumableCheckoutRepository,
                       ActivityLogService activityLogService,
                       com.company.itam.repository.ActivityLogRepository activityLogRepository,
                       com.company.itam.repository.TicketRepository ticketRepository) {
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.emailService = emailService;
        this.authService = authService;
        this.maintenanceRepository = maintenanceRepository;
        this.emailRepository = emailRepository;
        this.accessoryCheckoutRepository = accessoryCheckoutRepository;
        this.consumableCheckoutRepository = consumableCheckoutRepository;
        this.activityLogService = activityLogService;
        this.activityLogRepository = activityLogRepository;
        this.ticketRepository = ticketRepository;
    }

    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public UserDetailResponseDto getUserDetails(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        UserResponseDto userDto = mapToDto(user);
        List<Asset> assignedAssets = assetRepository.findByAssignedEmployeeEmailOrName(user.getEmail(), user.getFullName());

        BigDecimal totalValue = assignedAssets.stream()
                .map(Asset::getPurchaseCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MaintenanceRecord> serviceHistory = new ArrayList<>();
        for (Asset a : assignedAssets) {
            serviceHistory.addAll(maintenanceRepository.findByAssetIdOrderByServiceDateDesc(a.getId()));
        }

        List<EmailNotification> emails = emailRepository.findByRecipientEmailIgnoreCaseOrderBySentAtDesc(user.getEmail());
        List<com.company.itam.model.AccessoryCheckout> accessories = accessoryCheckoutRepository.findByUserEmailIgnoreCase(user.getEmail());
        List<com.company.itam.model.ConsumableCheckout> consumables = consumableCheckoutRepository.findByUserEmailIgnoreCase(user.getEmail());
        List<com.company.itam.model.ActivityLog> userLogs = activityLogRepository.findByTargetUserEmailIgnoreCaseOrderByTimestampDesc(user.getEmail());
        List<com.company.itam.model.Ticket> tickets = ticketRepository.findByRequesterEmailIgnoreCaseOrderByCreatedAtDesc(user.getEmail());

        UserDetailResponseDto detail = new UserDetailResponseDto();
        detail.setUser(userDto);
        detail.setAssignedAssets(assignedAssets);
        detail.setAssignedAccessories(accessories);
        detail.setReceivedConsumables(consumables);
        detail.setTotalAssetValue(totalValue);
        detail.setServiceHistory(serviceHistory);
        detail.setEmailHistory(emails);
        detail.setUserActivityHistory(userLogs);
        detail.setTickets(tickets);

        return detail;
    }

    public UserDetailResponseDto getUserDetailsByEmail(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return getUserDetails(user.getId());
    }

    public UserResponseDto getUserById(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return mapToDto(user);
    }

    public UserResponseDto getUserByEmail(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return mapToDto(user);
    }

    @Transactional
    public UserResponseDto createUser(UserRequestDto dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("An organization user with email '" + normalizedEmail + "' already exists");
        }

        // Determine password: use supplied or generate a clean secure temporary password
        String rawPassword = (dto.getInitialPassword() != null && !dto.getInitialPassword().isBlank())
                ? dto.getInitialPassword().trim()
                : generateSecurePassword();

        AppUser user = new AppUser();
        user.setFullName(dto.getFullName().trim());
        user.setEmail(normalizedEmail);
        user.setPassword(authService.hashPassword(rawPassword));
        user.setTemporaryPassword(rawPassword);
        user.setDepartment(dto.getDepartment());
        user.setJobTitle(dto.getJobTitle());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setRole(dto.getRole() != null ? dto.getRole() : UserRole.EMPLOYEE);
        user.setStatus(UserStatus.ACTIVE);

        AppUser savedUser = userRepository.save(user);
        log.info("Registered new user {} ({}) with role {}", savedUser.getFullName(), savedUser.getEmail(), savedUser.getRole());
        activityLogService.log(com.company.itam.model.ActivityAction.CREATED, com.company.itam.model.ItemType.USER,
                savedUser.getId(), null, savedUser.getFullName(), savedUser.getFullName(), savedUser.getEmail(), "IT Admin", "Added user account (" + savedUser.getRole() + ")");

        // Send welcome email with site link, ID, and temporary password
        if (dto.isSendWelcomeEmail()) {
            emailService.sendWelcomeEmail(savedUser, rawPassword, null);
        }

        return mapToDto(savedUser);
    }

    @Transactional
    public UserResponseDto updateUser(Long id, UserRequestDto dto) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        String newEmail = dto.getEmail().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(newEmail) && userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new DuplicateResourceException("Email '" + newEmail + "' is already in use by another user");
        }

        user.setFullName(dto.getFullName().trim());
        user.setEmail(newEmail);
        user.setDepartment(dto.getDepartment());
        user.setJobTitle(dto.getJobTitle());
        user.setPhoneNumber(dto.getPhoneNumber());
        if (dto.getRole() != null) {
            user.setRole(dto.getRole());
        }

        if (dto.getInitialPassword() != null && !dto.getInitialPassword().isBlank()) {
            user.setPassword(authService.hashPassword(dto.getInitialPassword().trim()));
            user.setTemporaryPassword(dto.getInitialPassword().trim());
        }

        AppUser updated = userRepository.save(user);
        activityLogService.log(com.company.itam.model.ActivityAction.UPDATED, com.company.itam.model.ItemType.USER,
                updated.getId(), null, updated.getFullName(), updated.getFullName(), updated.getEmail(), "IT Admin", "Updated user profile");
        return mapToDto(updated);
    }

    @Transactional
    public void deleteUser(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Free any assets assigned to this user
        List<Asset> assignedAssets = assetRepository.findByAssignedEmployeeEmailIgnoreCase(user.getEmail());
        for (Asset asset : assignedAssets) {
            asset.setAssignedEmployeeName(null);
            asset.setAssignedEmployeeEmail(null);
            asset.setAssignedDepartment(null);
            asset.setAssignedDate(null);
            asset.setStatus(AssetStatus.AVAILABLE);
            assetRepository.save(asset);
        }

        userRepository.delete(user);
        activityLogService.log(com.company.itam.model.ActivityAction.DELETED, com.company.itam.model.ItemType.USER,
                id, null, user.getFullName(), user.getFullName(), user.getEmail(), "IT Admin", "Deleted user from organization");
        log.info("Deleted user {} and unassigned {} assets", user.getEmail(), assignedAssets.size());
    }

    @Transactional
    public UserResponseDto resendWelcomeEmail(Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Generate a new temporary password
        String newPassword = generateSecurePassword();
        user.setPassword(authService.hashPassword(newPassword));
        user.setTemporaryPassword(newPassword);
        userRepository.save(user);

        emailService.sendWelcomeEmail(user, newPassword, null);
        return mapToDto(user);
    }

    private UserResponseDto mapToDto(AppUser user) {
        UserResponseDto dto = new UserResponseDto();
        dto.setId(user.getId());
        dto.setFullName(user.getFullName());
        dto.setEmail(user.getEmail());
        dto.setDepartment(user.getDepartment());
        dto.setJobTitle(user.getJobTitle());
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setTemporaryPassword(user.getTemporaryPassword());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());

        List<Asset> assets = assetRepository.findByAssignedEmployeeEmailOrName(user.getEmail(), user.getFullName());
        dto.setAssignedAssetsCount(assets.size());
        dto.setAssignedAssetTags(assets.stream()
                .map(a -> a.getAssetTag() + " (" + a.getName() + ")")
                .collect(Collectors.toList()));

        return dto;
    }

    private String generateSecurePassword() {
        int code = 1000 + random.nextInt(9000);
        return "Aegis#" + code;
    }
}
