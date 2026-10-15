package com.company.itam.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_notifications")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EmailNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipientEmail;

    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String emailType;

    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private String temporaryPassword;

    private String portalUrl;

    @Column(nullable = false)
    private String status = "DELIVERED_TO_OUTBOX";

    private LocalDateTime sentAt;

    public EmailNotification() {
        this.sentAt = LocalDateTime.now();
    }

    public EmailNotification(String recipientEmail, String recipientName, String subject,
                             String emailType, String content, String temporaryPassword, String portalUrl) {
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
        this.subject = subject;
        this.emailType = emailType;
        this.content = content;
        this.temporaryPassword = temporaryPassword;
        this.portalUrl = portalUrl;
        this.status = "DELIVERED_TO_OUTBOX";
        this.sentAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEmailType() {
        return emailType;
    }

    public void setEmailType(String emailType) {
        this.emailType = emailType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTemporaryPassword() {
        return temporaryPassword;
    }

    public void setTemporaryPassword(String temporaryPassword) {
        this.temporaryPassword = temporaryPassword;
    }

    public String getPortalUrl() {
        return portalUrl;
    }

    public void setPortalUrl(String portalUrl) {
        this.portalUrl = portalUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
