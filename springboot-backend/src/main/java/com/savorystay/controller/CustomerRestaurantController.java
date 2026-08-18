package com.savorystay.controller;

import com.savorystay.security.AuthContext;
import com.savorystay.service.CustomerRestaurantService;
import com.savorystay.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/customer-restaurants")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CustomerRestaurantController {

    private final CustomerRestaurantService service;
    private final AuthContext authContext;

    /**
     * Join a restaurant. The customer becomes a member and can later select
     * this restaurant during login.
     */
    @PostMapping("/join")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> join(@RequestBody Map<String, String> body,
                                  HttpServletRequest request) {
        String customerId = authContext.userId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        String restaurantId = body.get("restaurantId");
        if (restaurantId == null || restaurantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "restaurantId is required"));
        }

        try {
            var membership = service.join(customerId, restaurantId, body.get("displayName"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Joined restaurant successfully",
                    "membershipId", membership.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Leave a restaurant.
     */
    @DeleteMapping("/leave/{restaurantId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> leave(@PathVariable String restaurantId,
                                   HttpServletRequest request) {
        String customerId = authContext.userId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        service.leave(customerId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Left restaurant"));
    }

    /**
     * List all restaurants the current customer is a member of.
     * This is the data source for the post-login restaurant picker.
     */
    @GetMapping("/my-restaurants")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> myRestaurants(HttpServletRequest request) {
        String customerId = authContext.userId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        var restaurants = service.myRestaurants(customerId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "restaurants", restaurants,
                "count", restaurants.size()
        ));
    }

    /**
     * Check if the current customer is a member of a specific restaurant.
     */
    @GetMapping("/is-member/{restaurantId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> isMember(@PathVariable String restaurantId,
                                      HttpServletRequest request) {
        String customerId = authContext.userId(request);
        if (customerId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        boolean member = service.isMember(customerId, restaurantId);
        return ResponseEntity.ok(Map.of("success", true, "isMember", member));
    }

    // =========================================
    // ADMIN / MANAGER ENDPOINTS
    // =========================================

    /**
     * List all customer members of a restaurant (admin/manager view).
     * Returns enriched data with user details (username, email, phone).
     */
    @GetMapping("/members")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER')")
    public ResponseEntity<?> listMembers(@RequestParam(required = false) String restaurantId,
                                         HttpServletRequest request) {
        String resolvedRestaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (resolvedRestaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        var members = service.listMembersWithDetails(resolvedRestaurantId);
        long count = service.memberCount(resolvedRestaurantId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "members", members,
                "count", count
        ));
    }

    /**
     * Remove a customer from a restaurant (admin action).
     * The customer's order history is preserved but they can no longer select this restaurant.
     */
    @DeleteMapping("/members/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> removeMember(@PathVariable String customerId,
                                          @RequestParam(required = false) String restaurantId,
                                          HttpServletRequest request) {
        String resolvedRestaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (resolvedRestaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }

        boolean removed = service.removeMember(customerId, resolvedRestaurantId);
        if (removed) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Customer removed from restaurant"));
        } else {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "Membership not found"));
        }
    }
}
