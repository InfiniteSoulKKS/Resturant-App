package com.savorystay.scheduler;

import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import com.savorystay.service.NotificationService;
import com.savorystay.service.PreOrderAvailabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily 8:45 AM IST reminder to every restaurant's managers/admins when
 * pre-order availability is not configured for at least one of the next
 * 3 days (operating hours missing, or no dish weekly schedule set).
 *
 * The nudge is informational — it does not change any business rule; it only
 * creates an in-app (APP) notification per manager/admin so slots get opened
 * in time for customers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreOrderReminderScheduler {

    private static final int REMINDER_LOOKAHEAD_DAYS = 3;

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PreOrderAvailabilityService availabilityService;

    /** Runs at 08:45 business time (IST by default — see app.business.timezone). */
    @Scheduled(cron = "0 45 8 * * *", zone = "${app.business.timezone:Asia/Kolkata}")
    public void remindManagersToOpenSlots() {
        List<Restaurant> restaurants = restaurantRepository.findAll();
        for (Restaurant restaurant : restaurants) {
            try {
                remindRestaurant(restaurant);
            } catch (Exception e) {
                log.error("Pre-order reminder failed for restaurant {}: {}",
                        restaurant.getId(), e.getMessage(), e);
            }
        }
    }

    private void remindRestaurant(Restaurant restaurant) {
        boolean needs = availabilityService.needsPreOrderReminder(restaurant.getId(), REMINDER_LOOKAHEAD_DAYS);
        if (!needs) {
            return; // everything configured — nothing to remind
        }

        List<User> managers = userRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurant.getId())
                .stream()
                .filter(u -> u.getRole() != null
                        && (u.getRole().contains("ROLE_MANAGER") || u.getRole().contains("ROLE_ADMIN")))
                .filter(User::getEnabled)
                .toList();

        if (managers.isEmpty()) {
            log.info("[PRE-ORDER REMINDER] {} has no active manager/admin to notify", restaurant.getName());
            return;
        }

        String title = "Pre-order slots need attention";
        String message = "Pre-order availability for " + restaurant.getName()
                + " is not fully configured for the next " + REMINDER_LOOKAHEAD_DAYS
                + " days. Please set operating hours and dish availability so customers can pre-order.";

        for (User manager : managers) {
            notificationService.create(manager.getId(), restaurant.getId(), null,
                    title, message, "SYSTEM", "APP");
        }
        log.info("[PRE-ORDER REMINDER] Notified {} manager(s)/admin(s) for {}", managers.size(), restaurant.getName());
    }
}
