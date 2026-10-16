package com.company.itam.controller;

import com.company.itam.model.SmtpSetting;
import com.company.itam.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/smtp")
@CrossOrigin(origins = "*")
public class SmtpController {

    private final EmailService emailService;

    public SmtpController(EmailService emailService) {
        this.emailService = emailService;
    }

    @GetMapping("/settings")
    public ResponseEntity<SmtpSetting> getSettings() {
        SmtpSetting setting = emailService.getSmtpSetting();
        // Clone and mask password for safety in response
        SmtpSetting safe = new SmtpSetting();
        safe.setId(setting.getId());
        safe.setHost(setting.getHost());
        safe.setPort(setting.getPort());
        safe.setUsername(setting.getUsername());
        safe.setPassword(setting.getPassword() != null && !setting.getPassword().isEmpty() ? "••••••••" : "");
        safe.setFromEmail(setting.getFromEmail());
        safe.setFromName(setting.getFromName());
        safe.setAuth(setting.isAuth());
        safe.setStartTls(setting.isStartTls());
        safe.setSsl(setting.isSsl());
        safe.setEnabled(setting.isEnabled());
        safe.setUpdatedAt(setting.getUpdatedAt());
        return ResponseEntity.ok(safe);
    }

    @PostMapping("/settings")
    public ResponseEntity<?> saveSettings(@RequestBody SmtpSetting updated) {
        SmtpSetting saved = emailService.saveSmtpSetting(updated);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("message", "SMTP Configuration updated successfully");
        resp.put("enabled", saved.isEnabled());
        resp.put("host", saved.getHost());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        SmtpSetting setting = emailService.getSmtpSetting();
        boolean sendingEnabled = emailService.isMailSendingEnabled();
        Map<String, Object> resp = new HashMap<>();
        resp.put("enabled", sendingEnabled);
        resp.put("host", setting.getHost());
        resp.put("port", setting.getPort());
        resp.put("username", setting.getUsername());
        resp.put("fromEmail", setting.getFromEmail());
        resp.put("fromName", setting.getFromName());
        resp.put("hasCredentials", setting.getUsername() != null && !setting.getUsername().isBlank()
                && setting.getPassword() != null && !setting.getPassword().isBlank());
        resp.put("updatedAt", setting.getUpdatedAt());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/toggle")
    public ResponseEntity<?> toggleSending(@RequestBody(required = false) Map<String, Object> request) {
        boolean newState;
        if (request != null && request.containsKey("enabled")) {
            Object val = request.get("enabled");
            boolean target = (val instanceof Boolean) ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
            newState = emailService.setMailSendingEnabled(target);
        } else {
            boolean current = emailService.isMailSendingEnabled();
            newState = emailService.setMailSendingEnabled(!current);
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", true);
        resp.put("enabled", newState);
        resp.put("message", newState ? "Outgoing email sending system is now ACTIVE (ON)" : "Outgoing email sending system is now PAUSED (OFF)");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/test")
    public ResponseEntity<?> sendTestEmail(@RequestBody Map<String, String> request) {
        String recipient = request.get("recipientEmail");
        if (recipient == null || recipient.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Recipient email is required"));
        }

        try {
            emailService.sendTestEmail(recipient.trim());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Test email successfully delivered to " + recipient
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "SMTP Delivery Failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            ));
        }
    }
}
