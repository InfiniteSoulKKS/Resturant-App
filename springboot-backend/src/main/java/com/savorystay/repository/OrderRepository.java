package com.savorystay.repository;

import com.savorystay.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    Optional<Order> findByOrderNumber(String orderNumber);
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);
    List<Order> findByRestaurantIdOrderByCreatedAtDesc(String restaurantId);
    List<Order> findByRestaurantIdAndOrderStatusOrderByCreatedAtDesc(String restaurantId, String orderStatus);
    Optional<Order> findByIdAndRestaurantId(String id, String restaurantId);
    Optional<Order> findByIdAndUserId(String id, String userId);

    /**
     * Orders relevant to a forecast date: pre-orders scheduled to be fulfilled
     * on that date (keyed by pickup_time), plus any orders placed that day.
     * Used to calculate next-day ingredient requirements from pre-order volume.
     */
    @Query("SELECT o FROM Order o WHERE o.restaurantId = :restaurantId " +
           "AND o.orderStatus NOT IN ('DECLINED', 'CANCELLED') " +
           "AND (" +
           "  (o.orderType = 'PRE_ORDER' AND o.pickupTime LIKE CONCAT(:dateStr, '%')) " +
           "  OR (o.createdAt BETWEEN :from AND :to)" +
           ") ORDER BY o.createdAt DESC")
    List<Order> findActiveOrdersBetween(@Param("restaurantId") String restaurantId,
                                        @Param("dateStr") String dateStr,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    /**
     * Count active DINE_IN orders grouped by guest count for a restaurant.
     * Returns rows of [guests, count].
     * DINE_IN time_slot is stored as just the time (e.g. "12:00 PM"), not datetime.
     */
    @Query(value = "SELECT o.guests AS guests, COUNT(*) AS cnt FROM orders o " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND o.order_type = 'DINE_IN' " +
           "AND o.order_status NOT IN ('DECLINED', 'CANCELLED', 'COMPLETED') " +
           "AND o.time_slot = :timeSlot " +
           "GROUP BY o.guests", nativeQuery = true)
    List<Object[]> countDineInByGuestsOnDate(@Param("restaurantId") String restaurantId,
                                              @Param("timeSlot") String timeSlot);

    /**
     * Count active DINE_IN orders for TODAY grouped by guest count.
     * Only counts orders placed today (based on created_at) for accurate daily availability.
     * Matches both 12h ("12:00 PM") and 24h ("13:00") time slot formats.
     */
    @Query(value = "SELECT o.guests AS guests, COUNT(*) AS cnt FROM orders o " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND o.order_type = 'DINE_IN' " +
           "AND o.order_status NOT IN ('DECLINED', 'CANCELLED', 'COMPLETED') " +
           "AND DATE(o.created_at) = :orderDate " +
           "AND ( o.time_slot IN :timeSlots OR o.time_slot IN :timeSlots24h ) " +
           "GROUP BY o.guests", nativeQuery = true)
    List<Object[]> countDineInByTimeSlots(@Param("restaurantId") String restaurantId,
                                            @Param("orderDate") String orderDate,
                                            @Param("timeSlots") List<String> timeSlots,
                                            @Param("timeSlots24h") List<String> timeSlots24h);
}
