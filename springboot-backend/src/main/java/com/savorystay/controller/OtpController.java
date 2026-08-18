package com.savorystay.controller;

import com.savorystay.entity.OtpRequest;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.service.OtpService;
import com.savorystay.service.AuthRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/otp")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;
    private final AuthRateLimitService rateLimitService;
    private final UserRepository userRepository;

    /**
     * For LOGIN OTP sends the frontend passes a {@code username}; when present, the
     * account must exist and the delivery target must match the registered contact.
     * Registration sends (no username) are unaffected. Returns null when OK.
     */
    private ResponseEntity<?> validateLoginTarget(Map<String, String> request, String target, String field) {
        String username = request.get("username");
        if (username == null || username.isBlank()) {
            return null; // registration-style send — no account validation
        }
        Optional<User> user = userRepository.findByUsername(username.trim());
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "No account found with this username."));
        }
        if ("email".equals(field)) {
            String registered = user.get().getEmail();
            if (registered == null || !registered.trim().equalsIgnoreCase(target.trim())) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "This email is not registered to this account."));
            }
        } else {
            if (!phonesMatch(user.get().getPhone(), target)) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "This phone number is not registered to this account."));
            }
        }
        return null;
    }

    /** Strip everything that is not a digit so formats like "+91 99990 00002" match "+919999000002". */
    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * Phone comparison tolerant of formatting/country-code differences. The typed
     * number must be the registered number exactly, or a genuine local form of it
     * (registered WITH country code ending in the typed local number). Comparing
     * only trailing digits would let a different country-code variant with the
     * same last 10 digits pass and then log in as the account.
     */
    private static boolean phonesMatch(String registered, String typed) {
        String r = digitsOnly(registered);
        String t = digitsOnly(typed);
        if (r.isEmpty() || t.isEmpty()) return false;
        return r.equals(t) || (t.length() >= 10 && r.length() > t.length() && r.endsWith(t));
    }

    /**
     * For login sends the OTP is keyed by the account's REGISTERED contact so
     * verification finds it via the user's identifiers regardless of how the user
     * typed it. Returns null when there is no username (registration-style send).
     */
    private String registeredContactKey(Map<String, String> request, String field) {
        String username = request.get("username");
        if (username == null || username.isBlank()) return null;
        User user = userRepository.findByUsername(username.trim()).orElse(null);
        if (user == null) return null;
        return "email".equals(field) ? user.getEmail() : user.getPhone();
    }

    /**
     * Send OTP to Email
     * Request body: { "userId": "user@example.com", "username": "optional (login flows)" }
     */
    @PostMapping("/send/email")
    public ResponseEntity<?> sendOtpViaEmail(@RequestBody Map<String, String> request,
                                             HttpServletRequest httpRequest) {
        String email = request.get("email");
        String ip = AuthRateLimitService.clientIp(httpRequest);
        rateLimitService.throwIfOtpSendExceeded(email, ip); // 429 via GlobalExceptionHandler

        try {
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Email is required"));
            }
            ResponseEntity<?> loginCheck = validateLoginTarget(request, email, "email");
            if (loginCheck != null) return loginCheck;

            // Login flows key the OTP by the account's REGISTERED contact (canonical
            // form) so verification finds it via the user's identifiers, while still
            // delivering the code to whatever the user typed.
            String key = registeredContactKey(request, "email");
            if (key == null) key = email;

            OtpRequest.OtpPurpose purpose = request.containsKey("username") && !request.get("username").isBlank()
                    ? OtpRequest.OtpPurpose.LOGIN : OtpRequest.OtpPurpose.REGISTRATION;
            OtpRequest otpRequest = otpService.generateAndSendOtp(key, email, OtpRequest.OtpChannel.EMAIL, purpose);
            rateLimitService.recordOtpSend(email, ip);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "OTP sent to email successfully");
            response.put("otpId", otpRequest.getId());
            response.put("expiresIn", "5 minutes");
            if (otpService.isDemoDelivery(OtpRequest.OtpChannel.EMAIL)) {
                response.put("demoOtp", otpRequest.getOtpCode());
                response.put("demoMode", true);
                response.put("message", "OTP sent to email (DEMO MODE - no SMTP credentials configured). Code: " + otpRequest.getOtpCode());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending email OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Send OTP to Mobile (SMS)
     * Request body: { "phone": "+919876543210" }
     */
    @PostMapping("/send/sms")
    public ResponseEntity<?> sendOtpViaSMS(@RequestBody Map<String, String> request,
                                           HttpServletRequest httpRequest) {
        String phone = request.get("phone");
        String ip = AuthRateLimitService.clientIp(httpRequest);
        rateLimitService.throwIfOtpSendExceeded(phone, ip); // 429 via GlobalExceptionHandler

        try {
            if (phone == null || phone.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Phone number is required"));
            }

            // Validate phone format (basic validation). Strip formatting first so
            // "+91 99990 00002" / "+91-99990-00002" are accepted like "+919999000002".
            String digits = digitsOnly(phone);
            if (digits.length() < 10 || digits.length() > 15) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Invalid phone number format. Use +country_code_number"));
            }
            ResponseEntity<?> loginCheck = validateLoginTarget(request, phone, "phone");
            if (loginCheck != null) return loginCheck;

            // Login flows key the OTP by the account's REGISTERED phone (canonical
            // form) so verification finds it via the user's identifiers, while still
            // delivering the code to whatever the user typed.
            String key = registeredContactKey(request, "phone");
            if (key == null) key = phone;

            OtpRequest.OtpPurpose purpose = request.containsKey("username") && !request.get("username").isBlank()
                    ? OtpRequest.OtpPurpose.LOGIN : OtpRequest.OtpPurpose.REGISTRATION;
            OtpRequest otpRequest = otpService.generateAndSendOtp(key, phone, OtpRequest.OtpChannel.SMS, purpose);
            rateLimitService.recordOtpSend(phone, ip);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "OTP sent via SMS successfully");
            response.put("otpId", otpRequest.getId());
            response.put("expiresIn", "5 minutes");
            if (otpService.isDemoDelivery(OtpRequest.OtpChannel.SMS)) {
                response.put("demoOtp", otpRequest.getOtpCode());
                response.put("demoMode", true);
                response.put("message", "OTP sent via SMS (DEMO MODE - no Twilio credentials configured). Code: " + otpRequest.getOtpCode());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending SMS OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Send OTP via WhatsApp
     * Request body: { "phone": "+919876543210" }
     */
    @PostMapping("/send/whatsapp")
    public ResponseEntity<?> sendOtpViaWhatsApp(@RequestBody Map<String, String> request,
                                                HttpServletRequest httpRequest) {
        String phone = request.get("phone");
        String ip = AuthRateLimitService.clientIp(httpRequest);
        rateLimitService.throwIfOtpSendExceeded(phone, ip); // 429 via GlobalExceptionHandler

        try {
            if (phone == null || phone.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Phone number is required"));
            }

            // Validate phone format (basic validation). Strip formatting first so
            // "+91 99990 00002" / "+91-99990-00002" are accepted like "+919999000002".
            String digits = digitsOnly(phone);
            if (digits.length() < 10 || digits.length() > 15) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Invalid phone number format. Use +country_code_number"));
            }
            ResponseEntity<?> loginCheck = validateLoginTarget(request, phone, "phone");
            if (loginCheck != null) return loginCheck;

            // Login flows key the OTP by the account's REGISTERED phone (canonical
            // form) so verification finds it via the user's identifiers, while still
            // delivering the code to whatever the user typed.
            String key = registeredContactKey(request, "phone");
            if (key == null) key = phone;

            OtpRequest.OtpPurpose purpose = request.containsKey("username") && !request.get("username").isBlank()
                    ? OtpRequest.OtpPurpose.LOGIN : OtpRequest.OtpPurpose.REGISTRATION;
            OtpRequest otpRequest = otpService.generateAndSendOtp(key, phone, OtpRequest.OtpChannel.WHATSAPP, purpose);
            rateLimitService.recordOtpSend(phone, ip);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "OTP sent via WhatsApp successfully");
            response.put("otpId", otpRequest.getId());
            response.put("expiresIn", "5 minutes");
            if (otpService.isDemoDelivery(OtpRequest.OtpChannel.WHATSAPP)) {
                response.put("demoOtp", otpRequest.getOtpCode());
                response.put("demoMode", true);
                response.put("message", "OTP sent via WhatsApp (DEMO MODE - no Twilio credentials configured). Code: " + otpRequest.getOtpCode());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending WhatsApp OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Failed to send OTP: " + e.getMessage()));
        }
    }

    /**
     * Verify OTP
     * Request body: { "userId": "user_id_or_email_or_phone", "otpCode": "123456", "channel": "EMAIL|SMS|WHATSAPP" }
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request,
                                       HttpServletRequest httpRequest) {
        String userId = request.get("userId");
        String ip = AuthRateLimitService.clientIp(httpRequest);
        rateLimitService.throwIfOtpVerifyExceeded(userId, ip); // 429 via GlobalExceptionHandler

        try {
            String otpCode = request.get("otpCode");
            String channelStr = request.get("channel");

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "User ID is required"));
            }

            if (otpCode == null || otpCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "OTP code is required"));
            }

            if (channelStr == null || channelStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Channel (EMAIL, SMS, WHATSAPP) is required"));
            }

            // Parse channel
            OtpRequest.OtpChannel channel;
            try {
                channel = OtpRequest.OtpChannel.valueOf(channelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Invalid channel. Use EMAIL, SMS, or WHATSAPP"));
            }

            rateLimitService.recordOtpVerify(userId, ip);
            boolean verified = otpService.verifyOtp(userId, otpCode, channel);

            if (verified) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "OTP verified successfully");
                response.put("verified", true);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Invalid or expired OTP");
                response.put("verified", false);
                return ResponseEntity.status(401).body(response);
            }

        } catch (Exception e) {
            log.error("Error verifying OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Failed to verify OTP: " + e.getMessage()));
        }
    }

    /**
     * Resend OTP to same channel
     * Request body: { "userId": "user_id_or_email_or_phone", "channel": "EMAIL|SMS|WHATSAPP" }
     */
    @PostMapping("/resend")
    public ResponseEntity<?> resendOtp(@RequestBody Map<String, String> request,
                                       HttpServletRequest httpRequest) {
        String userId = request.get("userId");
        String ip = AuthRateLimitService.clientIp(httpRequest);
        rateLimitService.throwIfOtpSendExceeded(userId, ip); // 429 via GlobalExceptionHandler

        try {
            String channelStr = request.get("channel");

            if (userId == null || userId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "User ID is required"));
            }

            if (channelStr == null || channelStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Channel (EMAIL, SMS, WHATSAPP) is required"));
            }

            OtpRequest.OtpChannel channel;
            try {
                channel = OtpRequest.OtpChannel.valueOf(channelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Invalid channel. Use EMAIL, SMS, or WHATSAPP"));
            }

            // Keep resend consistent with send: for login flows (username present)
            // key the OTP by the account's REGISTERED contact.
            String key = registeredContactKey(request, channel == OtpRequest.OtpChannel.EMAIL ? "email" : "phone");
            if (key == null) key = userId;

            OtpRequest.OtpPurpose purpose = request.containsKey("username") && !request.get("username").isBlank()
                    ? OtpRequest.OtpPurpose.LOGIN : OtpRequest.OtpPurpose.REGISTRATION;
            OtpRequest otpRequest = otpService.generateAndSendOtp(key, userId, channel, purpose);
            rateLimitService.recordOtpSend(userId, ip);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "OTP resent successfully via " + channel);
            response.put("otpId", otpRequest.getId());
            response.put("expiresIn", "5 minutes");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error resending OTP: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(
                    Map.of("success", false, "message", "Failed to resend OTP: " + e.getMessage()));
        }
    }
}
