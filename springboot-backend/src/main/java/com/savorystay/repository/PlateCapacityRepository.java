package com.savorystay.repository;

import com.savorystay.entity.PlateCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlateCapacityRepository extends JpaRepository<PlateCapacity, Long> {

    Optional<PlateCapacity> findByMenuItemIdAndBusinessDate(String menuItemId, LocalDate businessDate);

    Optional<PlateCapacity> findByMenuItemIdAndRestaurantIdAndBusinessDate(
            String menuItemId, String restaurantId, LocalDate businessDate);

    List<PlateCapacity> findByRestaurantIdAndBusinessDate(String restaurantId, LocalDate businessDate);

    /**
     * Find or create a plate capacity record. Used by the atomic reservation
     * flow. The SELECT FOR UPDATE pattern ensures that concurrent requests
     * serialize on this row.
     */
    @Query(value = "SELECT * FROM plate_capacity pc " +
           "WHERE pc.menu_item_id = :menuItemId AND pc.business_date = :businessDate " +
           "FOR UPDATE", nativeQuery = true)
    Optional<PlateCapacity> findByMenuItemIdAndBusinessDateForUpdate(
            @Param("menuItemId") String menuItemId,
            @Param("businessDate") LocalDate businessDate);
}
