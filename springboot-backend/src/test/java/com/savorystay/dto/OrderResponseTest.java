package com.savorystay.dto;

import com.savorystay.entity.Order;
import com.savorystay.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OrderResponse} item inclusion.
 *
 * The kitchen dashboard and customer tracking both render per-order line items,
 * so every response variant must carry them: {@code from(order, items)} maps
 * the items, and the single-arg overload (used where items are unavailable)
 * degrades to an empty list rather than omitting the field.
 */
class OrderResponseTest {

    private Order order(String id) {
        return Order.builder()
                .id(id)
                .orderNumber("#ORD-TEST")
                .restaurantId("REST_1")
                .orderType("PICKUP")
                .customerName("QA Customer")
                .totalAmount(new BigDecimal("840.00"))
                .paymentStatus("PAID")
                .orderStatus("NEW")
                .build();
    }

    private OrderItem item(String id, String title, int qty, String price) {
        return OrderItem.builder()
                .id(id)
                .orderId("ORD_1")
                .menuItemId("MI_1")
                .title(title)
                .quantity(qty)
                .unitPrice(new BigDecimal(price))
                .notes("no onion")
                .build();
    }

    @Test
    void fromWithItemsMapsEveryLineItem() {
        List<OrderItem> items = List.of(
                item("OI_1", "Butter Chicken", 2, "450.00"),
                item("OI_2", "Garlic Naan", 3, "80.00"));

        OrderResponse response = OrderResponse.from(order("ORD_1"), items);

        assertEquals(2, response.items().size());

        OrderItemResponse first = response.items().get(0);
        assertEquals("OI_1", first.id());
        assertEquals("ORD_1", first.orderId());
        assertEquals("MI_1", first.menuItemId());
        assertEquals("Butter Chicken", first.title());
        assertEquals(2, first.quantity());
        assertEquals(0, new BigDecimal("450.00").compareTo(first.unitPrice()));
        assertEquals("no onion", first.notes());

        assertEquals("Garlic Naan", response.items().get(1).title());
        assertEquals(3, response.items().get(1).quantity());
    }

    @Test
    void fromWithoutItemsYieldsEmptyList() {
        OrderResponse response = OrderResponse.from(order("ORD_1"));
        assertTrue(response.items().isEmpty());
    }

    @Test
    void fromWithNullItemsYieldsEmptyList() {
        OrderResponse response = OrderResponse.from(order("ORD_1"), null);
        assertTrue(response.items().isEmpty());
    }

    @Test
    void orderFieldsArePreservedAlongsideItems() {
        OrderResponse response = OrderResponse.from(order("ORD_1"), List.of(item("OI_1", "Chai", 1, "40.00")));

        assertEquals("ORD_1", response.id());
        assertEquals("#ORD-TEST", response.orderNumber());
        assertEquals("REST_1", response.restaurantId());
        assertEquals("PICKUP", response.orderType());
        assertEquals("QA Customer", response.customerName());
        assertEquals(0, new BigDecimal("840.00").compareTo(response.totalAmount()));
        assertEquals("PAID", response.paymentStatus());
        assertEquals("NEW", response.orderStatus());
    }
}
