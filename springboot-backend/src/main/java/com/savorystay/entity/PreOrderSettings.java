package com.savorystay.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

/**
 * Pre-order rules for a restaurant (one row per restaurant).
 *
 * Pre-orders for a fulfillment date D are accepted until {@code cutoffTime}
 * on day D-1 (e.g. cutoff 09:00 means Tuesday's pre-orders close Monday 09:00,
 * business timezone). Customers can pre-order up to {@code advanceDays} days
 * ahead.
 */
@Entity
@Table(name = "preorder_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreOrderSettings {

    @Id
    @Column(name = "restaurant_id", length = 64)
    private String restaurantId;

    @Column(name = "cutoff_time", nullable = false)
    private LocalTime cutoffTime;

    @Column(name = "advance_days", nullable = false)
    private Integer advanceDays;
}
