package com.savorystay.service;

import com.savorystay.dto.MailHealthResponse;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Real-time multi-channel delivery engine shared by OTP and order-status
 * notifications. Sends real SMS / WhatsApp via Twilio and email via Spring
 * Mail when credentials are configured. When credentials are missing or are
 * still the placeholder defaults, it falls back to DEMO MODE: the message is
 * logged and the OTP code is surfaced via the API response so local flows
 * remain testable end-to-end.
 */
@Slf4j
@Service
public class ChannelDeliveryService {

    private final JavaMailSender mailSender;

    @Value("${twilio.account-sid}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token}")
    private String twilioAuthToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    @Value("${twilio.whatsapp-number}")
    private String twilioWhatsappNumber;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.name}")
    private String appName;

    public ChannelDeliveryService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isTwilioConfigured() {
        return !isBlank(twilioAccountSid) && !isBlank(twilioAuthToken)
                && !"your_account_sid".equalsIgnoreCase(twilioAccountSid)
                && !"your_auth_token".equalsIgnoreCase(twilioAuthToken)
                && !isBlank(twilioPhoneNumber) && !isBlank(twilioWhatsappNumber)
                && !"+1234567890".equals(twilioPhoneNumber)
                && !"+14155238886".equals(twilioWhatsappNumber);
    }

    public boolean isMailConfigured() {
        return !isBlank(fromEmail)
                && !"your-email@gmail.com".equalsIgnoreCase(fromEmail)
                && !isBlank(mailPassword)
                && !"your-app-password".equalsIgnoreCase(mailPassword);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Send a real-time SMS. Returns false when no Twilio credentials (demo mode). */
    public boolean sendSms(String phoneNumber, String messageBody) {
        if (!isTwilioConfigured()) {
            log.info("[DEMO-MODE SMS] To {}: {}", phoneNumber, messageBody);
            return false;
        }
        try {
            Twilio.init(twilioAccountSid, twilioAuthToken);
            Message message = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(twilioPhoneNumber),
                    messageBody).create();
            log.info("SMS sent to {} (sid={})", phoneNumber, message.getSid());
            return true;
        } catch (Exception e) {
            log.error("SMS delivery failed to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("SMS delivery failed: " + e.getMessage());
        }
    }

    /** Send a real-time WhatsApp message. Returns false when no Twilio credentials (demo mode). */
    public boolean sendWhatsApp(String phoneNumber, String messageBody) {
        if (!isTwilioConfigured()) {
            log.info("[DEMO-MODE WHATSAPP] To {}: {}", phoneNumber, messageBody);
            return false;
        }
        try {
            Twilio.init(twilioAccountSid, twilioAuthToken);
            Message message = Message.creator(
                    new PhoneNumber("whatsapp:" + phoneNumber),
                    new PhoneNumber("whatsapp:" + twilioWhatsappNumber),
                    messageBody).create();
            log.info("WhatsApp sent to {} (sid={})", phoneNumber, message.getSid());
            return true;
        } catch (Exception e) {
            log.error("WhatsApp delivery failed to {}: {}", phoneNumber, e.getMessage(), e);
            throw new RuntimeException("WhatsApp delivery failed: " + e.getMessage());
        }
    }

    /** Send a real-time plain-text email. Returns false when no SMTP credentials (demo mode). */
    public boolean sendEmail(String email, String subject, String body) {
        if (!isMailConfigured()) {
            log.info("[DEMO-MODE EMAIL] To {}: [{}] {}", email, subject, body);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {}", email);
            return true;
        } catch (Exception e) {
            log.error("Email delivery failed to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage());
        }
    }

    /**
     * Send a real-time HTML email (branded templates from {@link EmailTemplateService}).
     * Returns false when no SMTP credentials are configured (demo mode logs it instead).
     */
    public boolean sendHtmlEmail(String email, String subject, String htmlBody) {
        if (!isMailConfigured()) {
            log.info("[DEMO-MODE EMAIL] To {}: [{}] {}", email, subject, stripHtml(htmlBody));
            return false;
        }
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(mime);
            log.info("HTML email sent to {}", email);
            return true;
        } catch (Exception e) {
            log.error("HTML email delivery failed to {}: {}", email, e.getMessage(), e);
            throw new RuntimeException("Email delivery failed: " + e.getMessage());
        }
    }

    /** Rough HTML-to-text fallback for demo-mode logs so they stay readable. */
    private String stripHtml(String html) {
        if (html == null) return "";
        String text = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    /**
     * On-demand SMTP connectivity check for {@code GET /api/v1/health/mail}.
     * Opens a real connection to the configured mail server and authenticates
     * (no message is sent), so it validates host/port/credentials exactly as a
     * delivery would. The password is never returned or logged.
     */
    public MailHealthResponse checkMailHealth() {
        if (!isMailConfigured()) {
            log.warn("[MAIL-HEALTH] SMTP not configured (username={})", fromEmail);
            return MailHealthResponse.notConfigured(fromEmail);
        }
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        String host = impl.getHost();
        int port = impl.getPort();
        long start = System.currentTimeMillis();
        try {
            impl.testConnection(); // connect + authenticate, then close
            long latencyMs = System.currentTimeMillis() - start;
            log.info("[MAIL-HEALTH] SMTP OK {}:{} as {} ({}ms)", host, port, fromEmail, latencyMs);
            return MailHealthResponse.up(host, port, fromEmail, latencyMs);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - start;
            String cause = sanitizeMailError(e);
            log.warn("[MAIL-HEALTH] SMTP check failed {}:{}: {}", host, port, cause);
            return MailHealthResponse.down(host, port, fromEmail, latencyMs, cause);
        }
    }

    /**
     * Keep error text user-safe: auth failures ("535 ... Access denied") are
     * meaningful to surface, but accidental secrets in messages are masked.
     */
    private String sanitizeMailError(Exception e) {
        String raw = e.getMessage();
        if (raw == null || raw.isBlank()) return e.getClass().getSimpleName();
        return raw.replaceAll("(?i)(password|passwd|pwd)[=: ]+\\S+", "$1=<hidden>");
    }

    public String getAppName() {
        return appName;
    }
}
