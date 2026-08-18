package com.savorystay.tenant;

/**
 * Holds the current request's tenant (restaurantId) and userId in a ThreadLocal,
 * populated by TenantContextFilter early in the filter chain.
 * This is the per-request "session" — every downstream controller and service
 * reads from here instead of re-decoding the JWT.
 *
 * Corresponds to the reference: TenantContext (ThreadLocal) + TenantContextFilter chain.
 */
public class TenantContext {

    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> RESTAURANT_ID_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME_HOLDER = new ThreadLocal<>();

    // ---------- userId ----------
    public static void setUserId(String userId) { USER_ID_HOLDER.set(userId); }
    public static String getUserId() { return USER_ID_HOLDER.get(); }

    // ---------- restaurantId (tenantId) ----------
    public static void setRestaurantId(String id) { RESTAURANT_ID_HOLDER.set(id); }
    public static String getRestaurantId() { return RESTAURANT_ID_HOLDER.get(); }

    // ---------- role ----------
    public static void setRole(String role) { ROLE_HOLDER.set(role); }
    public static String getRole() { return ROLE_HOLDER.get(); }

    // ---------- username ----------
    public static void setUsername(String username) { USERNAME_HOLDER.set(username); }
    public static String getUsername() { return USERNAME_HOLDER.get(); }

    // ---------- helpers ----------
    public static boolean isAuthenticated() {
        return USER_ID_HOLDER.get() != null;
    }

    public static boolean isSuperAdmin() {
        return "ROLE_SUPER_ADMIN".equals(ROLE_HOLDER.get());
    }

    public static boolean isCustomer() {
        return "ROLE_CUSTOMER".equals(ROLE_HOLDER.get());
    }

    public static boolean isStaff() {
        String role = ROLE_HOLDER.get();
        return role != null && !role.equals("ROLE_CUSTOMER") && !role.equals("ROLE_SUPER_ADMIN");
    }

    /**
     * Resolves the effective restaurant scope.
     * Super Admin may override via a request parameter; everyone else is locked to their own.
     */
    public static String resolveRestaurantScope(String requestedRestaurantId) {
        if (isSuperAdmin() && requestedRestaurantId != null) return requestedRestaurantId;
        String myRestaurant = getRestaurantId();
        if (myRestaurant != null) return myRestaurant;
        // Fallback: super admin with no restaurantId may pass one explicitly
        return requestedRestaurantId;
    }

    /** Must be called in a finally block (Filter does this). */
    public static void clear() {
        USER_ID_HOLDER.remove();
        RESTAURANT_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
        USERNAME_HOLDER.remove();
    }
}