package com.savorystay.repository;

import com.savorystay.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, String> {
    List<Refund> findByOrderId(String orderId);
    Optional<Refund> findByPaymentIdAndRefundStatus(String paymentId, String refundStatus);
    List<Refund> findByRestaurantIdAndRefundStatus(String restaurantId, String refundStatus);
}
