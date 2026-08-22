package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Per-restaurant settings for table configuration, time slots, and capacity.
 * One row per restaurant. Defaults are sensible so restaurants work out-of-the-box.
 */
@Entity
@Table(name = "restaurant_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestaurantSettings {

    @Id
    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;

    /**
     * JSON array of table-type configurations.
     * Example: [{"type":"2-Seater","count":5},{"type":"4-Seater","count":4},{"type":"6-Seater","count":2}]
     * Each entry defines a seating category with the number of tables of that type.
     */
    @Column(name = "table_config", columnDefinition = "TEXT")
    @Builder.Default
    private String tableConfig = "[{\"type\":\"2-Seater\",\"count\":5},{\"type\":\"4-Seater\",\"count\":4},{\"type\":\"6-Seater\",\"count\":2}]";

    /** Total number of tables available for dine-in (computed from table_config). */
    @Column(name = "total_tables", nullable = false)
    @Builder.Default
    private Integer totalTables = 11;

    /**
     * Comma-separated pickup time slot templates for PICKUP orders.
     * The checkout modal shows these as options.
     *
     * Example: "15 Mins,30 Mins,45 Mins,1 Hour,1.5 Hours"
     */
    @Column(name = "pickup_time_slots", length = 500)
    @Builder.Default
    private String pickupTimeSlots = "15 Mins,30 Mins,45 Mins,1 Hour,1.5 Hours";

    /**
     * Comma-separated time slot templates for DINE_IN orders.
     * The checkout modal shows these as time-of-day options.
     *
     * Example: "12:00 PM,12:30 PM,1:00 PM,1:30 PM,7:00 PM,7:30 PM,8:00 PM,8:30 PM,9:00 PM"
     */
    @Column(name = "dinein_time_slots", length = 500)
    @Builder.Default
    private String dineinTimeSlots = "12:00 PM,12:30 PM,1:00 PM,1:30 PM,2:00 PM,7:00 PM,7:30 PM,8:00 PM,8:30 PM,9:00 PM,9:30 PM";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
