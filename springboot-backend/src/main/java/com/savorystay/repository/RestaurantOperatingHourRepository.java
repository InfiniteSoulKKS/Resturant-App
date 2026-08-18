package com.savorystay.repository;

import com.savorystay.entity.RestaurantOperatingHour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantOperatingHourRepository extends JpaRepository<RestaurantOperatingHour, Long> {
    List<RestaurantOperatingHour> findByRestaurantId(String restaurantId);
    Optional<RestaurantOperatingHour> findByRestaurantIdAndDayOfWeek(String restaurantId, Integer dayOfWeek);
}
