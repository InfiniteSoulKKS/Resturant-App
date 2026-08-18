package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userId; // Email, Phone, or User ID

    @Column(nullable = false)
    private String otpCode; // 6-digit OTP

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpChannel channel; // EMAIL, SMS, WHATSAPP

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpStatus status; // PENDING, VERIFIED, EXPIRED

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // OTP expiry time (typically 5-10 mins)

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "attempt_count", columnDefinition = "INT DEFAULT 0")
    private Integer attemptCount; // Track failed attempts

    /**
     * What this OTP was issued for. LOGIN OTPs are only issued to the account's
     * registered contact for the chosen channel (validated at send time) and are
     * the only codes /login-with-otp accepts. REGISTRATION OTPs are issued to a
     * new (not yet existing) account's contact and can never be used to log in.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'REGISTRATION'")
    private OtpPurpose purpose = OtpPurpose.REGISTRATION;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = OtpStatus.PENDING;
        if (attemptCount == null) attemptCount = 0;
        if (purpose == null) purpose = OtpPurpose.REGISTRATION;
        if (expiresAt == null) expiresAt = LocalDateTime.now().plusMinutes(5); // 5-minute expiry
    }

    public enum OtpChannel {
        EMAIL, SMS, WHATSAPP
    }

    public enum OtpPurpose {
        LOGIN, REGISTRATION
    }

    public enum OtpStatus {
        PENDING, VERIFIED, EXPIRED
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isPending() {
        return status == OtpStatus.PENDING && !isExpired();
    }
}
