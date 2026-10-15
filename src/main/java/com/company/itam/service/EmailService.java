package com.company.itam.service;

import com.company.itam.model.AppUser;
import com.company.itam.model.Asset;
import com.company.itam.model.EmailNotification;
import com.company.itam.model.SmtpSetting;
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

    @Value("${itam.mail.from-name:Aegis IT Asset Management}")
    private String propFromName;

    @Value("${itam.mail.enabled:true}")
    private boolean propMailEnabled;

    public EmailService(EmailNotificationRepository emailRepository, SmtpSettingRepository smtpSettingRepository) {
        this.emailRepository = emailRepository;
        this.smtpSettingRepository = smtpSettingRepository;
    }

    public EmailNotification sendWelcomeEmail(AppUser user, String rawPassword, String customPortalUrl) {
        String basePortalUrl = customPortalUrl != null && !customPortalUrl.isBlank()
                ? customPortalUrl
                : "http://localhost:" + serverPort;

        String loginUrl = basePortalUrl + "/?email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "Welcome to Aegis ITAM - Your Organization Account & Equipment Access";
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
        String subject = "Welcome to Aegis ITAM - Your Organization Account & Equipment Access";
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
        // 1. Direct project configuration from application.properties takes precedence
        if (propMailEnabled && propUsername != null && !propUsername.isBlank() && !propUsername.contains("YOUR_EMAIL_HERE")) {
            log.info("Using project application.properties SMTP config (Host: {}:{})", propHost, propPort);
            return dispatchUsingConfig(notification, propHost, propPort, propUsername, propPassword, propFromEmail, propFromName);
        }

        // 2. Fallback to dynamic database settings if configured
        Optional<SmtpSetting> settingOpt = smtpSettingRepository.findFirstByOrderByIdAsc();
        if (settingOpt.isPresent() && settingOpt.get().isEnabled()) {
            SmtpSetting s = settingOpt.get();
            return dispatchUsingConfig(notification, s.getHost(), s.getPort(), s.getUsername(), s.getPassword(), s.getFromEmail(), s.getFromName());
        }

        log.info("Physical SMTP credentials not yet provided in application.properties. Email logged to Outbox.");
        return false;
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
            String personal = (fromName != null && !fromName.isBlank()) ? fromName : "Aegis IT Asset Management";

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
        String personal = (fromName != null && !fromName.isBlank()) ? fromName : "Aegis IT Asset Management";

        helper.setFrom(fromAddr, personal);
        helper.setTo(recipientEmail);
        helper.setSubject("Test Email: Aegis ITAM SMTP Connectivity Verified");

        String testHtml = "<div style='font-family:sans-serif; background:#0f172a; color:#e2e8f0; padding:24px; border-radius:8px;'>"
                + "<h2 style='color:#38bdf8;'>SMTP Connectivity Verified!</h2>"
                + "<p>This is a test notification from your <strong>Aegis IT Asset Management</strong> system.</p>"
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

    private String buildWelcomeEmailHtml(AppUser user, String password, String portalUrl) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'>"
                + "<style>"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 24px; }"
                + ".card { max-width: 600px; margin: 0 auto; background: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }"
                + ".header { background: linear-gradient(135deg, #4f46e5 0%, #06b6d4 100%); padding: 32px 24px; text-align: center; color: white; }"
                + ".header h1 { margin: 0 0 8px 0; font-size: 24px; font-weight: 700; letter-spacing: -0.5px; }"
                + ".header p { margin: 0; opacity: 0.9; font-size: 14px; }"
                + ".body { padding: 32px 24px; line-height: 1.6; color: #cbd5e1; font-size: 15px; }"
                + ".creds-box { background: #0f172a; border: 1px solid #38bdf8; border-radius: 8px; padding: 20px; margin: 24px 0; }"
                + ".cred-row { display: flex; justify-content: space-between; margin-bottom: 12px; font-size: 14px; }"
                + ".cred-label { color: #94a3b8; font-weight: 500; }"
                + ".cred-value { font-family: monospace; font-weight: 700; color: #38bdf8; background: #1e293b; padding: 4px 10px; border-radius: 4px; }"
                + ".btn-container { text-align: center; margin: 32px 0 16px 0; }"
                + ".btn { display: inline-block; background: linear-gradient(135deg, #6366f1 0%, #3b82f6 100%); color: #ffffff !important; text-decoration: none; font-weight: 600; padding: 14px 32px; border-radius: 8px; font-size: 16px; box-shadow: 0 4px 14px rgba(99,102,241,0.4); }"
                + ".footer { border-top: 1px solid #334155; padding: 20px 24px; text-align: center; font-size: 12px; color: #64748b; background: #162032; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='card'>"
                + "  <div class='header'>"
                + "    <h1>Aegis ITAM System</h1>"
                + "    <p>Enterprise Asset & Equipment Portal</p>"
                + "  </div>"
                + "  <div class='body'>"
                + "    <h2 style='color:#f8fafc; font-size:18px; margin-top:0;'>Hello " + escapeHtml(user.getFullName()) + ",</h2>"
                + "    <p>You have been officially registered in the <strong>Enterprise IT Asset Management</strong> system for the <strong>" + escapeHtml(user.getDepartment() != null ? user.getDepartment() : "General") + "</strong> department.</p>"
                + "    <p>You can now sign in to your personal employee portal to review your assigned hardware assets (laptops, monitors, devices), view provisioned software licenses, and request IT maintenance or service.</p>"
                + "    <div class='creds-box'>"
                + "      <div style='font-weight:600; color:#38bdf8; margin-bottom:12px; font-size:13px; text-transform:uppercase; letter-spacing:0.5px;'>Your Login Credentials</div>"
                + "      <div class='cred-row'><span class='cred-label'>Organization Email / User ID:</span> <span class='cred-value'>" + escapeHtml(user.getEmail()) + "</span></div>"
                + "      <div class='cred-row' style='margin-bottom:0;'><span class='cred-label'>Temporary Password:</span> <span class='cred-value'>" + escapeHtml(password) + "</span></div>"
                + "    </div>"
                + "    <div class='btn-container'>"
                + "      <a href='" + escapeHtml(portalUrl) + "' class='btn' target='_blank'>Log In to IT Portal &rarr;</a>"
                + "    </div>"
                + "    <p style='font-size:13px; color:#94a3b8; text-align:center;'>Direct link: <a href='" + escapeHtml(portalUrl) + "' style='color:#38bdf8;'>" + escapeHtml(portalUrl) + "</a></p>"
                + "    <p style='font-size:12px; color:#64748b; margin-top:24px;'>Note: Please change your password upon your first successful login from the My Profile section.</p>"
                + "  </div>"
                + "  <div class='footer'>"
                + "    &copy; " + LocalDateTime.now().getYear() + " Aegis IT Asset Management • IT Infrastructure & Operations"
                + "  </div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    private String buildAssetAssignedEmailHtml(AppUser user, Asset asset, String portalUrl) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'>"
                + "<style>"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 24px; }"
                + ".card { max-width: 600px; margin: 0 auto; background: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden; }"
                + ".header { background: linear-gradient(135deg, #0ea5e9 0%, #10b981 100%); padding: 28px 24px; text-align: center; color: white; }"
                + ".body { padding: 28px 24px; line-height: 1.6; color: #cbd5e1; font-size: 15px; }"
                + ".box { background: #0f172a; border-radius: 8px; padding: 18px; margin: 20px 0; border: 1px solid #334155; }"
                + ".item { margin-bottom: 8px; font-size: 14px; }"
                + ".btn { display: inline-block; background: #0ea5e9; color: white !important; text-decoration: none; padding: 12px 24px; border-radius: 6px; font-weight: 600; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='card'>"
                + "  <div class='header'>"
                + "    <h2 style='margin:0;'>New Equipment Assigned</h2>"
                + "  </div>"
                + "  <div class='body'>"
                + "    <p>Hello " + escapeHtml(user.getFullName()) + ",</p>"
                + "    <p>A new hardware asset has been assigned to you in the IT Asset Management system:</p>"
                + "    <div class='box'>"
                + "      <div class='item'><strong>Asset Tag:</strong> " + escapeHtml(asset.getAssetTag()) + "</div>"
                + "      <div class='item'><strong>Device:</strong> " + escapeHtml(asset.getName()) + " (" + escapeHtml(asset.getManufacturer() != null ? asset.getManufacturer() : "") + " " + escapeHtml(asset.getModel() != null ? asset.getModel() : "") + ")</div>"
                + "      <div class='item'><strong>Serial Number:</strong> " + escapeHtml(asset.getSerialNumber() != null ? asset.getSerialNumber() : "N/A") + "</div>"
                + "      <div class='item'><strong>Condition:</strong> " + asset.getCondition() + "</div>"
                + "      <div class='item'><strong>Specifications:</strong> " + escapeHtml(asset.getSpecifications() != null ? asset.getSpecifications() : "Standard") + "</div>"
                + "    </div>"
                + "    <p>You can view full equipment details or report any hardware issues anytime on your portal:</p>"
                + "    <p style='text-align:center;'><a href='" + escapeHtml(portalUrl) + "' class='btn'>View Assigned Equipment</a></p>"
                + "  </div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    public EmailNotification sendTicketCreatedNotification(com.company.itam.model.Ticket ticket) {
        String portalUrl = "http://localhost:" + serverPort + "/?email=" + java.net.URLEncoder.encode(ticket.getRequesterEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "IT Support Ticket Created: [" + ticket.getTicketNumber() + "] " + ticket.getTitle();
        String htmlContent = buildTicketEmailHtml(ticket, portalUrl, "Support Ticket Registered",
                "Your IT support ticket has been received by the IT Helpdesk team. A technician will review and action this request.");

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

    public EmailNotification sendTicketUpdatedNotification(com.company.itam.model.Ticket ticket) {
        String portalUrl = "http://localhost:" + serverPort + "/?email=" + java.net.URLEncoder.encode(ticket.getRequesterEmail(), java.nio.charset.StandardCharsets.UTF_8);
        String subject = "IT Ticket Status Updated: [" + ticket.getTicketNumber() + "] " + ticket.getStatus();
        String message = "Your IT support ticket has been updated to <strong>" + ticket.getStatus() + "</strong>."
                + (ticket.getResolutionNotes() != null && !ticket.getResolutionNotes().isBlank() 
                   ? "<br><br><strong>IT Resolution Notes:</strong> " + escapeHtml(ticket.getResolutionNotes()) : "");
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

    private String buildTicketEmailHtml(com.company.itam.model.Ticket ticket, String portalUrl, String headerTitle, String bodyMessage) {
        String assetInfo = ticket.getAsset() != null 
                ? "<div class='item'><strong>Linked Device:</strong> " + escapeHtml(ticket.getAsset().getAssetTag()) + " - " + escapeHtml(ticket.getAsset().getName()) + "</div>"
                : "";
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #0f172a; color: #e2e8f0; margin: 0; padding: 24px; }"
                + ".card { max-width: 600px; margin: 0 auto; background: #1e293b; border-radius: 12px; border: 1px solid #334155; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }"
                + ".header { background: linear-gradient(135deg, #6366f1 0%, #06b6d4 100%); padding: 28px 24px; text-align: center; color: white; }"
                + ".body { padding: 28px 24px; line-height: 1.6; color: #cbd5e1; font-size: 15px; }"
                + ".box { background: #0f172a; border: 1px solid #334155; border-radius: 8px; padding: 16px; margin: 20px 0; font-size: 14px; }"
                + ".item { margin-bottom: 8px; }"
                + ".btn { display: inline-block; background: #6366f1; color: #ffffff !important; text-decoration: none; font-weight: 600; padding: 12px 28px; border-radius: 8px; font-size: 15px; margin-top: 14px; }"
                + "</style></head><body><div class='card'>"
                + "<div class='header'><h2 style='margin:0;'>" + escapeHtml(headerTitle) + "</h2></div>"
                + "<div class='body'>"
                + "<p>Hello " + escapeHtml(ticket.getRequesterName()) + ",</p>"
                + "<p>" + bodyMessage + "</p>"
                + "<div class='box'>"
                + "<div class='item'><strong>Ticket ID:</strong> " + escapeHtml(ticket.getTicketNumber()) + "</div>"
                + "<div class='item'><strong>Title:</strong> " + escapeHtml(ticket.getTitle()) + "</div>"
                + "<div class='item'><strong>Category:</strong> " + ticket.getCategory() + "</div>"
                + "<div class='item'><strong>Priority:</strong> " + ticket.getPriority() + "</div>"
                + "<div class='item'><strong>Status:</strong> " + ticket.getStatus() + "</div>"
                + assetInfo
                + "</div>"
                + "<p style='text-align:center;'><a href='" + escapeHtml(portalUrl) + "' class='btn'>View in IT Portal</a></p>"
                + "</div></div></body></html>";
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
