package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    private String id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "order_type", nullable = false, length = 20)
    private String orderType; // PICKUP, DINE_IN, PRE_ORDER

    @Column(name = "table_number")
    private Integer tableNumber;

    @Column
    private Integer guests;

    // Slots carry human-readable labels from the UI (e.g. "Tomorrow 30 Mins (Ready by
    // 07:45 PM)") and ISO datetimes for pre-orders — keep them comfortably wide.
    @Column(name = "time_slot", length = 100)
    private String timeSlot;

    @Column(name = "pickup_time", length = 100)
    private String pickupTime;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "customer_email", length = 100)
    private String customerEmail;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_status", length = 20)
    private String paymentStatus; // PENDING, PAID, FAILED

    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // STRIPE, PAYPAL, UPI, CASH, MOCK

    @Column(name = "order_status", length = 20)
    private String orderStatus; // NEW, PREPARING, PACKED_READY, COMPLETED, DECLINED, CANCELLED

    /** Who/what cancelled/declined the order. Null if not cancelled/declined. */
    @Column(name = "cancelled_by", length = 64)
    private String cancelledBy;

    /** Reason for cancellation/decline. */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    /** When the order was cancelled/declined. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("ORD");
        if (orderNumber == null) orderNumber = "#ORD-" + IdGenerator.shortSuffix();
        if (orderType == null) orderType = "PICKUP";
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (paymentStatus == null) paymentStatus = "PENDING";
        if (orderStatus == null) orderStatus = "NEW";
    }
}
