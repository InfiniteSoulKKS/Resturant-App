package com.savorystay.repository;

import com.savorystay.entity.DishAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DishAvailabilityRepository extends JpaRepository<DishAvailability, Long> {
    List<DishAvailability> findByRestaurantId(String restaurantId);
    List<DishAvailability> findByMenuItemId(String menuItemId);
    List<DishAvailability> findByMenuItemIdIn(List<String> menuItemIds);

    /**
     * Bulk delete — executes immediately. (A derived deleteBy... only queues
     * entity removals, which Hibernate flushes AFTER inserts — that re-inserting
     * the same (menu_item_id, day_of_week) pair in the same transaction would
     * violate the unique key. This must be used before re-saving availability.)
     */
    @Modifying
    @Query("DELETE FROM DishAvailability d WHERE d.menuItemId = :menuItemId")
    void deleteAllByMenuItemId(@Param("menuItemId") String menuItemId);
}
