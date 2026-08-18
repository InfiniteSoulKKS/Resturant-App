package com.savorystay.repository;

import com.savorystay.entity.Restaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, String> {
    Optional<Restaurant> findBySlug(String slug);
    Optional<Restaurant> findByName(String name);
    List<Restaurant> findByStatusOrderByCreatedAtDesc(String status);
    List<Restaurant> findAllByOrderByCreatedAtDesc();
}
