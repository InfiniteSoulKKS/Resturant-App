package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Recurring weekly availability: a dish can be pre-ordered on the configured
 * weekdays (dayOfWeek: 1 = Monday ... 7 = Sunday). A dish without any rows is
 * available every day (backward compatible), but managers are nudged to
 * configure availability via the pre-order reminder.
 */
@Entity
@Table(name = "dish_availability",
        uniqueConstraints = @UniqueConstraint(columnNames = {"menu_item_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DishAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "menu_item_id", nullable = false, length = 64)
    private String menuItemId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;
}
