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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthResponseDto login(LoginRequestDto request) {
        if (request.getEmail() == null || request.getPassword() == null) {
            return new AuthResponseDto(false, "Email and password are required");
        }

        String email = request.getEmail().trim().toLowerCase();
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (user == null) {
            log.warn("Login failed: User not found for email {}", email);
            return new AuthResponseDto(false, "Invalid email or password");
        }

        // Check password (matches either raw or hashed)
        if (!verifyPassword(request.getPassword(), user.getPassword())) {
            log.warn("Login failed: Incorrect password for email {}", email);
            return new AuthResponseDto(false, "Invalid email or password");
        }

        // Record last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        AuthResponseDto response = new AuthResponseDto(true, "Authentication successful");
        response.setId(user.getId());
        response.setFullName(user.getFullName());
        response.setEmail(user.getEmail());
        response.setDepartment(user.getDepartment());
        response.setJobTitle(user.getJobTitle());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken("tok_" + UUID.randomUUID().toString().replace("-", ""));

        log.info("User {} logged in successfully with role {}", user.getEmail(), user.getRole());
        return response;
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
        log.info("Password successfully changed for user {}", user.getEmail());
        return true;
    }

    public String hashPassword(String rawPassword) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    public boolean verifyPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null || rawPassword == null) {
            return false;
        }
        // Support direct match (for initial seeded passwords) or hashed match
        if (rawPassword.equals(storedPassword)) {
            return true;
        }
        return hashPassword(rawPassword).equals(storedPassword);
    }
}
