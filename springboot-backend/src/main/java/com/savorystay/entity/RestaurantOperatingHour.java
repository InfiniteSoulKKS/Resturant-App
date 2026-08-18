package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

/**
 * Weekly operating hours for a restaurant, one row per day of the week
 * (dayOfWeek: 1 = Monday ... 7 = Sunday).
 *
 * A day can be a full holiday ({@code closed == true}) or a partial day —
 * e.g. "2nd half closed" is modeled as {@code closeTime = 14:00}. Per the
 * business rule, ANY closed period on a day means no pre-orders are accepted
 * for that day at all; on fully open days the requested pickup time must fall
 * within {@code openTime..closeTime}.
 */
@Entity
@Table(name = "restaurant_operating_hours",
        uniqueConstraints = @UniqueConstraint(columnNames = {"restaurant_id", "day_of_week"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantOperatingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "open_time")
    private LocalTime openTime;

    @Column(name = "close_time")
    private LocalTime closeTime;

    /** True = the restaurant is closed the entire day (weekly holiday). */
    @Column(nullable = false)
    private Boolean closed;

    @PrePersist
    protected void onCreate() {
        if (closed == null) closed = false;
    }
}
