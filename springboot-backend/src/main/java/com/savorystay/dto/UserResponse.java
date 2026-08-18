package com.savorystay.dto;

import com.savorystay.entity.User;

import java.time.LocalDateTime;

/**
 * View model for a user. The password hash never leaves the backend — this is
 * the only shape users are ever returned in.
 */
public record UserResponse(
        String id,
        String username,
        String email,
        String phone,
        String role,
        String restaurantId,
        Boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime lastLogin) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getEmail(),
                u.getPhone(),
                u.getRole(),
                u.getRestaurantId(),
                u.getEnabled(),
                u.getCreatedAt(),
                u.getLastLogin());
    }
}
