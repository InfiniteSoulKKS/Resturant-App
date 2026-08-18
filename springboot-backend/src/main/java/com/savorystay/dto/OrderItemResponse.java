package com.savorystay.dto;

import com.savorystay.entity.OrderItem;

import java.math.BigDecimal;

/** View model for a single line item inside an order. */
public record OrderItemResponse(
        String id,
        String orderId,
        String menuItemId,
        String title,
        Integer quantity,
        BigDecimal unitPrice,
        String notes) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getOrderId(),
                item.getMenuItemId(),
                item.getTitle(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getNotes());
    }
}
