package com.company.itam.controller;

import com.company.itam.model.EmailNotification;
import com.company.itam.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/emails")
@Tag(name = "Email Outbox & Notifications", description = "Audit trail of sent onboarding credentials and system emails")
@CrossOrigin(origins = "*")
public class EmailController {

    private final EmailService emailService;

    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping
    @Operation(summary = "List all dispatched system emails in the outbox")
    public ResponseEntity<List<EmailNotification>> getAllEmails() {
        return ResponseEntity.ok(emailService.getAllNotifications());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed email content including HTML template and credentials")
    public ResponseEntity<EmailNotification> getEmailById(@PathVariable Long id) {
        EmailNotification notification = emailService.getNotificationById(id);
        if (notification == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(notification);
    }
}
