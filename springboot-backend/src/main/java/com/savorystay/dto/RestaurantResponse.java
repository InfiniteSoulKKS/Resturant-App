package com.savorystay.dto;

import com.savorystay.entity.Restaurant;

import java.time.LocalDateTime;

/** View model for a restaurant as exposed to clients (public browse + admin). */
public record RestaurantResponse(
        String id,
        String name,
        String slug,
        String description,
        String address,
        String city,
        String cuisine,
        String phone,
        String email,
        String logoUrl,
        String status,
        String currency,
        String ownerId,
        LocalDateTime createdAt) {

    public static RestaurantResponse from(Restaurant r) {
        return new RestaurantResponse(
                r.getId(),
                r.getName(),
                r.getSlug(),
                r.getDescription(),
                r.getAddress(),
                r.getCity(),
                r.getCuisine(),
                r.getPhone(),
                r.getEmail(),
                r.getLogoUrl(),
                r.getStatus(),
                r.getCurrency(),
                r.getOwnerId(),
                r.getCreatedAt());
    }
}
