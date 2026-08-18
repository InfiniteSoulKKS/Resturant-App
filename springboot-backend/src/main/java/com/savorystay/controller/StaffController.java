package com.savorystay.controller;

import com.savorystay.dto.CreateStaffRequest;
import com.savorystay.dto.SetStaffEnabledRequest;
import com.savorystay.dto.UserResponse;
import com.savorystay.entity.User;
import com.savorystay.service.RestaurantService;
import com.savorystay.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StaffController {

    private final RestaurantService restaurantService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> addStaff(@Valid @RequestBody CreateStaffRequest req) {
        try {
            String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "Staff must belong to a restaurant"));
            }
            User staff = restaurantService.addStaff(
                    restaurantId, req.username(), req.email(),
                    req.password(), req.phone(),
                    req.role() != null ? req.role() : "ROLE_MANAGER");
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", staff.getRole().replace("ROLE_", "") + " account created for " + staff.getUsername());
            resp.put("staff", UserResponse.from(staff));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error adding staff: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> listStaff(@RequestParam(required = false) String restaurantId) {
        restaurantId = TenantContext.resolveRestaurantScope(restaurantId);
        if (restaurantId == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
        }
        List<User> staff = restaurantService.listStaff(restaurantId);
        List<UserResponse> dtos = staff.stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(Map.of("success", true, "staff", dtos));
    }

    @PatchMapping("/{staffId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> setEnabled(@PathVariable String staffId,
                                        @Valid @RequestBody SetStaffEnabledRequest req) {
        try {
            boolean enabled = req.enabled() != null ? req.enabled() : true;
            // Scope to the caller's restaurant (admins are locked to their own;
            // super admins may pass an explicit restaurantId). RestaurantService
            // enforces the boundary too, so cross-restaurant staff changes are blocked.
            String restaurantId = TenantContext.resolveRestaurantScope(req.restaurantId());
            if (restaurantId == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "No restaurant scope"));
            }
            User staff = restaurantService.setStaffEnabled(staffId, restaurantId, enabled);
            return ResponseEntity.ok(Map.of("success", true, "staff", UserResponse.from(staff)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
