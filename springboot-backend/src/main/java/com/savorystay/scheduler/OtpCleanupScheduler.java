package com.savorystay.scheduler;

import com.savorystay.service.OtpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OtpCleanupScheduler {

    private final OtpService otpService;

    /**
     * Clean up expired OTPs every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes in milliseconds
    public void cleanupExpiredOtps() {
        try {
            log.info("Starting scheduled cleanup of expired OTPs");
            otpService.cleanupExpiredOtps();
            log.info("Completed cleanup of expired OTPs");
        } catch (Exception e) {
            log.error("Error in OTP cleanup scheduler: {}", e.getMessage(), e);
        }
    }
}
