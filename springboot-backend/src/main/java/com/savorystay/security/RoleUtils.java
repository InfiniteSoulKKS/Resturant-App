package com.savorystay.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Role helpers for the comma-separated role model.
 *
 * A user may hold more than one role — e.g. a kitchen lead who is both
 * MANAGER and CHEF. Roles are stored / carried as a comma-separated string
 * ("ROLE_MANAGER,ROLE_CHEF") in the users.role column, the JWT "role" claim,
 * and TenantContext. These helpers make "does this actor have role X?" checks
 * safe regardless of how many roles they hold.
 */
public final class RoleUtils {

    private RoleUtils() {
    }

    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    public static final String ADMIN = "ROLE_ADMIN";
    public static final String MANAGER = "ROLE_MANAGER";
    public static final String CHEF = "ROLE_CHEF";
    public static final String CUSTOMER = "ROLE_CUSTOMER";

    /** Split a possibly comma-separated role string into individual roles. */
    public static Set<String> parseRoles(String roleCsv) {
        if (roleCsv == null || roleCsv.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(roleCsv.split(","))
                .map(String::trim)
                .filter(r -> !r.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** True if the actor holds the given role (e.g. "ROLE_CHEF"). */
    public static boolean hasRole(String roleCsv, String role) {
        return role != null && parseRoles(roleCsv).contains(role);
    }

    /** True if the actor holds ANY of the given roles. */
    public static boolean hasAnyRole(String roleCsv, String... roles) {
        if (roles == null || roles.length == 0) {
            return false;
        }
        Set<String> held = parseRoles(roleCsv);
        for (String role : roles) {
            if (held.contains(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the actor has management authority over menu/pricing/orders
     * (manager, admin, or super admin — NOT a chef-only account).
     */
    public static boolean canManage(String roleCsv) {
        return hasAnyRole(roleCsv, MANAGER, ADMIN, SUPER_ADMIN);
    }

    /**
     * Normalize a user-supplied role list to a canonical comma-separated string,
     * rejecting anything that is not an allowed staff role. Used when creating
     * staff accounts so "ROLE_MANAGER", "ROLE_CHEF", and "ROLE_MANAGER,ROLE_CHEF"
     * are all accepted (deduplicated) and nothing else is.
     */
    public static String normalizeStaffRoles(String roleCsv, List<String> allowedRoles) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String part : parseRoles(roleCsv)) {
            if (!allowedRoles.contains(part)) {
                throw new IllegalArgumentException(
                        "Staff role must be one of: " + String.join(", ", allowedRoles));
            }
            normalized.add(part);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one staff role is required");
        }
        return String.join(",", normalized);
    }
}
