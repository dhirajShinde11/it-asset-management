package com.company.itam.service;

import com.company.itam.model.AppUser;
import com.company.itam.model.Asset;
import com.company.itam.model.EmailNotification;
import com.company.itam.model.SmtpSetting;
import com.company.itam.model.Ticket;
import com.company.itam.model.TicketPriority;
import com.company.itam.model.TicketStatus;
import com.company.itam.repository.EmailNotificationRepository;
import com.company.itam.repository.SmtpSettingRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailNotificationRepository emailRepository;
    private final SmtpSettingRepository smtpSettingRepository;

    @Value("${server.port:8085}")
    private String serverPort;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String propHost;

    @Value("${spring.mail.port:587}")
    private int propPort;

    @Value("${spring.mail.username:}")
    private String propUsername;

    @Value("${spring.mail.password:}")
    private String propPassword;

    @Value("${itam.mail.from-email:}")
    private String propFromEmail;

    @Value("${itam.mail.from-name:BMC AssetHub}")
    private String propFromName;

    @Value("${itam.mail.enabled:true}")
    private boolean propMailEnabled;

    @Value("${itam.server.host:}")
    private String propServerHost;

    public String getBasePortalUrl() {
        if (propServerHost != null && !propServerHost.isBlank()) {
            return "http://" + propServerHost.trim() + ":" + serverPort;
        }
        return "http://localhost:" + serverPort;
    }

    public EmailService(EmailNotificationRepository emailRepository, SmtpSettingRepository smtpSettingRepository) {
        this.emailRepository = emailRepository;
        this.smtpSettingRepository = smtpSettingRepository;
    }

    public EmailNotification sendWelcomeEmail(AppUser user, String rawPassword, String customPortalUrl) {
        String basePortalUrl = customPortalUrl != null && !customPortalUrl.isBlank()
                ? customPortalUrl
                : "http://localhost:" + serverPort;

        String loginUrl = basePortalUrl + "/?email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "Welcome to BMC AssetHub - Your Organization Account & Equipment Access";
        String htmlContent = buildWelcomeEmailHtml(user, rawPassword, loginUrl);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(user.getEmail());
        notification.setRecipientName(user.getFullName());
        notification.setSubject(subject);
        notification.setEmailType("WELCOME_ONBOARDING");
        notification.setContent(htmlContent);
        notification.setTemporaryPassword(rawPassword);
        notification.setPortalUrl(loginUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        log.info("Recorded Welcome Email for {} in outbox (ID: {})", user.getEmail(), saved.getId());

        // Attempt physical SMTP transmission
        dispatchViaSmtp(saved);

        return saved;
    }

    public EmailNotification seedWelcomeEmail(AppUser user, String rawPassword) {
        String loginUrl = "http://localhost:" + serverPort + "/?email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "Welcome to BMC AssetHub - Your Organization Account & Equipment Access";
        String htmlContent = buildWelcomeEmailHtml(user, rawPassword, loginUrl);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(user.getEmail());
        notification.setRecipientName(user.getFullName());
        notification.setSubject(subject);
        notification.setEmailType("WELCOME_ONBOARDING");
        notification.setContent(htmlContent);
        notification.setTemporaryPassword(rawPassword);
        notification.setPortalUrl(loginUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now().minusHours(3));

        EmailNotification saved = emailRepository.save(notification);
        log.info("Seeded initial outbox welcome email for {} (ID: {})", user.getEmail(), saved.getId());
        return saved;
    }

    public EmailNotification sendAssetAssignedEmail(AppUser user, Asset asset) {
        String portalUrl = "http://localhost:" + serverPort + "/?email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "IT Equipment Assigned: " + asset.getName() + " (" + asset.getAssetTag() + ")";
        String htmlContent = buildAssetAssignedEmailHtml(user, asset, portalUrl);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(user.getEmail());
        notification.setRecipientName(user.getFullName());
        notification.setSubject(subject);
        notification.setEmailType("ASSET_ASSIGNED");
        notification.setContent(htmlContent);
        notification.setTemporaryPassword(null);
        notification.setPortalUrl(portalUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        log.info("Recorded Asset Assigned Email for asset {} to {} in outbox", asset.getAssetTag(), user.getEmail());

        // Attempt physical SMTP transmission
        dispatchViaSmtp(saved);

        return saved;
    }

    public boolean dispatchViaSmtp(EmailNotification notification) {
        if (notification == null || notification.getRecipientEmail() == null) {
            return false;
        }

        // Safety Guard 1: Never dispatch real SMTP traffic to dummy, demo, or placeholder addresses
        if (isDemoOrPlaceholderEmail(notification.getRecipientEmail())) {
            log.info("Skipping real SMTP dispatch for demo/placeholder address: {}", notification.getRecipientEmail());
            notification.setStatus("DEMO_SKIPPED");
            emailRepository.save(notification);
            return false;
        }

        // Safety Guard 2: Main email sending system ON/OFF master toggle
        if (!isMailSendingEnabled()) {
            log.info("Main email sending system is OFF (Paused). Notification {} retained in Outbox without SMTP transmission.", notification.getId());
            notification.setStatus("OUTBOX_PAUSED");
            emailRepository.save(notification);
            return false;
        }

        // Configuration Resolution: Check dynamic database settings first, then application.properties
        SmtpSetting setting = getSmtpSetting();
        String host = setting.getHost();
        int port = setting.getPort() != null ? setting.getPort() : 587;
        String username = setting.getUsername();
        String password = setting.getPassword();
        String fromEmail = setting.getFromEmail();
        String fromName = setting.getFromName();

        // Fallback to application.properties if database username is blank
        if ((username == null || username.isBlank()) && (propUsername != null && !propUsername.isBlank() && !propUsername.contains("YOUR_EMAIL_HERE"))) {
            host = propHost;
            port = propPort;
            username = propUsername;
            password = propPassword;
            if (fromEmail == null || fromEmail.isBlank()) fromEmail = propFromEmail;
            if (fromName == null || fromName.isBlank()) fromName = propFromName;
        }

        // If credentials are not yet configured
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.info("Main email sending is ON, but SMTP credentials are incomplete. Email {} kept in Outbox.", notification.getId());
            notification.setStatus("CONFIG_REQUIRED");
            emailRepository.save(notification);
            return false;
        }

        final String fHost = host;
        final int fPort = port;
        final String fUsername = username;
        final String fPassword = password;
        final String fFromEmail = fromEmail;
        final String fFromName = fromName;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                dispatchUsingConfig(notification, fHost, fPort, fUsername, fPassword, fFromEmail, fFromName);
            } catch (Exception e) {
                log.error("Asynchronous SMTP delivery failure for {}: {}", notification.getRecipientEmail(), e.getMessage());
            }
        });
        return true;
    }

    private boolean isDemoOrPlaceholderEmail(String email) {
        if (email == null || email.isBlank()) return true;
        String clean = email.toLowerCase().trim();
        return clean.endsWith("@example.com")
                || clean.endsWith("@test.com")
                || clean.endsWith("@company.com")
                || clean.endsWith("@dummy.com")
                || clean.endsWith("@sample.com")
                || clean.endsWith("@placeholder.com")
                || clean.startsWith("test@")
                || clean.startsWith("demo@");
    }

    private boolean dispatchUsingConfig(EmailNotification notification, String host, int port, String username, String password, String fromEmail, String fromName) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(port);
            if (username != null && !username.isBlank()) {
                mailSender.setUsername(username);
                mailSender.setPassword(password);
            }
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String fromAddr = (fromEmail != null && !fromEmail.isBlank() && !fromEmail.contains("YOUR_EMAIL_HERE"))
                    ? fromEmail
                    : (username != null && !username.isBlank() ? username : "no-reply@company.com");
            String personal = (fromName != null && !fromName.isBlank()) ? fromName : "BMC AssetHub";

            helper.setFrom(fromAddr, personal);
            helper.setTo(notification.getRecipientEmail());
            helper.setSubject(notification.getSubject());
            helper.setText(notification.getContent(), true);

            mailSender.send(mimeMessage);

            notification.setStatus("DELIVERED_TO_INBOX");
            emailRepository.save(notification);
            log.info("Successfully delivered physical email via SMTP to {}", notification.getRecipientEmail());
            return true;
        } catch (Exception ex) {
            String errorMsg = "SMTP Error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            log.error("Failed to physically send email via SMTP to {}: {}", notification.getRecipientEmail(), errorMsg);
            if (errorMsg.length() > 250) {
                errorMsg = errorMsg.substring(0, 250);
            }
            notification.setStatus(errorMsg);
            emailRepository.save(notification);
            return false;
        }
    }

    public void sendTestEmail(String recipientEmail) throws Exception {
        String host = propHost;
        int port = propPort;
        String username = propUsername;
        String password = propPassword;
        String fromEmail = propFromEmail;
        String fromName = propFromName;

        if (username == null || username.isBlank() || username.contains("YOUR_EMAIL_HERE")) {
            Optional<SmtpSetting> settingOpt = smtpSettingRepository.findFirstByOrderByIdAsc();
            if (settingOpt.isPresent()) {
                SmtpSetting s = settingOpt.get();
                host = s.getHost();
                port = s.getPort();
                username = s.getUsername();
                password = s.getPassword();
                fromEmail = s.getFromEmail();
                fromName = s.getFromName();
            }
        }

        if (username == null || username.isBlank() || username.contains("YOUR_EMAIL_HERE")) {
            throw new IllegalStateException("SMTP username/password is not configured in application.properties.");
        }

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        String fromAddr = (fromEmail != null && !fromEmail.isBlank() && !fromEmail.contains("YOUR_EMAIL_HERE"))
                ? fromEmail
                : username;
        String personal = (fromName != null && !fromName.isBlank()) ? fromName : "BMC AssetHub";

        helper.setFrom(fromAddr, personal);
        helper.setTo(recipientEmail);
        helper.setSubject("Test Email: BMC AssetHub SMTP Connectivity Verified");

        String testHtml = "<div style='font-family:sans-serif; background:#0f172a; color:#e2e8f0; padding:24px; border-radius:8px;'>"
                + "<h2 style='color:#38bdf8;'>SMTP Connectivity Verified!</h2>"
                + "<p>This is a test notification from your <strong>BMC AssetHub</strong> system.</p>"
                + "<p>Your hardcoded project SMTP server (<code>" + escapeHtml(host) + ":" + port + "</code>) is connected and operating properly.</p>"
                + "<p style='color:#94a3b8; font-size:12px;'>Timestamp: " + LocalDateTime.now() + "</p>"
                + "</div>";

        helper.setText(testHtml, true);
        mailSender.send(mimeMessage);
    }

    private JavaMailSenderImpl createMailSender(SmtpSetting setting) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(setting.getHost());
        mailSender.setPort(setting.getPort() != null ? setting.getPort() : 587);

        if (setting.getUsername() != null && !setting.getUsername().isBlank()) {
            mailSender.setUsername(setting.getUsername());
            mailSender.setPassword(setting.getPassword());
        }

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(setting.isAuth()));

        if (setting.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(setting.getPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", String.valueOf(setting.isStartTls()));
            props.put("mail.smtp.starttls.required", String.valueOf(setting.isStartTls()));
        }

        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        return mailSender;
    }

    public List<EmailNotification> getAllNotifications() {
        return emailRepository.findAllByOrderBySentAtDesc();
    }

    public EmailNotification getNotificationById(Long id) {
        return emailRepository.findById(id).orElse(null);
    }

    public boolean deleteNotification(Long id) {
        if (emailRepository.existsById(id)) {
            emailRepository.deleteById(id);
            log.info("Deleted email notification #{} from outbox", id);
            return true;
        }
        return false;
    }

    public void clearAllNotifications() {
        long count = emailRepository.count();
        emailRepository.deleteAll();
        log.info("Cleared all {} email notification record(s) from outbox", count);
    }

    public SmtpSetting getSmtpSetting() {
        return smtpSettingRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            SmtpSetting s = new SmtpSetting();
            return smtpSettingRepository.save(s);
        });
    }

    public SmtpSetting saveSmtpSetting(SmtpSetting updated) {
        SmtpSetting existing = getSmtpSetting();
        existing.setHost(updated.getHost());
        existing.setPort(updated.getPort());
        existing.setUsername(updated.getUsername());
        if (updated.getPassword() != null && !updated.getPassword().isBlank() && !updated.getPassword().equals("••••••••")) {
            existing.setPassword(updated.getPassword());
        }
        existing.setFromEmail(updated.getFromEmail());
        existing.setFromName(updated.getFromName());
        existing.setAuth(updated.isAuth());
        existing.setStartTls(updated.isStartTls());
        existing.setSsl(updated.isSsl());
        existing.setEnabled(updated.isEnabled());
        existing.setUpdatedAt(LocalDateTime.now());
        return smtpSettingRepository.save(existing);
    }

    public boolean isMailSendingEnabled() {
        try {
            return getSmtpSetting().isEnabled();
        } catch (Exception e) {
            log.warn("Could not retrieve SMTP enabled state, defaulting to false: {}", e.getMessage());
            return false;
        }
    }

    public boolean setMailSendingEnabled(boolean enabled) {
        SmtpSetting existing = getSmtpSetting();
        existing.setEnabled(enabled);
        existing.setUpdatedAt(LocalDateTime.now());
        SmtpSetting saved = smtpSettingRepository.save(existing);
        log.info("Main email sending system switched to: {}", saved.isEnabled() ? "ON (ACTIVE)" : "OFF (PAUSED)");
        return saved.isEnabled();
    }

    private String buildWelcomeEmailHtml(AppUser user, String password, String portalUrl) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 28px 16px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='750' border='0' cellspacing='0' cellpadding='0' style='max-width: 750px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06);'>"
                + "        <!-- Horizontal Header -->"
                + "        <tr><td style='padding: 18px 28px; background-color: #0f172a; border-bottom: 2px solid #2563eb;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <span style='font-size: 16px; font-weight: 700; color: #ffffff;'>BMC AssetHub</span>"
                + "                <span style='font-size: 13px; color: #94a3b8; margin-left: 10px;'>| &nbsp; User Provisioning</span>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-size: 11px; font-weight: 700; color: #38bdf8; background-color: rgba(56, 189, 248, 0.12); border: 1px solid rgba(56, 189, 248, 0.25); padding: 3px 10px; border-radius: 4px; letter-spacing: 0.5px;'>ACCOUNT ACTIVATED</span>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Body -->"
                + "        <tr><td style='padding: 24px 28px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'>Hello <strong>" + escapeHtml(user.getFullName()) + "</strong>,</p>"
                + "          <p style='margin: 0 0 20px 0; font-size: 14px; color: #475569; line-height: 1.5;'>Your organizational account has been created. You now have access to the IT Asset Management self-service portal to view assigned hardware, review software licenses, and raise IT support requests.</p>"
                + "          <!-- Horizontal 3-Column Credential Grid -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 22px; background-color: #f8fafc; border-collapse: collapse;'>"
                + "            <tr>"
                + "              <td width='38%' style='padding: 14px 18px; border-right: 1px solid #e2e8f0; vertical-align: top;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Username / Email</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px; font-family: monospace; word-break: break-all;'>" + escapeHtml(user.getEmail()) + "</div>"
                + "              </td>"
                + "              <td width='32%' style='padding: 14px 18px; border-right: 1px solid #e2e8f0; vertical-align: top;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Temporary Password</div>"
                + "                <div style='font-size: 14px; font-weight: 700; color: #2563eb; margin-top: 4px; font-family: monospace; letter-spacing: 0.5px;'>" + escapeHtml(password) + "</div>"
                + "              </td>"
                + "              <td width='30%' style='padding: 14px 18px; vertical-align: top;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Department / Role</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(user.getDepartment() != null ? user.getDepartment() : "General") + " (" + (user.getRole() != null ? user.getRole().name() : "EMPLOYEE") + ")</div>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "          <!-- Horizontal Action Toolbar -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 13px; font-weight: 600; border-radius: 5px;'>Log In to IT Portal &rarr;</a>"
                + "              </td>"
                + "              <td align='right' style='color: #64748b; font-size: 12px;'>"
                + "                Please change your password upon your first successful login."
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Footer -->"
                + "        <tr><td style='padding: 14px 28px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>BMC AssetHub &bull; BM Consulting IT Operations</td>"
                + "              <td align='right'>Internal Corporate Services</td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    private String buildAssetAssignedEmailHtml(AppUser user, Asset asset, String portalUrl) {
        String mfgModel = (asset.getManufacturer() != null ? asset.getManufacturer() : "")
                + ((asset.getModel() != null && !asset.getModel().isBlank()) ? " " + asset.getModel() : "");
        if (mfgModel.isBlank()) mfgModel = "Standard Configuration";

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 28px 16px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='750' border='0' cellspacing='0' cellpadding='0' style='max-width: 750px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06);'>"
                + "        <!-- Horizontal Header -->"
                + "        <tr><td style='padding: 18px 28px; background-color: #0f172a; border-bottom: 2px solid #2563eb;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <span style='font-size: 16px; font-weight: 700; color: #ffffff;'>BMC AssetHub</span>"
                + "                <span style='font-size: 13px; color: #94a3b8; margin-left: 10px;'>| &nbsp; Equipment Custody</span>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-size: 12px; font-family: monospace; font-weight: 700; color: #60a5fa; background-color: rgba(96, 165, 250, 0.12); border: 1px solid rgba(96, 165, 250, 0.3); padding: 3px 10px; border-radius: 4px;'>"
                + "                  TAG: " + escapeHtml(asset.getAssetTag()) + "</span>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Body -->"
                + "        <tr><td style='padding: 24px 28px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'>Hello <strong>" + escapeHtml(user.getFullName()) + "</strong>,</p>"
                + "          <p style='margin: 0 0 20px 0; font-size: 14px; color: #475569; line-height: 1.5;'>A hardware device has been checked out and registered under your custody. Review the hardware details assigned to your profile below:</p>"
                + "          <!-- Horizontal 4-Column Asset Grid -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 20px; background-color: #f8fafc; border-collapse: collapse;'>"
                + "            <tr>"
                + "              <td width='25%' style='padding: 12px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Asset Tag</div>"
                + "                <div style='font-size: 13px; font-weight: 700; color: #2563eb; margin-top: 4px; font-family: monospace;'>" + escapeHtml(asset.getAssetTag()) + "</div>"
                + "              </td>"
                + "              <td width='35%' style='padding: 12px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Device & Model</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(asset.getName()) + " (" + escapeHtml(mfgModel) + ")</div>"
                + "              </td>"
                + "              <td width='22%' style='padding: 12px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Serial Number</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px; font-family: monospace;'>" + escapeHtml(asset.getSerialNumber() != null ? asset.getSerialNumber() : "N/A") + "</div>"
                + "              </td>"
                + "              <td width='18%' style='padding: 12px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Condition</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + asset.getCondition() + "</div>"
                + "              </td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td colspan='4' style='padding: 11px 16px; background-color: #ffffff; border: 1px solid #e2e8f0;'>"
                + "                <span style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Specifications: </span>"
                + "                <span style='font-size: 13px; color: #334155; margin-left: 6px;'>" + escapeHtml(asset.getSpecifications() != null && !asset.getSpecifications().isBlank() ? asset.getSpecifications() : "Standard configuration") + "</span>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "          <!-- Horizontal Action & Info Bar -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 13px; font-weight: 600; border-radius: 5px;'>View Equipment in Portal &rarr;</a>"
                + "              </td>"
                + "              <td align='right' style='color: #64748b; font-size: 12px;'>"
                + "                Need hardware support? You can raise an IT ticket directly from your portal."
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Footer -->"
                + "        <tr><td style='padding: 14px 28px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>BMC AssetHub &bull; BM Consulting IT Operations</td>"
                + "              <td align='right'>Hardware Custody Tracking</td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    public EmailNotification sendTicketCreatedNotification(Ticket ticket) {
        String portalUrl = getBasePortalUrl() + "/?email=" + java.net.URLEncoder.encode(ticket.getRequesterEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "[IT Helpdesk] Ticket Created: [" + ticket.getTicketNumber() + "] " + ticket.getTitle();
        String htmlContent = buildTicketEmailHtml(ticket, portalUrl, "Ticket Received",
                "Your IT support ticket has been registered. An IT technician will review and action your request.");

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(ticket.getRequesterEmail());
        notification.setRecipientName(ticket.getRequesterName());
        notification.setSubject(subject);
        notification.setEmailType("TICKET_CREATED");
        notification.setContent(htmlContent);
        notification.setPortalUrl(portalUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        dispatchViaSmtp(saved);
        return saved;
    }

    public EmailNotification sendTicketUpdatedNotification(Ticket ticket) {
        String portalUrl = getBasePortalUrl() + "/?email=" + java.net.URLEncoder.encode(ticket.getRequesterEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "[IT Helpdesk] Ticket Updated: [" + ticket.getTicketNumber() + "] Status: " + ticket.getStatus();
        String message = "Your IT support ticket status has been updated to <strong>" + ticket.getStatus() + "</strong>."
                + (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank() 
                   ? "<br><br><strong>Resolution Notes:</strong> " + escapeHtml(ticket.getResolutionNotes()) : "");
        String htmlContent = buildTicketEmailHtml(ticket, portalUrl, "Ticket Status: " + ticket.getStatus(), message);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(ticket.getRequesterEmail());
        notification.setRecipientName(ticket.getRequesterName());
        notification.setSubject(subject);
        notification.setEmailType("TICKET_UPDATED");
        notification.setContent(htmlContent);
        notification.setPortalUrl(portalUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        dispatchViaSmtp(saved);
        return saved;
    }

    public EmailNotification sendTicketTechnicianAlert(Ticket ticket, String technicianEmail, String technicianName) {
        String dashboardUrl = getBasePortalUrl() + "/?tab=tickets&ticket=" + java.net.URLEncoder.encode(ticket.getTicketNumber(), java.nio.charset.StandardCharsets.UTF_8);

        String reqName = (ticket.getRequesterName() != null && !ticket.getRequesterName().isBlank())
                ? ticket.getRequesterName()
                : "Employee";

        String subject = "[IT Helpdesk] New Ticket Assigned: [" + ticket.getTicketNumber() + "] "
                + ticket.getTitle() + " (Requester: " + reqName + ")";

        String htmlContent = buildTechnicianTicketAlertHtml(ticket, technicianName, dashboardUrl, false, null);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(technicianEmail);
        notification.setRecipientName(technicianName != null && !technicianName.isBlank() ? technicianName : "IT Technician");
        notification.setSubject(subject);
        notification.setEmailType("TICKET_TECHNICIAN_ALERT");
        notification.setContent(htmlContent);
        notification.setPortalUrl(dashboardUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        log.info("Dispatched technician alert notification for ticket {} to {} ({})", ticket.getTicketNumber(), technicianName, technicianEmail);
        dispatchViaSmtp(saved);
        return saved;
    }

    public EmailNotification sendTicketReassignedToTechnicianNotification(Ticket ticket, String technicianEmail, String technicianName, String updatedBy) {
        String dashboardUrl = getBasePortalUrl() + "/?tab=tickets&ticket=" + java.net.URLEncoder.encode(ticket.getTicketNumber(), java.nio.charset.StandardCharsets.UTF_8);

        String subject = "[IT Helpdesk] Ticket Reassigned to You: [" + ticket.getTicketNumber() + "] " + ticket.getTitle();
        String htmlContent = buildTechnicianTicketAlertHtml(ticket, technicianName, dashboardUrl, true, updatedBy);

        EmailNotification notification = new EmailNotification();
        notification.setRecipientEmail(technicianEmail);
        notification.setRecipientName(technicianName != null && !technicianName.isBlank() ? technicianName : "IT Technician");
        notification.setSubject(subject);
        notification.setEmailType("TICKET_REASSIGNED_ALERT");
        notification.setContent(htmlContent);
        notification.setPortalUrl(dashboardUrl);
        notification.setStatus("DELIVERED_TO_OUTBOX");
        notification.setSentAt(LocalDateTime.now());

        EmailNotification saved = emailRepository.save(notification);
        log.info("Dispatched technician reassignment notification for ticket {} to {} ({})", ticket.getTicketNumber(), technicianName, technicianEmail);
        dispatchViaSmtp(saved);
        return saved;
    }

    private String getPriorityBadgeHtml(TicketPriority priority) {
        String name = priority != null ? priority.name() : "MEDIUM";
        String bg = "#e0f2fe";
        String color = "#0369a1";
        String border = "#bae6fd";
        if (priority != null) {
            switch (priority) {
                case CRITICAL -> { bg = "#fee2e2"; color = "#991b1b"; border = "#fca5a5"; }
                case HIGH -> { bg = "#fef3c7"; color = "#92400e"; border = "#fcd34d"; }
                case LOW -> { bg = "#f1f5f9"; color = "#475569"; border = "#cbd5e1"; }
                default -> { bg = "#e0f2fe"; color = "#0369a1"; border = "#bae6fd"; }
            }
        }
        return "<span style='display: inline-block; font-size: 11px; font-weight: 700; background-color: " + bg
                + "; color: " + color + "; border: 1px solid " + border + "; padding: 2px 8px; border-radius: 4px; letter-spacing: 0.5px;'>"
                + name + "</span>";
    }

    private String getStatusBadgeHtml(TicketStatus status) {
        String name = status != null ? status.name() : "OPEN";
        String bg = "#dbeafe";
        String color = "#1e40af";
        String border = "#93c5fd";
        if (status != null) {
            switch (status) {
                case IN_PROGRESS -> { bg = "#fef3c7"; color = "#92400e"; border = "#fcd34d"; }
                case RESOLVED -> { bg = "#dcfce7"; color = "#166534"; border = "#86efac"; }
                case CLOSED -> { bg = "#f1f5f9"; color = "#475569"; border = "#cbd5e1"; }
                default -> { bg = "#dbeafe"; color = "#1e40af"; border = "#93c5fd"; }
            }
        }
        return "<span style='display: inline-block; font-size: 11px; font-weight: 700; background-color: " + bg
                + "; color: " + color + "; border: 1px solid " + border + "; padding: 2px 8px; border-radius: 4px; letter-spacing: 0.5px;'>"
                + name.replace("_", " ") + "</span>";
    }

    private String buildTechnicianTicketAlertHtml(Ticket ticket, String technicianName,
                                                  String dashboardUrl,
                                                  boolean isReassignment, String updatedBy) {
        String actionTitle = isReassignment ? "TICKET REASSIGNED" : "NEW TICKET ASSIGNED";
        String actionSub = isReassignment
                ? "This support ticket has been reassigned to you by <strong>" + escapeHtml(updatedBy != null ? updatedBy : "IT Admin") + "</strong>."
                : "A new IT support ticket has been registered and assigned directly to your queue.";

        String replyMailto = "mailto:" + escapeHtml(ticket.getRequesterEmail()) + "?subject="
                + escapeHtml("Re: [" + ticket.getTicketNumber() + "] " + ticket.getTitle());

        String createdTime = (ticket.getCreatedAt() != null)
                ? ticket.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
                : "Just now";

        String dept = (ticket.getRequesterDepartment() != null && !ticket.getRequesterDepartment().isBlank())
                ? ticket.getRequesterDepartment()
                : "General";

        String techDisplay = (technicianName != null && !technicianName.isBlank()) ? technicianName : "IT Technician";

        StringBuilder assetSection = new StringBuilder();
        if (ticket.getAsset() != null) {
            Asset a = ticket.getAsset();
            String mfgModel = (a.getManufacturer() != null ? a.getManufacturer() : "")
                    + ((a.getModel() != null && !a.getModel().isBlank()) ? " " + a.getModel() : "");
            if (mfgModel.isBlank()) mfgModel = "Standard Configuration";

            assetSection.append("<!-- Horizontal Attached Device Strip -->")
                    .append("<table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 20px; background-color: #f8fafc; border-collapse: collapse;'>")
                    .append("  <tr>")
                    .append("    <td width='40%' style='padding: 10px 14px; border-right: 1px solid #e2e8f0; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Attached Hardware</div>")
                    .append("      <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 3px;'>")
                    .append(escapeHtml(a.getName())).append(" (").append(escapeHtml(mfgModel)).append(")</div>")
                    .append("    </td>")
                    .append("    <td width='30%' style='padding: 10px 14px; border-right: 1px solid #e2e8f0; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Asset Tag</div>")
                    .append("      <div style='font-size: 13px; font-weight: 700; color: #2563eb; margin-top: 3px; font-family: monospace;'>")
                    .append(escapeHtml(a.getAssetTag())).append("</div>")
                    .append("    </td>")
                    .append("    <td width='30%' style='padding: 10px 14px; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Serial Number</div>")
                    .append("      <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 3px; font-family: monospace;'>")
                    .append(escapeHtml(a.getSerialNumber() != null ? a.getSerialNumber() : "N/A")).append("</div>")
                    .append("    </td>")
                    .append("  </tr>")
                    .append("</table>");
        }

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 28px 16px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='750' border='0' cellspacing='0' cellpadding='0' style='max-width: 750px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06);'>"
                + "        <!-- Horizontal Header Bar -->"
                + "        <tr><td style='padding: 18px 28px; background-color: #0f172a; border-bottom: 2px solid #2563eb;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <span style='font-size: 16px; font-weight: 700; color: #ffffff;'>BMC AssetHub</span>"
                + "                <span style='font-size: 13px; color: #94a3b8; margin-left: 10px;'>| &nbsp; Helpdesk Operations</span>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-size: 12px; font-family: monospace; font-weight: 700; color: #ffffff; background-color: #1e293b; border: 1px solid #334155; padding: 3px 10px; border-radius: 4px; margin-right: 8px;'>"
                + "                  " + escapeHtml(ticket.getTicketNumber()) + "</span>"
                + "                " + getPriorityBadgeHtml(ticket.getPriority())
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Body -->"
                + "        <tr><td style='padding: 24px 28px;'>"
                + "          <!-- Notification Callout Banner -->"
                + "          <div style='background-color: #f8fafc; border: 1px solid #e2e8f0; border-left: 4px solid #2563eb; border-radius: 4px; padding: 12px 18px; margin-bottom: 20px;'>"
                + "            <span style='font-size: 11px; font-weight: 700; color: #2563eb; letter-spacing: 0.5px; text-transform: uppercase;'>" + actionTitle + "</span>"
                + "            <div style='font-size: 13px; color: #334155; margin-top: 3px; line-height: 1.4;'>" + actionSub + "</div>"
                + "          </div>"
                + "          <!-- Horizontal 4-Column Metadata Matrix (2 Rows) -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 20px; border-collapse: collapse;'>"
                + "            <tr>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Ticket ID</div>"
                + "                <div style='font-size: 13px; font-weight: 700; color: #0f172a; margin-top: 4px; font-family: monospace;'>" + escapeHtml(ticket.getTicketNumber()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Priority</div>"
                + "                <div style='margin-top: 4px;'>" + getPriorityBadgeHtml(ticket.getPriority()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Category</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + ticket.getCategory() + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Status</div>"
                + "                <div style='margin-top: 4px;'>" + getStatusBadgeHtml(ticket.getStatus()) + "</div>"
                + "              </td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Requester</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(ticket.getRequesterName()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Requester Email</div>"
                + "                <div style='font-size: 12px; font-weight: 500; color: #2563eb; margin-top: 4px; word-break: break-all;'>" + escapeHtml(ticket.getRequesterEmail()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Department</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(dept) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Assigned Tech</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(techDisplay) + "</div>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + assetSection.toString()
                + "          <!-- Horizontal Issue Container -->"
                + "          <div style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 22px;'>"
                + "            <div style='padding: 10px 16px; background-color: #f8fafc; border-bottom: 1px solid #e2e8f0;'>"
                + "              <span style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Reported Issue: </span>"
                + "              <span style='font-size: 14px; font-weight: 700; color: #0f172a; margin-left: 6px;'>" + escapeHtml(ticket.getTitle()) + "</span>"
                + "            </div>"
                + "            <div style='padding: 14px 16px; background-color: #ffffff; font-size: 13px; color: #334155; line-height: 1.6; white-space: pre-wrap;'>"
                + escapeHtml(ticket.getDescription() != null ? ticket.getDescription() : "No details provided.")
                + "            </div>"
                + "          </div>"
                + "          <!-- Horizontal Action Toolbar -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <table border='0' cellspacing='0' cellpadding='0'>"
                + "                  <tr>"
                + "                    <td>"
                + "                      <a href='" + escapeHtml(dashboardUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 13px; font-weight: 600; border-radius: 5px;'>Open Ticket in Helpdesk &rarr;</a>"
                + "                    </td>"
                + "                    <td style='padding-left: 10px;'>"
                + "                      <a href='" + replyMailto + "' style='display: inline-block; background-color: #ffffff; color: #334155; border: 1px solid #cbd5e1; text-decoration: none; padding: 9px 18px; font-size: 13px; font-weight: 600; border-radius: 5px;'>Reply to Requester</a>"
                + "                    </td>"
                + "                  </tr>"
                + "                </table>"
                + "              </td>"
                + "              <td align='right' style='color: #64748b; font-size: 12px;'>"
                + "                Logged: " + createdTime
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Footer -->"
                + "        <tr><td style='padding: 14px 28px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>BMC AssetHub &bull; BM Consulting IT Support</td>"
                + "              <td align='right'>Operational Dispatch Notification</td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    private String buildTicketEmailHtml(Ticket ticket, String portalUrl, String headerTitle, String bodyMessage) {
        String dept = (ticket.getRequesterDepartment() != null && !ticket.getRequesterDepartment().isBlank())
                ? ticket.getRequesterDepartment()
                : "General";

        String techDisplay = (ticket.getAssignedTo() != null && !ticket.getAssignedTo().isBlank())
                ? ticket.getAssignedTo()
                : "IT Support Queue";

        String createdTime = (ticket.getCreatedAt() != null)
                ? ticket.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"))
                : "Recent";

        StringBuilder assetSection = new StringBuilder();
        if (ticket.getAsset() != null) {
            Asset a = ticket.getAsset();
            String mfgModel = (a.getManufacturer() != null ? a.getManufacturer() : "")
                    + ((a.getModel() != null && !a.getModel().isBlank()) ? " " + a.getModel() : "");
            if (mfgModel.isBlank()) mfgModel = "Standard Configuration";

            assetSection.append("<!-- Horizontal Attached Device Strip -->")
                    .append("<table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 20px; background-color: #f8fafc; border-collapse: collapse;'>")
                    .append("  <tr>")
                    .append("    <td width='40%' style='padding: 10px 14px; border-right: 1px solid #e2e8f0; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Attached Hardware</div>")
                    .append("      <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 3px;'>")
                    .append(escapeHtml(a.getName())).append(" (").append(escapeHtml(mfgModel)).append(")</div>")
                    .append("    </td>")
                    .append("    <td width='30%' style='padding: 10px 14px; border-right: 1px solid #e2e8f0; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Asset Tag</div>")
                    .append("      <div style='font-size: 13px; font-weight: 700; color: #2563eb; margin-top: 3px; font-family: monospace;'>")
                    .append(escapeHtml(a.getAssetTag())).append("</div>")
                    .append("    </td>")
                    .append("    <td width='30%' style='padding: 10px 14px; vertical-align: top;'>")
                    .append("      <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Serial Number</div>")
                    .append("      <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 3px; font-family: monospace;'>")
                    .append(escapeHtml(a.getSerialNumber() != null ? a.getSerialNumber() : "N/A")).append("</div>")
                    .append("    </td>")
                    .append("  </tr>")
                    .append("</table>");
        }

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 28px 16px; background-color: #f1f5f9; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='750' border='0' cellspacing='0' cellpadding='0' style='max-width: 750px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.06);'>"
                + "        <!-- Horizontal Header Bar -->"
                + "        <tr><td style='padding: 18px 28px; background-color: #0f172a; border-bottom: 2px solid #2563eb;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <span style='font-size: 16px; font-weight: 700; color: #ffffff;'>BMC AssetHub</span>"
                + "                <span style='font-size: 13px; color: #94a3b8; margin-left: 10px;'>| &nbsp; " + escapeHtml(headerTitle) + "</span>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-size: 12px; font-family: monospace; font-weight: 700; color: #ffffff; background-color: #1e293b; border: 1px solid #334155; padding: 3px 10px; border-radius: 4px; margin-right: 8px;'>"
                + "                  " + escapeHtml(ticket.getTicketNumber()) + "</span>"
                + "                " + getStatusBadgeHtml(ticket.getStatus())
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Body -->"
                + "        <tr><td style='padding: 24px 28px;'>"
                + "          <!-- Notification Callout Banner -->"
                + "          <div style='background-color: #f8fafc; border: 1px solid #e2e8f0; border-left: 4px solid #2563eb; border-radius: 4px; padding: 12px 18px; margin-bottom: 20px;'>"
                + "            <div style='font-size: 14px; color: #0f172a; font-weight: 600;'>Hello " + escapeHtml(ticket.getRequesterName()) + ",</div>"
                + "            <div style='font-size: 13px; color: #334155; margin-top: 4px; line-height: 1.5;'>" + bodyMessage + "</div>"
                + "          </div>"
                + "          <!-- Horizontal 4-Column Metadata Matrix (2 Rows) -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 20px; border-collapse: collapse;'>"
                + "            <tr>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Ticket ID</div>"
                + "                <div style='font-size: 13px; font-weight: 700; color: #0f172a; margin-top: 4px; font-family: monospace;'>" + escapeHtml(ticket.getTicketNumber()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Status</div>"
                + "                <div style='margin-top: 4px;'>" + getStatusBadgeHtml(ticket.getStatus()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Priority</div>"
                + "                <div style='margin-top: 4px;'>" + getPriorityBadgeHtml(ticket.getPriority()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #f8fafc;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Category</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + ticket.getCategory() + "</div>"
                + "              </td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Requester</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(ticket.getRequesterName()) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Department</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(dept) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Assigned Tech</div>"
                + "                <div style='font-size: 13px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + escapeHtml(techDisplay) + "</div>"
                + "              </td>"
                + "              <td width='25%' style='padding: 11px 14px; border: 1px solid #e2e8f0; vertical-align: top; background-color: #ffffff;'>"
                + "                <div style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Logged At</div>"
                + "                <div style='font-size: 12px; font-weight: 600; color: #0f172a; margin-top: 4px;'>" + createdTime + "</div>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + assetSection.toString()
                + "          <!-- Horizontal Issue Container -->"
                + "          <div style='border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden; margin-bottom: 22px;'>"
                + "            <div style='padding: 10px 16px; background-color: #f8fafc; border-bottom: 1px solid #e2e8f0;'>"
                + "              <span style='font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748b; letter-spacing: 0.5px;'>Subject: </span>"
                + "              <span style='font-size: 14px; font-weight: 700; color: #0f172a; margin-left: 6px;'>" + escapeHtml(ticket.getTitle()) + "</span>"
                + "            </div>"
                + "            <div style='padding: 14px 16px; background-color: #ffffff; font-size: 13px; color: #334155; line-height: 1.6; white-space: pre-wrap;'>"
                + escapeHtml(ticket.getDescription() != null ? ticket.getDescription() : "No details provided.")
                + "            </div>"
                + "          </div>"
                + "          <!-- Horizontal Action Toolbar -->"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td align='left'>"
                + "                <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 13px; font-weight: 600; border-radius: 5px;'>View in Employee Portal &rarr;</a>"
                + "              </td>"
                + "              <td align='right' style='color: #64748b; font-size: 12px;'>"
                + "                Real-time updates and ticket comments are available in your portal."
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <!-- Horizontal Footer -->"
                + "        <tr><td style='padding: 14px 28px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>BMC AssetHub &bull; BM Consulting IT Support</td>"
                + "              <td align='right'>Automated Service Desk Notification</td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
