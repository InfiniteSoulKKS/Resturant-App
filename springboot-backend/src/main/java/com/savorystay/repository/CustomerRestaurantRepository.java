package com.savorystay.repository;

import com.savorystay.entity.CustomerRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRestaurantRepository extends JpaRepository<CustomerRestaurant, String> {

    List<CustomerRestaurant> findByCustomerIdOrderByJoinedAtDesc(String customerId);

    List<CustomerRestaurant> findByRestaurantIdOrderByJoinedAtDesc(String restaurantId);

    Optional<CustomerRestaurant> findByCustomerIdAndRestaurantId(String customerId, String restaurantId);

    boolean existsByCustomerIdAndRestaurantId(String customerId, String restaurantId);

    long countByRestaurantId(String restaurantId);
}
