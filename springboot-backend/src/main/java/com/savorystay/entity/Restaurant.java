package com.savorystay.entity;

import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * A registered restaurant in the multi-tenant platform.
 * Every restaurant owns its menu, staff, orders and ingredients.
 */
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(unique = true, length = 60)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String address;

    @Column(length = 80)
    private String city;

    @Column(length = 60)
    private String cuisine;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Lob
    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(length = 20)
    private String status; // ACTIVE, SUSPENDED

    @Column(name = "currency", length = 10)
    private String currency; // INR, USD, EUR

    @Column(name = "owner_id", length = 64)
    private String ownerId; // Super Admin that registered this restaurant

    /** When true, customers are auto-joined to this restaurant on their first order. */
    @Column(name = "auto_join_customers")
    @Builder.Default
    private Boolean autoJoinCustomers = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("REST");
        if (slug == null || slug.isBlank()) {
            slug = (name != null ? name.toLowerCase().replaceAll("[^a-z0-9]+", "-") : "restaurant")
                    .replaceAll("(^-|-$)", "") + "-" + (100 + (int) (Math.random() * 900));
        }
        if (status == null) status = "ACTIVE";
        if (currency == null) currency = "INR";
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
