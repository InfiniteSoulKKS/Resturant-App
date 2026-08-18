package com.savorystay.service;

import com.savorystay.entity.OtpRequest;
import com.savorystay.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpService {

    private final OtpRequestRepository otpRequestRepository;
    private final ChannelDeliveryService deliveryService;
    private final OutboxService outboxService;

    @Value("${app.name}")
    private String appName;

    private static final Random RANDOM = new Random();
    private static final int OTP_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;

    /**
     * Demo mode: when Twilio / SMTP credentials are not configured, the OTP is
     * logged to the console and returned in the API response (demoOtp) instead of
     * being delivered through a real provider. Once real credentials are set,
     * delivery switches to the live provider automatically.
     */
    public boolean isDemoDelivery(OtpRequest.OtpChannel channel) {
        return switch (channel) {
            case EMAIL -> !deliveryService.isMailConfigured();
            case SMS, WHATSAPP -> !deliveryService.isTwilioConfigured();
        };
    }

    /**
     * Generate and queue an OTP for delivery. Login flows pass {@code purpose = LOGIN} —
     * those OTPs are the only ones accepted by /login-with-otp. Registration-style sends
     * default to REGISTRATION and can never be used to log in.
     *
     * Delivery is event-driven: the OTP row and an {@code otp.generated} outbox event are
     * written in the SAME transaction, the {@code OutboxPoller} publishes it to the Kafka
     * {@code savorystay.otp} topic, and {@code OtpNotificationConsumer} sends the real
     * email (Gmail), SMS or WhatsApp. In demo mode (no SMTP/Twilio credentials) the code
     * is still surfaced via {@code demoOtp} in the API response.
     */
    @Transactional
    public OtpRequest generateAndSendOtp(String userId, String deliveryTarget, OtpRequest.OtpChannel channel,
                                         OtpRequest.OtpPurpose purpose) {
        // The storage key and the delivery target are the same unless the caller
        // passes a canonical key (e.g. the registered contact for a login send)
        // that differs only in formatting from what the user typed.
        try {
            // Generate 6-digit OTP
            String otpCode = generateOtp();

            // Create OTP request
            OtpRequest otpRequest = OtpRequest.builder()
                    .userId(userId)
                    .otpCode(otpCode)
                    .channel(channel)
                    .status(OtpRequest.OtpStatus.PENDING)
                    .purpose(purpose == null ? OtpRequest.OtpPurpose.REGISTRATION : purpose)
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .attemptCount(0)
                    .build();

            otpRequest = otpRequestRepository.save(otpRequest);

            // Transactional outbox: the delivery event commits atomically with the OTP row.
            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("otpId", otpRequest.getId());
            eventPayload.put("userId", userId);
            eventPayload.put("deliveryTarget", deliveryTarget);
            eventPayload.put("channel", channel.name());
            eventPayload.put("purpose", otpRequest.getPurpose() != null ? otpRequest.getPurpose().name() : "REGISTRATION");
            eventPayload.put("otpCode", otpCode);
            eventPayload.put("expiresAt", otpRequest.getExpiresAt().toString());
            eventPayload.put("appName", appName);
            outboxService.record(String.valueOf(otpRequest.getId()), "otp.generated", eventPayload);

            log.info("OTP queued for delivery via {} to {} {}", channel, deliveryTarget,
                    isDemoDelivery(channel) ? "(DEMO MODE - see response demoOtp)" : "");
            return otpRequest;

        } catch (Exception e) {
            log.error("Failed to queue OTP via {}: {}", channel, e.getMessage(), e);
            throw new RuntimeException("Failed to send OTP: " + e.getMessage());
        }
    }

    /**
     * Verify an OTP against any of the candidate identifiers a user is known by
     * (DB id, email, or phone) — OTPs are stored keyed by the delivery target.
     */
    /**
     * Login-only verification: accepts a code ONLY if it was issued for LOGIN
     * (i.e. sent with a validated username to the account's registered contact).
     * Registration-purpose OTPs — including codes sent to the same address without
     * a username — can never be used to log in.
     */
    public boolean verifyLoginOtp(List<String> candidateIds, String otpCode, OtpRequest.OtpChannel channel) {
        for (String id : candidateIds) {
            if (id == null || id.isBlank()) continue;
            Optional<OtpRequest> otpOptional = otpRequestRepository
                    .findTopByUserIdAndChannelAndStatusOrderByIdDesc(
                            id, channel, OtpRequest.OtpStatus.PENDING);
            if (otpOptional.isEmpty()) continue;
            OtpRequest otp = otpOptional.get();
            if (otp.getPurpose() != OtpRequest.OtpPurpose.LOGIN) continue; // not a login-issued code
            if (verifyOtp(id, otpCode, channel)) return true;
        }
        return false;
    }

    public boolean verifyOtp(String userId, String otpCode, OtpRequest.OtpChannel channel) {
        try {
            // Look up the newest PENDING OTP for this user + channel, then compare
            // the submitted code in Java so failed attempts actually count toward
            // the MAX_ATTEMPTS throttle (filtering by code in SQL would skip
            // incrementing on wrong entries).
            Optional<OtpRequest> otpOptional = otpRequestRepository
                    .findTopByUserIdAndChannelAndStatusOrderByIdDesc(userId, channel, OtpRequest.OtpStatus.PENDING);

            if (otpOptional.isEmpty()) {
                log.warn("OTP verification failed: No pending OTP for user {} via {}", userId, channel);
                return false;
            }

            OtpRequest otp = otpOptional.get();

            // Check if OTP is expired
            if (otp.isExpired()) {
                otp.setStatus(OtpRequest.OtpStatus.EXPIRED);
                otpRequestRepository.save(otp);
                log.warn("OTP verification failed: OTP expired for user {} via {}", userId, channel);
                return false;
            }

            // Check if too many attempts
            if (otp.getAttemptCount() >= MAX_ATTEMPTS) {
                otp.setStatus(OtpRequest.OtpStatus.EXPIRED);
                otpRequestRepository.save(otp);
                log.warn("OTP verification failed: Max attempts exceeded for user {} via {}", userId, channel);
                return false;
            }

            // Compare the submitted code
            if (otp.getOtpCode().equals(otpCode)) {
                otp.setStatus(OtpRequest.OtpStatus.VERIFIED);
                otp.setVerifiedAt(LocalDateTime.now());
                otpRequestRepository.save(otp);
                log.info("OTP verified successfully for user {} via {}", userId, channel);
                return true;
            } else {
                // Increment attempt count
                otp.setAttemptCount(otp.getAttemptCount() + 1);
                otpRequestRepository.save(otp);
                log.warn("OTP verification failed: Invalid code attempt {} for user {} via {}",
                        otp.getAttemptCount(), userId, channel);
                return false;
            }

        } catch (Exception e) {
            log.error("Error verifying OTP: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * True if this contact has a VERIFIED OTP from the last few minutes.
     * Used by registration: the UI calls /verify (which consumes the code to
     * VERIFIED) before /register, so registration accepts either a still-pending
     * code (direct API callers) or a recently-verified one (UI flow).
     */
    public boolean hasRecentVerifiedOtp(String userId, OtpRequest.OtpChannel channel) {
        return otpRequestRepository
                .findTopByUserIdAndChannelAndStatusOrderByIdDesc(userId, channel, OtpRequest.OtpStatus.VERIFIED)
                .map(o -> o.getVerifiedAt() != null
                        && o.getVerifiedAt().isAfter(LocalDateTime.now().minusMinutes(10)))
                .orElse(false);
    }

    public Optional<OtpRequest> getLatestOtp(String userId, OtpRequest.OtpChannel channel) {
        return otpRequestRepository.findTopByUserIdAndChannelAndStatusOrderByCreatedAtDesc(
                userId, channel, OtpRequest.OtpStatus.PENDING);
    }

    @Transactional
    public void cleanupExpiredOtps() {
        try {
            otpRequestRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            log.info("Cleaned up expired OTPs");
        } catch (Exception e) {
            log.error("Error cleaning up expired OTPs: {}", e.getMessage(), e);
        }
    }

    // Private helper methods

    private String generateOtp() {
        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }

}
