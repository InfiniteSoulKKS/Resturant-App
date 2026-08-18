package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A customer–restaurant membership record.
 * Each customer (ROLE_CUSTOMER) may join multiple restaurants;
 * staff users are scoped to a single restaurant via {@link User#restaurantId}.
 */
@Entity
@Table(
    name = "customer_restaurant",
    uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "restaurant_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRestaurant {

    @Id
    private String id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Column(name = "restaurant_id", nullable = false, length = 64)
    private String restaurantId;

    /** Optional display name the customer uses at this restaurant (e.g. "Rahul" vs "Rahul S."). */
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("CR");
        if (joinedAt == null) joinedAt = LocalDateTime.now();
    }
}
