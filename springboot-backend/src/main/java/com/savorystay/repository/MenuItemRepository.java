package com.savorystay.repository;

import com.savorystay.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, String> {
    List<MenuItem> findByRestaurantIdOrderByCreatedAtDesc(String restaurantId);
    List<MenuItem> findByRestaurantIdAndStatusOrderByCreatedAtDesc(String restaurantId, String status);
    Optional<MenuItem> findByIdAndRestaurantId(String id, String restaurantId);
    long countByRestaurantId(String restaurantId);
}
