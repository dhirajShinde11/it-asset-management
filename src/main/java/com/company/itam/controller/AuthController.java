package com.company.itam.controller;

import com.company.itam.dto.AuthResponseDto;
import com.company.itam.dto.ChangePasswordDto;
import com.company.itam.dto.LoginRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.service.AuthService;
import com.company.itam.service.TokenService;
import com.company.itam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User authentication and profile access")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user with organization email and password")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        if (!response.isSuccess()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate active session token")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            token = request.getHeader("X-Auth-Token");
        }
        if (token != null) {
            authService.logout(token);
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Logged out successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get user profile by email")
    public ResponseEntity<?> getProfile(@RequestParam String email, HttpServletRequest request) {
        Object userObj = request.getAttribute("currentUser");
        if (userObj instanceof TokenService.UserSession session) {
            if (!session.isAdmin() && !session.email().equalsIgnoreCase(email.trim())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Cannot access other profiles.");
            }
        }
        UserResponseDto user = userService.getUserByEmail(email);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/change-password")
    @Operation(summary = "Update account password")
    public ResponseEntity<?> changePassword(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @Valid @RequestBody ChangePasswordDto dto) {
        boolean success = authService.changePassword(userId, email, dto);
        if (!success) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Current password does not match."));
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
    }
}
