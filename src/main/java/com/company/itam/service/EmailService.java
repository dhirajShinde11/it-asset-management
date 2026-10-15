package com.company.itam.service;

import com.company.itam.model.AppUser;
import com.company.itam.model.Asset;
import com.company.itam.model.EmailNotification;
import com.company.itam.model.SmtpSetting;
import com.company.itam.model.Ticket;
import com.company.itam.model.TicketPriority;
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
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 24px 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='600' border='0' cellspacing='0' cellpadding='0' style='max-width: 600px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden;'>"
                + "        <tr><td style='padding: 24px 32px 18px 32px; border-bottom: 2px solid #2563eb; background-color: #ffffff;'>"
                + "          <div style='font-size: 17px; font-weight: 700; color: #0f172a;'>Aegis IT Asset Management</div>"
                + "          <div style='font-size: 13px; color: #64748b; margin-top: 2px;'>Organization Account &bull; Access Provisioning</div>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 28px 32px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'><strong>Hello " + escapeHtml(user.getFullName()) + ",</strong></p>"
                + "          <p style='margin: 0 0 16px 0; font-size: 14px; color: #475569; line-height: 1.5;'>Your account has been registered in the IT Asset Management system for the <strong>" + escapeHtml(user.getDepartment() != null ? user.getDepartment() : "General") + "</strong> department. You can now access your self-service portal to view assigned hardware, licenses, and raise IT support requests.</p>"
                + "          <div style='background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px; padding: 16px 18px; margin: 20px 0;'>"
                + "            <div style='font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; margin-bottom: 12px;'>Your Login Credentials</div>"
                + "            <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "              <tr>"
                + "                <td style='padding: 4px 0; color: #64748b; font-size: 13px; width: 140px;'>Username / Email:</td>"
                + "                <td style='padding: 4px 0; color: #0f172a; font-size: 13px; font-family: monospace; font-weight: 600;'>" + escapeHtml(user.getEmail()) + "</td>"
                + "              </tr>"
                + "              <tr>"
                + "                <td style='padding: 4px 0; color: #64748b; font-size: 13px;'>Temporary Password:</td>"
                + "                <td style='padding: 4px 0; color: #2563eb; font-size: 13px; font-family: monospace; font-weight: 700;'>" + escapeHtml(password) + "</td>"
                + "              </tr>"
                + "            </table>"
                + "          </div>"
                + "          <div style='margin: 24px 0 16px 0;'>"
                + "            <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 14px; font-weight: 600; border-radius: 4px;'>Log In to IT Portal</a>"
                + "          </div>"
                + "          <p style='font-size: 12px; color: #94a3b8; margin: 16px 0 0 0;'>Please change your password upon your first successful login.</p>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 16px 32px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; line-height: 1.5;'>"
                + "          <div style='font-weight: 500; color: #475569;'>Aegis IT Asset Management &bull; BM Consulting IT Operations</div>"
                + "          <div style='margin-top: 3px;'>Need assistance? Contact your organization IT administrator.</div>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    private String buildAssetAssignedEmailHtml(AppUser user, Asset asset, String portalUrl) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 24px 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='600' border='0' cellspacing='0' cellpadding='0' style='max-width: 600px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden;'>"
                + "        <tr><td style='padding: 24px 32px 18px 32px; border-bottom: 2px solid #2563eb; background-color: #ffffff;'>"
                + "          <div style='font-size: 17px; font-weight: 700; color: #0f172a;'>Aegis IT Asset Management</div>"
                + "          <div style='font-size: 13px; color: #64748b; margin-top: 2px;'>Equipment Custody &bull; New Assignment</div>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 28px 32px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'><strong>Hello " + escapeHtml(user.getFullName()) + ",</strong></p>"
                + "          <p style='margin: 0 0 16px 0; font-size: 14px; color: #475569; line-height: 1.5;'>A new hardware asset has been assigned to your custody in the IT Asset Management system:</p>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 4px; border-collapse: collapse; margin-bottom: 22px;'>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; width: 130px; border-bottom: 1px solid #f1f5f9;'>Asset Tag</td>"
                + "              <td style='padding: 8px 12px; color: #2563eb; font-size: 13px; font-family: monospace; font-weight: 600; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(asset.getAssetTag()) + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Device Name</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 500; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(asset.getName()) + " (" + escapeHtml(asset.getManufacturer() != null ? asset.getManufacturer() : "") + " " + escapeHtml(asset.getModel() != null ? asset.getModel() : "") + ")</td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Serial Number</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(asset.getSerialNumber() != null ? asset.getSerialNumber() : "N/A") + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Condition</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + asset.getCondition() + "</td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px;'>Specifications</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px;'>" + escapeHtml(asset.getSpecifications() != null ? asset.getSpecifications() : "Standard") + "</td>"
                + "            </tr>"
                + "          </table>"
                + "          <div style='margin-bottom: 16px;'>"
                + "            <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 14px; font-weight: 600; border-radius: 4px;'>View Assigned Equipment</a>"
                + "          </div>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 16px 32px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; line-height: 1.5;'>"
                + "          <div style='font-weight: 500; color: #475569;'>Aegis IT Asset Management &bull; BM Consulting IT Operations</div>"
                + "          <div style='margin-top: 3px;'>You can view full equipment details or report hardware issues via your employee portal.</div>"
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

    private String buildTechnicianTicketAlertHtml(Ticket ticket, String technicianName,
                                                  String dashboardUrl,
                                                  boolean isReassignment, String updatedBy) {
        String actionTitle = isReassignment ? "Ticket Reassigned" : "New Ticket Assigned";
        String actionSub = isReassignment
                ? "This support ticket has been reassigned to you by " + escapeHtml(updatedBy != null ? updatedBy : "IT Admin") + "."
                : "A new IT support ticket has been submitted and assigned to your queue.";

        String prioText = ticket.getPriority() != null ? ticket.getPriority().name() : "MEDIUM";
        String prioBadgeBg = "#e0f2fe";
        String prioBadgeColor = "#0369a1";
        if (ticket.getPriority() != null) {
            switch (ticket.getPriority()) {
                case CRITICAL -> {
                    prioBadgeBg = "#fee2e2";
                    prioBadgeColor = "#991b1b";
                }
                case HIGH -> {
                    prioBadgeBg = "#fef3c7";
                    prioBadgeColor = "#92400e";
                }
                case LOW -> {
                    prioBadgeBg = "#f1f5f9";
                    prioBadgeColor = "#475569";
                }
                default -> {
                    prioBadgeBg = "#e0f2fe";
                    prioBadgeColor = "#0369a1";
                }
            }
        }

        StringBuilder assetRow = new StringBuilder();
        if (ticket.getAsset() != null) {
            Asset a = ticket.getAsset();
            assetRow.append("<tr>")
                    .append("<td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Attached Device</td>")
                    .append("<td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 500; border-bottom: 1px solid #f1f5f9;'>")
                    .append(escapeHtml(a.getName()))
                    .append(" (Tag: <span style='font-family: monospace; color: #2563eb;'>").append(escapeHtml(a.getAssetTag())).append("</span>")
                    .append(a.getSerialNumber() != null && !a.getSerialNumber().isBlank() ? ", S/N: " + escapeHtml(a.getSerialNumber()) : "")
                    .append(")</td></tr>");
        }

        String replyMailto = "mailto:" + escapeHtml(ticket.getRequesterEmail()) + "?subject="
                + escapeHtml("Re: [" + ticket.getTicketNumber() + "] " + ticket.getTitle());

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 24px 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='600' border='0' cellspacing='0' cellpadding='0' style='max-width: 600px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden;'>"
                + "        <tr><td style='padding: 24px 32px 18px 32px; border-bottom: 2px solid #2563eb; background-color: #ffffff;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>"
                + "                <div style='font-size: 17px; font-weight: 700; color: #0f172a;'>Aegis IT Asset Management</div>"
                + "                <div style='font-size: 13px; color: #64748b; margin-top: 2px;'>IT Helpdesk &bull; " + escapeHtml(actionTitle) + "</div>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-family: monospace; font-size: 13px; font-weight: 600; color: #0f172a; background-color: #f1f5f9; padding: 4px 10px; border-radius: 4px; border: 1px solid #e2e8f0;'>" + escapeHtml(ticket.getTicketNumber()) + "</span>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 28px 32px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'><strong>Hello " + escapeHtml(technicianName != null && !technicianName.isBlank() ? technicianName : "Technician") + ",</strong></p>"
                + "          <p style='margin: 0 0 20px 0; font-size: 14px; color: #475569; line-height: 1.5;'>" + escapeHtml(actionSub) + "</p>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 4px; border-collapse: collapse; margin-bottom: 22px;'>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; width: 130px; border-bottom: 1px solid #f1f5f9;'>Ticket ID</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 600; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(ticket.getTicketNumber()) + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Priority</td>"
                + "              <td style='padding: 8px 12px; border-bottom: 1px solid #f1f5f9;'>"
                + "                <span style='display: inline-block; font-size: 12px; font-weight: 600; background-color: " + prioBadgeBg + "; color: " + prioBadgeColor + "; padding: 2px 8px; border-radius: 4px;'>" + prioText + "</span>"
                + "              </td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Category</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + ticket.getCategory() + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Status</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + ticket.getStatus() + "</td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Requester</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(ticket.getRequesterName()) + " (" + escapeHtml(ticket.getRequesterEmail()) + ")</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Department</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(ticket.getRequesterDepartment() != null ? ticket.getRequesterDepartment() : "General") + "</td>"
                + "            </tr>"
                + assetRow.toString()
                + "          </table>"
                + "          <div style='background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 4px; padding: 14px 16px; margin-bottom: 24px;'>"
                + "            <div style='font-size: 12px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; color: #64748b; margin-bottom: 6px;'>Reported Issue</div>"
                + "            <div style='font-size: 14px; font-weight: 600; color: #0f172a; margin-bottom: 8px;'>" + escapeHtml(ticket.getTitle()) + "</div>"
                + "            <div style='font-size: 13px; color: #334155; line-height: 1.6; white-space: pre-wrap;'>" + escapeHtml(ticket.getDescription() != null ? ticket.getDescription() : "No details provided.") + "</div>"
                + "          </div>"
                + "          <table border='0' cellspacing='0' cellpadding='0' style='margin-bottom: 16px;'>"
                + "            <tr>"
                + "              <td>"
                + "                <a href='" + escapeHtml(dashboardUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 14px; font-weight: 600; border-radius: 4px;'>View in Helpdesk</a>"
                + "              </td>"
                + "              <td style='padding-left: 10px;'>"
                + "                <a href='" + replyMailto + "' style='display: inline-block; background-color: #ffffff; color: #334155; border: 1px solid #cbd5e1; text-decoration: none; padding: 9px 18px; font-size: 14px; font-weight: 500; border-radius: 4px;'>Reply to Requester</a>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 16px 32px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; line-height: 1.5;'>"
                + "          <div style='font-weight: 500; color: #475569;'>Aegis IT Asset Management &bull; BM Consulting IT Support</div>"
                + "          <div style='margin-top: 3px;'>This is an automated operational notification. Please triage via the helpdesk dashboard.</div>"
                + "        </td></tr>"
                + "      </table>"
                + "    </td></tr>"
                + "  </table>"
                + "</body></html>";
    }

    private String buildTicketEmailHtml(Ticket ticket, String portalUrl, String headerTitle, String bodyMessage) {
        StringBuilder assetRow = new StringBuilder();
        if (ticket.getAsset() != null) {
            Asset a = ticket.getAsset();
            assetRow.append("<tr>")
                    .append("<td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Attached Device</td>")
                    .append("<td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 500; border-bottom: 1px solid #f1f5f9;'>")
                    .append(escapeHtml(a.getName()))
                    .append(" (Tag: <span style='font-family: monospace; color: #2563eb;'>").append(escapeHtml(a.getAssetTag())).append("</span>)")
                    .append("</td></tr>");
        }

        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1.0'></head>"
                + "<body style='margin: 0; padding: 24px 0; background-color: #f4f6f8; font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, sans-serif; -webkit-font-smoothing: antialiased;'>"
                + "  <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "    <tr><td align='center'>"
                + "      <table width='600' border='0' cellspacing='0' cellpadding='0' style='max-width: 600px; width: 100%; background-color: #ffffff; border: 1px solid #e2e8f0; border-radius: 6px; overflow: hidden;'>"
                + "        <tr><td style='padding: 24px 32px 18px 32px; border-bottom: 2px solid #2563eb; background-color: #ffffff;'>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "            <tr>"
                + "              <td>"
                + "                <div style='font-size: 17px; font-weight: 700; color: #0f172a;'>Aegis IT Asset Management</div>"
                + "                <div style='font-size: 13px; color: #64748b; margin-top: 2px;'>IT Helpdesk &bull; " + escapeHtml(headerTitle) + "</div>"
                + "              </td>"
                + "              <td align='right'>"
                + "                <span style='display: inline-block; font-family: monospace; font-size: 13px; font-weight: 600; color: #0f172a; background-color: #f1f5f9; padding: 4px 10px; border-radius: 4px; border: 1px solid #e2e8f0;'>" + escapeHtml(ticket.getTicketNumber()) + "</span>"
                + "              </td>"
                + "            </tr>"
                + "          </table>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 28px 32px;'>"
                + "          <p style='margin: 0 0 12px 0; font-size: 15px; color: #0f172a;'><strong>Hello " + escapeHtml(ticket.getRequesterName()) + ",</strong></p>"
                + "          <p style='margin: 0 0 20px 0; font-size: 14px; color: #475569; line-height: 1.5;'>" + bodyMessage + "</p>"
                + "          <table width='100%' border='0' cellspacing='0' cellpadding='0' style='border: 1px solid #e2e8f0; border-radius: 4px; border-collapse: collapse; margin-bottom: 22px;'>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; width: 130px; border-bottom: 1px solid #f1f5f9;'>Ticket ID</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 600; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(ticket.getTicketNumber()) + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Subject</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 500; border-bottom: 1px solid #f1f5f9;'>" + escapeHtml(ticket.getTitle()) + "</td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Category</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + ticket.getCategory() + "</td>"
                + "            </tr>"
                + "            <tr>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Priority</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>" + ticket.getPriority() + "</td>"
                + "            </tr>"
                + "            <tr style='background-color: #f8fafc;'>"
                + "              <td style='padding: 8px 12px; color: #64748b; font-size: 13px; border-bottom: 1px solid #f1f5f9;'>Status</td>"
                + "              <td style='padding: 8px 12px; color: #0f172a; font-size: 13px; font-weight: 600; border-bottom: 1px solid #f1f5f9;'>" + ticket.getStatus() + "</td>"
                + "            </tr>"
                + assetRow.toString()
                + "          </table>"
                + "          <div style='margin-bottom: 16px;'>"
                + "            <a href='" + escapeHtml(portalUrl) + "' target='_blank' style='display: inline-block; background-color: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 22px; font-size: 14px; font-weight: 600; border-radius: 4px;'>View in Employee Portal</a>"
                + "          </div>"
                + "        </td></tr>"
                + "        <tr><td style='padding: 16px 32px; background-color: #f8fafc; border-top: 1px solid #e2e8f0; font-size: 12px; color: #64748b; line-height: 1.5;'>"
                + "          <div style='font-weight: 500; color: #475569;'>Aegis IT Asset Management &bull; BM Consulting IT Support</div>"
                + "          <div style='margin-top: 3px;'>You received this email because a ticket was submitted with your organization account.</div>"
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
