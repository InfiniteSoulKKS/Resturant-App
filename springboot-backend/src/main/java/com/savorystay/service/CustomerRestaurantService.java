package com.savorystay.service;

import com.savorystay.entity.CustomerRestaurant;
import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.CustomerRestaurantRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerRestaurantService {

    private final CustomerRestaurantRepository membershipRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    /**
     * Add a customer to a restaurant. Idempotent — joining an already-joined
     * restaurant is a no-op (returns the existing membership).
     *
     * @return the membership record
     */
    @Transactional
    public CustomerRestaurant join(String customerId, String restaurantId, String displayName) {
        // Verify the restaurant exists and is active
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        if ("SUSPENDED".equals(restaurant.getStatus())) {
            throw new IllegalArgumentException("This restaurant is currently suspended and cannot be joined.");
        }

        // Idempotent: if already a member, update display name and return existing
        return membershipRepository.findByCustomerIdAndRestaurantId(customerId, restaurantId)
                .map(existing -> {
                    if (displayName != null && !displayName.isBlank()) {
                        existing.setDisplayName(displayName);
                        return membershipRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    CustomerRestaurant membership = CustomerRestaurant.builder()
                            .customerId(customerId)
                            .restaurantId(restaurantId)
                            .displayName(displayName)
                            .build();
                    log.info("Customer {} joined restaurant {}", customerId, restaurantId);
                    return membershipRepository.save(membership);
                });
    }

    /**
     * Remove a customer from a restaurant.
     */
    @Transactional
    public void leave(String customerId, String restaurantId) {
        membershipRepository.findByCustomerIdAndRestaurantId(customerId, restaurantId)
                .ifPresent(membership -> {
                    membershipRepository.delete(membership);
                    log.info("Customer {} left restaurant {}", customerId, restaurantId);
                });
    }

    /**
     * List all restaurants a customer belongs to.
     * Returns enriched data (restaurant details) for the frontend picker.
     */
    public List<Map<String, Object>> myRestaurants(String customerId) {
        return membershipRepository.findByCustomerIdOrderByJoinedAtDesc(customerId)
                .stream()
                .map(m -> {
                    Restaurant restaurant = restaurantRepository.findById(m.getRestaurantId()).orElse(null);
                    Map<String, Object> entry = new java.util.LinkedHashMap<>();
                    entry.put("membershipId", m.getId());
                    entry.put("restaurantId", m.getRestaurantId());
                    entry.put("displayName", m.getDisplayName());
                    entry.put("joinedAt", m.getJoinedAt());
                    if (restaurant != null) {
                        entry.put("name", restaurant.getName());
                        entry.put("slug", restaurant.getSlug());
                        entry.put("logoUrl", restaurant.getLogoUrl());
                        entry.put("cuisine", restaurant.getCuisine());
                        entry.put("currency", restaurant.getCurrency());
                        entry.put("status", restaurant.getStatus());
                    }
                    return entry;
                })
                .toList();
    }

    /**
     * Check if a customer is a member of a restaurant.
     */
    public boolean isMember(String customerId, String restaurantId) {
        return membershipRepository.existsByCustomerIdAndRestaurantId(customerId, restaurantId);
    }

    /**
     * Count how many customers are members of a restaurant.
     */
    public long memberCount(String restaurantId) {
        return membershipRepository.countByRestaurantId(restaurantId);
    }

    /**
     * List all customer memberships for a restaurant (manager/admin view).
     */
    public List<CustomerRestaurant> listMembers(String restaurantId) {
        return membershipRepository.findByRestaurantIdOrderByJoinedAtDesc(restaurantId);
    }

    /**
     * List all customer memberships for a restaurant, enriched with user details.
     * Used by the admin/manager customer membership management UI.
     */
    public List<Map<String, Object>> listMembersWithDetails(String restaurantId) {
        List<CustomerRestaurant> memberships = membershipRepository.findByRestaurantIdOrderByJoinedAtDesc(restaurantId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (CustomerRestaurant m : memberships) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("membershipId", m.getId());
            entry.put("customerId", m.getCustomerId());
            entry.put("displayName", m.getDisplayName());
            entry.put("joinedAt", m.getJoinedAt());
            // Enrich with user details
            userRepository.findById(m.getCustomerId()).ifPresent(user -> {
                entry.put("username", user.getUsername());
                entry.put("email", user.getEmail());
                entry.put("phone", user.getPhone());
                entry.put("enabled", user.getEnabled());
            });
            result.add(entry);
        }
        return result;
    }

    /**
     * Remove a customer from a restaurant (admin/manager action).
     * Returns true if the membership existed and was removed.
     */
    @Transactional
    public boolean removeMember(String customerId, String restaurantId) {
        return membershipRepository.findByCustomerIdAndRestaurantId(customerId, restaurantId)
                .map(membership -> {
                    membershipRepository.delete(membership);
                    log.info("Admin removed customer {} from restaurant {}", customerId, restaurantId);
                    return true;
                })
                .orElse(false);
    }
}
