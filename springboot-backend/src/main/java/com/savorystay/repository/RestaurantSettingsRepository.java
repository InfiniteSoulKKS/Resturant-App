package com.savorystay.repository;

import com.savorystay.entity.RestaurantSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RestaurantSettingsRepository extends JpaRepository<RestaurantSettings, String> {
    Optional<RestaurantSettings> findByRestaurantId(String restaurantId);
}
