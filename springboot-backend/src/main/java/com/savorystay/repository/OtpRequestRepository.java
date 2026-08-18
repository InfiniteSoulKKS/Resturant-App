package com.savorystay.repository;

import com.savorystay.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {
    
    Optional<OtpRequest> findTopByUserIdAndChannelAndStatusOrderByCreatedAtDesc(
            String userId, OtpRequest.OtpChannel channel, OtpRequest.OtpStatus status);

    /**
     * Newest PENDING OTP for a user + channel — used to compare the submitted
     * code in Java so failed attempts actually count toward the throttle.
     */
    Optional<OtpRequest> findTopByUserIdAndChannelAndStatusOrderByIdDesc(
            String userId, OtpRequest.OtpChannel channel, OtpRequest.OtpStatus status);
    
    List<OtpRequest> findByUserIdAndChannel(String userId, OtpRequest.OtpChannel channel);
    
    List<OtpRequest> findByStatusAndExpiresAtBefore(OtpRequest.OtpStatus status, LocalDateTime dateTime);
    
    // Clean up expired OTPs
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
