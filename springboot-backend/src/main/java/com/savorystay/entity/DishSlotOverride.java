package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Manager-controlled override for a specific date + dish:
 * {@code action} is OPEN (make it orderable even if the weekly schedule says
 * otherwise) or CLOSE (block it even if the weekly schedule says available).
 *
 * Precedence: CLOSE > OPEN > weekly schedule. Restaurant closure always blocks.
 */
@Entity
@Table(name = "dish_slot_override",
        uniqueConstraints = @UniqueConstraint(columnNames = {"menu_item_id", "target_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DishSlotOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "menu_item_id", nullable = false, length = 64)
    private String menuItemId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(nullable = false, length = 10)
    private String action; // OPEN, CLOSE
}
