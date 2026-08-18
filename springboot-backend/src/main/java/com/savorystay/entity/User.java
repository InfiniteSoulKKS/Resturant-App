package com.savorystay.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.savorystay.common.IdGenerator;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Nullable on purpose: some staff members have no email and are created
    // with a phone number only (MySQL allows repeated NULLs in a unique index).
    @Column(unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    @JsonIgnore // never serialize the password hash to API responses
    private String passwordHash;

    @Column(length = 30)
    private String role; // ROLE_SUPER_ADMIN, ROLE_ADMIN, ROLE_MANAGER, ROLE_CHEF, ROLE_CUSTOMER

    @Column(length = 20, unique = true)
    private String phone; // For OTP delivery

    @Column(name = "restaurant_id", length = 64)
    private String restaurantId; // Null for SUPER_ADMIN and roaming CUSTOMERS

    private Boolean enabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = IdGenerator.newId("USR");
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = "ROLE_CUSTOMER";
        if (enabled == null) enabled = true;
        if ("ROLE_CUSTOMER".equals(role)) restaurantId = null;
        normalizeBlankPhone();
    }

    @PreUpdate
    protected void onUpdate() {
        normalizeBlankPhone();
    }

    /**
     * The phone column is UNIQUE — MySQL treats an empty string as a real value,
     * so a second user with an empty phone would violate the unique index. Blank
     * phones are normalized to null (which may repeat freely) before persisting.
     */
    private void normalizeBlankPhone() {
        if (phone != null && phone.isBlank()) {
            phone = null;
        }
    }
}
