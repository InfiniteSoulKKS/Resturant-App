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
     * Count active DINE_IN orders grouped by guest count for a restaurant on a
     * given date. Used to determine table availability per seating type.
     * Returns rows of [guests, count].
     * Time slots within 1 hour of the requested slot are also counted as booked
     * to prevent double-booking.
     */
    @Query(value = "SELECT o.guests AS guests, COUNT(*) AS cnt FROM orders o " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND o.order_type = 'DINE_IN' " +
           "AND o.order_status NOT IN ('DECLINED', 'CANCELLED', 'COMPLETED') " +
           "AND o.time_slot LIKE CONCAT(:datePrefix, '%') " +
           "GROUP BY o.guests", nativeQuery = true)
    List<Object[]> countDineInByGuestsOnDate(@Param("restaurantId") String restaurantId,
                                              @Param("datePrefix") String datePrefix);

    /**
     * Count active DINE_IN orders grouped by guest count for overlapping time windows.
     * Accepts a list of time slot prefixes (e.g., ["2026-08-22 12:00 PM", "2026-08-22 12:30 PM"])
     * to count bookings within a 1-hour window around the requested time.
     */
    @Query(value = "SELECT o.guests AS guests, COUNT(*) AS cnt FROM orders o " +
           "WHERE o.restaurant_id = :restaurantId " +
           "AND o.order_type = 'DINE_IN' " +
           "AND o.order_status NOT IN ('DECLINED', 'CANCELLED', 'COMPLETED') " +
           "AND o.time_slot IN :timeSlotPrefixes " +
           "GROUP BY o.guests", nativeQuery = true)
    List<Object[]> countDineInByTimeSlots(@Param("restaurantId") String restaurantId,
                                            @Param("timeSlotPrefixes") List<String> timeSlotPrefixes);
}
