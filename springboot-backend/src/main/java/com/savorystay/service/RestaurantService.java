package com.savorystay.service;

import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.RoleUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final PasswordEncoder passwordEncoder;

    public List<Restaurant> listAll() {
        return restaurantRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Restaurant> listActive() {
        return restaurantRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
    }

    public Optional<Restaurant> get(String id) {
        return restaurantRepository.findById(id);
    }

    /**
     * Super Admin creates a restaurant and its first Restaurant Admin account.
     */
    @Transactional
    public Restaurant createRestaurant(Restaurant restaurant, String adminUsername,
                                       String adminEmail, String adminPassword) {
        if (restaurantRepository.findByName(restaurant.getName()).isPresent()) {
            throw new IllegalArgumentException("Restaurant with this name already exists");
        }

        Restaurant saved = restaurantRepository.save(restaurant);

        if (adminUsername != null && !adminUsername.isBlank()) {
            if (userRepository.existsByUsername(adminUsername)) {
                throw new IllegalArgumentException("Username already exists");
            }
            if (adminEmail != null && !adminEmail.isBlank() && userRepository.existsByEmail(adminEmail)) {
                throw new IllegalArgumentException("Email already in use");
            }
            User admin = User.builder()
                    .username(adminUsername)
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .role("ROLE_ADMIN")
                    .restaurantId(saved.getId())
                    .phone(null)
                    .build();
            userRepository.save(admin);
            saved.setOwnerId(admin.getId());
            restaurantRepository.save(saved);
        }
        return saved;
    }

    public Restaurant updateRestaurant(String id, Restaurant updates) {
        Restaurant existing = restaurantRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getAddress() != null) existing.setAddress(updates.getAddress());
        if (updates.getCity() != null) existing.setCity(updates.getCity());
        if (updates.getCuisine() != null) existing.setCuisine(updates.getCuisine());
        if (updates.getPhone() != null) existing.setPhone(updates.getPhone());
        if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
        if (updates.getLogoUrl() != null) existing.setLogoUrl(updates.getLogoUrl());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getCurrency() != null) existing.setCurrency(updates.getCurrency());
        return restaurantRepository.save(existing);
    }

    /**
     * Deleting a restaurant with historical orders or menu items would violate
     * foreign keys and surface a raw SQL error. A restaurant that only has its
     * own staff accounts (e.g. just created by mistake) CAN be deleted: its
     * restaurant-scoped staff rows are removed along with it. Restaurants with
     * order/menu history must be suspended instead — never deleted.
     */
    public void deleteRestaurant(String id) {
        if (!restaurantRepository.existsById(id)) {
            throw new IllegalArgumentException("Restaurant not found");
        }
        if (!orderRepository.findByRestaurantIdOrderByCreatedAtDesc(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete this restaurant: it has order history. Suspend it instead of deleting.");
        }
        if (!menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(id).isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot delete this restaurant: it has menu items. Remove them or suspend the restaurant instead.");
        }
        // A restaurant with only its own staff accounts is safe to remove — the
        // accounts belong to it and have no orders. Delete them with the restaurant.
        userRepository.deleteAll(userRepository.findByRestaurantIdOrderByCreatedAtDesc(id));
        restaurantRepository.deleteById(id);
    }

    // ==================== STAFF MANAGEMENT ====================

    /**
     * Restaurant Admin (or Super Admin) creates MANAGER / CHEF accounts.
     * A single staff member may hold both roles ("ROLE_MANAGER,ROLE_CHEF")
     * to share kitchen + management responsibilities.
     */
    public User addStaff(String restaurantId, String username, String email, String password,
                         String phone, String role) {
        String normalizedRoles = RoleUtils.normalizeStaffRoles(
                role == null ? "ROLE_MANAGER" : role,
                List.of(RoleUtils.MANAGER, RoleUtils.CHEF));
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Blank emails are stored as null (see User.normalizeBlankPhone / email
        // column is nullable) so staff without an email can be added via phone.
        String normalizedEmail = email != null && !email.isBlank() ? email.trim() : null;
        String normalizedPhone = phone != null && !phone.isBlank() ? phone.trim() : null;
        if (normalizedEmail == null && normalizedPhone == null) {
            throw new IllegalArgumentException("Email or phone number is required (provide at least one)");
        }
        if (normalizedEmail != null && userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Phone number already in use");
        }

        User staff = User.builder()
                .username(username)
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .passwordHash(passwordEncoder.encode(password))
                .role(normalizedRoles)
                .restaurantId(restaurantId)
                .build();
        return userRepository.save(staff);
    }

    public List<User> listStaff(String restaurantId) {
        return userRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    /**
     * Enable/disable a staff account. The caller's restaurant scope is enforced
     * so one restaurant's admin can never touch another restaurant's staff.
     */
    public User setStaffEnabled(String staffId, String restaurantId, boolean enabled) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff not found"));
        if (restaurantId == null || !restaurantId.equals(staff.getRestaurantId())) {
            throw new SecurityException("Staff member does not belong to your restaurant");
        }
        staff.setEnabled(enabled);
        return userRepository.save(staff);
    }
}
