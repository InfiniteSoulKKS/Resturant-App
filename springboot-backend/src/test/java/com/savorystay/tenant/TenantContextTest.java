package com.savorystay.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the tenant-isolation rule in
 * {@link TenantContext#resolveRestaurantScope}: restaurant staff are locked to
 * their own restaurant no matter what a caller sends; only a super admin may
 * pass an explicit restaurant id to operate cross-restaurant.
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void staffIsLockedToOwnRestaurantEvenWhenRequestingAnother() {
        // ROLE_ADMIN of REST_A asks for REST_B — the scope must resolve to REST_A.
        TenantContext.setRole("ROLE_ADMIN");
        TenantContext.setRestaurantId("REST_A");

        assertEquals("REST_A", TenantContext.resolveRestaurantScope("REST_B"));
    }

    @Test
    void staffWithNoRequestedParamUsesOwnRestaurant() {
        TenantContext.setRole("ROLE_CHEF");
        TenantContext.setRestaurantId("REST_A");

        assertEquals("REST_A", TenantContext.resolveRestaurantScope(null));
    }

    @Test
    void superAdminMayOverrideWithExplicitRestaurantId() {
        TenantContext.setRole("ROLE_SUPER_ADMIN");
        TenantContext.setRestaurantId(null);

        assertEquals("REST_B", TenantContext.resolveRestaurantScope("REST_B"));
    }

    @Test
    void superAdminWithoutParamHasNoScope() {
        TenantContext.setRole("ROLE_SUPER_ADMIN");
        TenantContext.setRestaurantId(null);

        assertNull(TenantContext.resolveRestaurantScope(null));
    }

    @Test
    void customerWithoutRestaurantFallsBackToRequested() {
        // Customers roam across restaurants — their JWT carries no restaurant,
        // so the scope comes from the requested id (order placement etc.).
        TenantContext.setRole("ROLE_CUSTOMER");
        TenantContext.setRestaurantId(null);

        assertEquals("REST_B", TenantContext.resolveRestaurantScope("REST_B"));
    }

    @Test
    void unauthenticatedContextHasNoScope() {
        assertNull(TenantContext.resolveRestaurantScope(null));
    }
}
