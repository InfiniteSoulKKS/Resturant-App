package com.savorystay.repository;

import com.savorystay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    Boolean existsByPhone(String phone);
    List<User> findByRestaurantIdOrderByCreatedAtDesc(String restaurantId);
    List<User> findByRestaurantIdAndRole(String restaurantId, String role);
    Optional<User> findFirstByRole(String role);
}
