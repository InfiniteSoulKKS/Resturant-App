package com.savorystay.controller;

import com.savorystay.entity.Order;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.security.SecurityConfig;
import com.savorystay.service.AuditService;
import com.savorystay.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies tenant isolation on the order endpoints through the real security
 * stack: staff accounts are locked to their own restaurant (a forged
 * {@code ?restaurantId=} is ignored), customers cannot list another
 * restaurant's order queue or read orders they don't own, and only a super
 * admin may view cross-restaurant data.
 */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderTenantIsolationTest {

    @Autowired MockMvc mockMvc;

    @MockBean OrderService orderService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;
    @MockBean AuditService auditService;
    @MockBean com.savorystay.repository.OrderRepository orderRepository;

    /** Authenticate the request as a given role/user/restaurant via a mock JWT. */
    private void authenticateAs(String username, String role, String userId, String restaurantId) {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromJWT(anyString())).thenReturn(username);
        when(jwtTokenProvider.getRoleFromJWT(anyString())).thenReturn(role);
        when(jwtTokenProvider.getUserIdFromJWT(anyString())).thenReturn(userId);
        when(jwtTokenProvider.getRestaurantIdFromJWT(anyString())).thenReturn(restaurantId);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(
                User.builder().id(userId).username(username).role(role).restaurantId(restaurantId).enabled(true).build()));
    }

    private Order order(String id, String restaurantId, String userId) {
        return Order.builder().id(id).orderNumber("#ORD-" + id).restaurantId(restaurantId)
                .userId(userId).orderType("PICKUP").build();
    }

    // ------------------------------------------------------------------
    // Restaurant order queue (staff only)
    // ------------------------------------------------------------------

    @Test
    void staffListIsLockedToOwnRestaurantDespiteForgedParam() throws Exception {
        authenticateAs("adminA", "ROLE_ADMIN", "USR_A", "REST_A");
        when(orderService.ordersForRestaurant("REST_A")).thenReturn(List.of());
        when(orderService.itemsByOrderIds(org.mockito.ArgumentMatchers.anyList())).thenReturn(java.util.Map.of());

        // Admin of REST_A asks for REST_B — the scope must stay REST_A.
        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer x.y.z")
                        .param("restaurantId", "REST_B"))
                .andExpect(status().isOk());

        verify(orderService).ordersForRestaurant("REST_A");
    }

    @Test
    void customerCannotListRestaurantOrders() throws Exception {
        authenticateAs("customer", "ROLE_CUSTOMER", "USR_C", null);

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotListRestaurantOrders() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Order detail (owner, staff of the same restaurant, or super admin)
    // ------------------------------------------------------------------

    @Test
    void staffCannotViewAnotherRestaurantsOrder() throws Exception {
        authenticateAs("adminA", "ROLE_ADMIN", "USR_A", "REST_A");
        when(orderService.getById("ORD_B")).thenReturn(Optional.of(order("ORD_B", "REST_B", "USR_B")));

        mockMvc.perform(get("/api/v1/orders/ORD_B").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanViewOwnRestaurantsOrder() throws Exception {
        authenticateAs("adminA", "ROLE_ADMIN", "USR_A", "REST_A");
        when(orderService.getById("ORD_A")).thenReturn(Optional.of(order("ORD_A", "REST_A", "USR_B")));
        when(orderService.itemsFor("ORD_A")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/ORD_A").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotViewSomeoneElsesOrder() throws Exception {
        authenticateAs("customer", "ROLE_CUSTOMER", "USR_C", null);
        when(orderService.getById("ORD_A")).thenReturn(Optional.of(order("ORD_A", "REST_A", "USR_OTHER")));

        mockMvc.perform(get("/api/v1/orders/ORD_A").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanViewOwnOrder() throws Exception {
        authenticateAs("customer", "ROLE_CUSTOMER", "USR_C", null);
        when(orderService.getById("ORD_C")).thenReturn(Optional.of(order("ORD_C", "REST_A", "USR_C")));
        when(orderService.itemsFor("ORD_C")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/ORD_C").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isOk());
    }

    @Test
    void superAdminCanViewAnyRestaurantsOrder() throws Exception {
        authenticateAs("superadmin", "ROLE_SUPER_ADMIN", "USR_SA", null);
        when(orderService.getById("ORD_B")).thenReturn(Optional.of(order("ORD_B", "REST_B", "USR_B")));
        when(orderService.itemsFor("ORD_B")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/orders/ORD_B").header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isOk());
    }
}
