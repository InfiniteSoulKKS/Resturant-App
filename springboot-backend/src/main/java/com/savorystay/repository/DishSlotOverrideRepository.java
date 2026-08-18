package com.savorystay.repository;

import com.savorystay.entity.DishSlotOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DishSlotOverrideRepository extends JpaRepository<DishSlotOverride, Long> {
    List<DishSlotOverride> findByMenuItemId(String menuItemId);
    List<DishSlotOverride> findByMenuItemIdIn(List<String> menuItemIds);
    List<DishSlotOverride> findByRestaurantIdAndTargetDateBetween(String restaurantId, LocalDate from, LocalDate to);
    Optional<DishSlotOverride> findByMenuItemIdAndTargetDate(String menuItemId, LocalDate targetDate);
    void deleteByMenuItemIdAndTargetDate(String menuItemId, LocalDate targetDate);
}
