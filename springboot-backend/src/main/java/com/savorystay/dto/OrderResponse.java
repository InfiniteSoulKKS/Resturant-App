package com.savorystay.dto;

import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** View model for an order (customer tracking + restaurant dashboard). */
public record OrderResponse(
        String id,
        String orderNumber,
        String restaurantId,
        String orderType,
        Integer tableNumber,
        Integer guests,
        String timeSlot,
        String pickupTime,
        String customerName,
        String customerPhone,
        String customerEmail,
        String userId,
        BigDecimal totalAmount,
        String paymentStatus,
        String paymentMethod,
        String orderStatus,
        LocalDateTime createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getRestaurantId(),
                o.getOrderType(),
                o.getTableNumber(),
                o.getGuests(),
                o.getTimeSlot(),
                o.getPickupTime(),
                o.getCustomerName(),
                o.getCustomerPhone(),
                o.getCustomerEmail(),
                o.getUserId(),
                o.getTotalAmount(),
                o.getPaymentStatus(),
                o.getPaymentMethod(),
                o.getOrderStatus(),
                o.getCreatedAt(),
                List.of());
    }

    /** Include the order's line items (kitchen dashboard / customer tracking need them). */
    public static OrderResponse from(Order o, List<OrderItem> items) {
        return new OrderResponse(
                o.getId(),
                o.getOrderNumber(),
                o.getRestaurantId(),
                o.getOrderType(),
                o.getTableNumber(),
                o.getGuests(),
                o.getTimeSlot(),
                o.getPickupTime(),
                o.getCustomerName(),
                o.getCustomerPhone(),
                o.getCustomerEmail(),
                o.getUserId(),
                o.getTotalAmount(),
                o.getPaymentStatus(),
                o.getPaymentMethod(),
                o.getOrderStatus(),
                o.getCreatedAt(),
                items == null ? List.of() : items.stream().map(OrderItemResponse::from).toList());
    }
}
