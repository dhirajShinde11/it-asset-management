package com.company.itam.controller;

import com.company.itam.dto.UserDetailResponseDto;
import com.company.itam.dto.UserRequestDto;
import com.company.itam.dto.UserResponseDto;
import com.company.itam.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User Management", description = "Admin user directory, onboarding, and invitations")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List all organization users with assigned asset counts")
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user summary by ID")
    public ResponseEntity<UserResponseDto> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping("/{id}/details")
    @Operation(summary = "Get full user profile including assigned hardware, accessories, tickets, and email log")
    public ResponseEntity<UserDetailResponseDto> getUserDetails(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserDetails(id));
    }

    @GetMapping("/detail/{email}")
    @Operation(summary = "Get full user profile by employee email")
    public ResponseEntity<UserDetailResponseDto> getUserDetailsByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userService.getUserDetailsByEmail(email));
    }

    @PostMapping
    @Operation(summary = "Register/Onboard a new organization user and trigger welcome email")
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserRequestDto dto) {
        UserResponseDto created = userService.createUser(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing user")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDto dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an organization user and release their assigned assets")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully and assigned assets released to stock."));
    }

    @PostMapping("/{id}/resend-welcome")
    @Operation(summary = "Regenerate credentials and resend welcome onboarding email")
    public ResponseEntity<UserResponseDto> resendWelcomeEmail(@PathVariable Long id) {
        return ResponseEntity.ok(userService.resendWelcomeEmail(id));
    }
}
