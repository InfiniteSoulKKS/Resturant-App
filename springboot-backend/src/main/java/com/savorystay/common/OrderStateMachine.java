package com.savorystay.common;

import com.savorystay.config.OrderStateException;

import java.util.Map;
import java.util.Set;

/**
 * Authoritative order state transition rules.
 * Every transition is validated server-side — frontend visibility is NOT security.
 * 
 * Valid lifecycle:
 *   NEW → PREPARING → PACKED_READY → COMPLETED
 *   NEW → DECLINED
 *   NEW → CANCELLED
 *   PREPARING → CANCELLED (manager only)
 *   PREPARING → DECLINED (manager only)
 *   PACKED_READY → COMPLETED (manager only)
 *   PACKED_READY → CANCELLED (manager only)
 * 
 * Terminal states (no transitions out):
 *   COMPLETED, DECLINED, CANCELLED
 */
public final class OrderStateMachine {

    private OrderStateMachine() {}

    /** All valid order statuses. */
    public static final Set<String> VALID_STATUSES = Set.of(
            "NEW", "PREPARING", "PACKED_READY", "COMPLETED",
            "DECLINED", "CANCELLED"
    );

    /** Terminal states — no further transitions allowed. */
    public static final Set<String> TERMINAL_STATES = Set.of(
            "COMPLETED", "DECLINED", "CANCELLED"
    );

    /**
     * Allowed transitions: source → set of valid targets.
     * Only the transitions listed here are permitted.
     */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "NEW",          Set.of("PREPARING", "DECLINED", "CANCELLED"),
            "PREPARING",    Set.of("PACKED_READY", "DECLINED", "CANCELLED"),
            "PACKED_READY", Set.of("COMPLETED", "CANCELLED")
    );

    /**
     * Chef-only transitions (cooking + packing).
     * Chefs cannot decline, cancel, or complete orders.
     */
    private static final Set<String> CHEF_ALLOWED = Set.of(
            "NEW→PREPARING",
            "PREPARING→PACKED_READY"
    );

    /**
     * Manager/Admin transitions (all transitions including decline/cancel/complete).
     */
    private static final Set<String> MANAGER_ALLOWED = Set.of(
            "NEW→PREPARING",
            "NEW→DECLINED",
            "NEW→CANCELLED",
            "PREPARING→PACKED_READY",
            "PREPARING→DECLINED",
            "PREPARING→CANCELLED",
            "PACKED_READY→COMPLETED",
            "PACKED_READY→CANCELLED"
    );

    /**
     * Check if a transition from `from` to `to` is valid.
     */
    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        Set<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    /**
     * Check if a role can perform this transition.
     */
    public static boolean canRolePerform(String from, String to, String role) {
        String key = from + "→" + to;
        if ("ROLE_CHEF".equals(role)) {
            return CHEF_ALLOWED.contains(key);
        }
        // Manager, Admin, Super Admin can perform all valid transitions
        return MANAGER_ALLOWED.contains(key);
    }

    /**
     * Validate a transition. Throws IllegalArgumentException with a clear
     * business error message if the transition is not allowed.
     */
    public static void validate(String from, String to, String role) {
        if (!canTransition(from, to)) {
            throw new OrderStateException(
                    "Order cannot move from " + from + " to " + to);
        }
        if (!canRolePerform(from, to, role)) {
            throw new SecurityException(
                    "Role " + role + " is not authorized for transition " + from + " → " + to);
        }
    }
}
