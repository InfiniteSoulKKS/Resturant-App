package com.savorystay.repository;

import com.savorystay.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    List<OrderItem> findByOrderId(String orderId);
    List<OrderItem> findByOrderIdIn(List<String> orderIds);
    void deleteByOrderId(String orderId);

    /**
     * Total plates ordered for a specific menu item within a time window.
     * Used to enforce daily plate caps: sum(order_items.quantity) for orders
     * created between start and end.
     */
    @Query(value = "SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi " +
           "JOIN orders o ON oi.order_id = o.id WHERE oi.menu_item_id = :menuItemId " +
           "AND o.created_at >= :start AND o.created_at < :end " +
           "AND o.order_status NOT IN ('DECLINED', 'CANCELLED')", nativeQuery = true)
    long countPlatesOrderedForItem(@Param("menuItemId") String menuItemId,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);
}
