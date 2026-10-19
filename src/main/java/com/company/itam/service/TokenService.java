package com.company.itam.service;

import com.company.itam.model.AppUser;
import com.company.itam.model.UserRole;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    public record UserSession(
            Long userId,
            String email,
            String fullName,
            UserRole role,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        public boolean isAdmin() {
            return UserRole.ADMIN.equals(role);
        }
    }

    private final Map<String, UserSession> activeTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    // Session duration: 24 hours
    private static final long SESSION_HOURS = 24;

    public String generateToken(AppUser user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = "tok_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        UserSession session = new UserSession(
                user.getId(),
                user.getEmail().trim().toLowerCase(),
                user.getFullName(),
                user.getRole(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(SESSION_HOURS)
        );

        activeTokens.put(token, session);
        return token;
    }

    public UserSession validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // Clean "Bearer " prefix if passed in Authorization header
        String cleanToken = token.trim();
        if (cleanToken.startsWith("Bearer ") || cleanToken.startsWith("bearer ")) {
            cleanToken = cleanToken.substring(7).trim();
        }

        UserSession session = activeTokens.get(cleanToken);
        if (session == null) {
            return null;
        }

        if (session.isExpired()) {
            activeTokens.remove(cleanToken);
            return null;
        }

        return session;
    }

    public void revokeToken(String token) {
        if (token != null) {
            String cleanToken = token.trim();
            if (cleanToken.startsWith("Bearer ") || cleanToken.startsWith("bearer ")) {
                cleanToken = cleanToken.substring(7).trim();
            }
            activeTokens.remove(cleanToken);
        }
    }
}
