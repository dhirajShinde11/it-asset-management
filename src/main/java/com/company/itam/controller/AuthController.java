package com.company.itam.controller;

import com.company.itam.dto.AuthResponseDto;
import com.company.itam.dto.ChangePasswordDto;
import com.company.itam.dto.LoginRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.service.AuthService;
import com.company.itam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping("/me")
    @Operation(summary = "Get user profile by email")
    public ResponseEntity<UserResponseDto> getProfile(@RequestParam String email) {
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
