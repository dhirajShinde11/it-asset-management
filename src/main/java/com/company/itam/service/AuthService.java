package com.company.itam.service;

import com.company.itam.dto.AuthResponseDto;
import com.company.itam.dto.ChangePasswordDto;
import com.company.itam.dto.LoginRequestDto;
import com.company.itam.exception.ResourceNotFoundException;
import com.company.itam.model.AppUser;
import com.company.itam.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int ITERATIONS = 12000;
    private static final int KEY_LENGTH = 256;
    private static final String PBKDF2_PREFIX = "pbkdf2$";

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
    }

    public AuthResponseDto login(LoginRequestDto request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return new AuthResponseDto(false, "Email and password are required");
        }

        String email = request.getEmail().trim().toLowerCase();
        AppUser user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            log.warn("Login failed: User not found for email {}", email);
            return new AuthResponseDto(false, "Invalid email or password");
        }

        // Verify password
        if (!verifyPassword(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: Incorrect password for email {}", email);
            return new AuthResponseDto(false, "Invalid email or password");
        }

        // Automatic password migration: If stored password was legacy SHA-256 or plaintext, upgrade to salted PBKDF2
        if (user.getPassword() != null && !user.getPassword().startsWith(PBKDF2_PREFIX)) {
            user.setPassword(hashPassword(request.getPassword()));
            log.info("Automatically upgraded password hash to salted PBKDF2 for user {}", user.getEmail());
        }

        // Record last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate verified server-side session token
        String sessionToken = tokenService.generateToken(user);

        AuthResponseDto response = new AuthResponseDto(true, "Authentication successful");
        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setDepartment(user.getDepartment());
        response.setJobTitle(user.getJobTitle());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken(sessionToken);

        log.info("User {} logged in successfully with role {}", user.getEmail(), user.getRole());
        return response;
    }

    public void logout(String token) {
        tokenService.revokeToken(token);
    }

    public boolean changePassword(Long userId, String email, ChangePasswordDto dto) {
        AppUser user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && email != null && !email.isBlank()) {
            user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase()).orElse(null);
        }
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }

        if (!verifyPassword(dto.getCurrentPassword(), user.getPassword())) {
            return false;
        }

        user.setPassword(hashPassword(dto.getNewPassword()));
        user.setTemporaryPassword(null); // Clear temporary password once changed
        userRepository.save(user);
        log.info("Password successfully changed to PBKDF2 salted hash for user {}", user.getEmail());
        return true;
    }

    /**
     * Hashes password using Salted PBKDF2 with HMAC-SHA256 (12,000 iterations).
     * Output format: pbkdf2$<base64Salt>$<base64Hash>
     */
    public String hashPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
        try {
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            KeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return PBKDF2_PREFIX + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password with PBKDF2", e);
        }
    }

    /**
     * Verifies raw password against stored password.
     * Supports PBKDF2 salted hashes, with backward compatibility for legacy unsalted SHA-256 or initial seeded accounts.
     */
    public boolean verifyPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null || rawPassword == null) {
            return false;
        }

        // 1. Salted PBKDF2 Verification
        if (storedPassword.startsWith(PBKDF2_PREFIX)) {
            try {
                String[] parts = storedPassword.split("\\$");
                if (parts.length != 3) {
                    return false;
                }
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expectedHash = Base64.getDecoder().decode(parts[2]);

                KeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
                SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] actualHash = factory.generateSecret(spec).getEncoded();

                return MessageDigest.isEqual(expectedHash, actualHash);
            } catch (Exception e) {
                log.error("Error verifying PBKDF2 password: {}", e.getMessage());
                return false;
            }
        }

        // 2. Backward compatibility: Legacy unsalted SHA-256 verification
        String legacyHash = hashLegacySha256(rawPassword);
        if (MessageDigest.isEqual(legacyHash.getBytes(StandardCharsets.UTF_8), storedPassword.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }

        // 3. Backward compatibility: Exact match for initial seeded plaintext passwords
        return rawPassword.equals(storedPassword);
    }

    private String hashLegacySha256(String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error with SHA-256", e);
        }
    }
}
