package com.savorystay.repository;

import com.savorystay.entity.PreOrderSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PreOrderSettingsRepository extends JpaRepository<PreOrderSettings, String> {
    Optional<PreOrderSettings> findByRestaurantId(String restaurantId);
}
