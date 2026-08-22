package com.savorystay.dto;

import com.savorystay.entity.MenuItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MenuItemResponse(
        String id,
        String restaurantId,
        String title,
        String description,
        BigDecimal price,
        String category,
        String imageUrl,
        String status,
        Boolean isVeg,
        String spiceLevel,
        Integer prepMinutes,
        Integer dailyPlateCount,
        LocalDateTime createdAt) {

    public static MenuItemResponse from(MenuItem m) {
        return new MenuItemResponse(
                m.getId(),
                m.getRestaurantId(),
                m.getTitle(),
                m.getDescription(),
                m.getPrice(),
                m.getCategory(),
                m.getImageUrl(),
                m.getStatus(),
                m.getIsVeg(),
                m.getSpiceLevel(),
                m.getPrepMinutes(),
                m.getDailyPlateCount(),
                m.getCreatedAt());
    }
}
