package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A menu item belonging to a specific restaurant.
 */
@Entity
@Table(name = "menu_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {

    @Id
    private String id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(length = 20)
    private String status; // Available, Sold Out

    @Column(name = "is_veg")
    private Boolean isVeg;

    @Column(name = "spice_level", length = 20)
    private String spiceLevel;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("MI");
        if (status == null) status = "Available";
        if (isVeg == null) isVeg = true;
        if (spiceLevel == null) spiceLevel = "Medium";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
