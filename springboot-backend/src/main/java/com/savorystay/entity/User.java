package com.savorystay.entity;

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

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 30)
    private String role; // ROLE_CUSTOMER, ROLE_CHEF, ROLE_ADMIN

    private Boolean enabled;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = "USR_" + System.currentTimeMillis();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = "ROLE_CUSTOMER";
        if (enabled == null) enabled = true;
    }
}
