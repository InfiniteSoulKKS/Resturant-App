package com.savorystay.controller;

import com.savorystay.entity.Restaurant;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.security.SecurityConfig;
import com.savorystay.service.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the PUBLIC restaurant-detail endpoint ({@code GET /api/v1/restaurants/{id}}):
 *
 * <ul>
 *   <li>guests (no token) may fetch an ACTIVE restaurant — the endpoint must be
 *       in the permit-all list, not behind authentication;</li>
 *   <li>suspended restaurants are hidden (404), so an offline restaurant is
 *       indistinguishable from a missing one to the public;</li>
 *   <li>the staff endpoints remain protected — the permit-all addition did not
 *       open anything else.</li>
 * </ul>
 *
 * Uses the real {@link SecurityConfig} + JWT/Tenant filters (dependencies mocked)
 * so the actual security rules are exercised, not reimplemented in the test.
 */
@WebMvcTest(RestaurantController.class)
@Import(SecurityConfig.class)
class RestaurantPublicEndpointTest {

    @Autowired MockMvc mockMvc;

    @MockBean RestaurantService restaurantService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private Restaurant restaurant(String id, String status) {
        return Restaurant.builder()
                .id(id)
                .name("Test Diner")
                .slug("test-diner")
                .status(status)
                .build();
    }

    @Test
    void guestCanFetchActiveRestaurant() throws Exception {
        when(restaurantService.get("REST_ACTIVE")).thenReturn(Optional.of(restaurant("REST_ACTIVE", "ACTIVE")));

        mockMvc.perform(get("/api/v1/restaurants/REST_ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.restaurant.id").value("REST_ACTIVE"))
                .andExpect(jsonPath("$.restaurant.status").value("ACTIVE"));
    }

    @Test
    void suspendedRestaurantIsHiddenFromGuests() throws Exception {
        when(restaurantService.get("REST_SUSPENDED")).thenReturn(Optional.of(restaurant("REST_SUSPENDED", "SUSPENDED")));

        mockMvc.perform(get("/api/v1/restaurants/REST_SUSPENDED"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingRestaurantReturns404() throws Exception {
        when(restaurantService.get("REST_MISSING")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/restaurants/REST_MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffEndpointsStillRequireAuthentication() throws Exception {
        // No token and no "with" user → the request must be rejected at the
        // security layer (403: anonymous user, no entry point configured),
        // proving the public restaurant endpoint was the only thing opened up.
        mockMvc.perform(get("/api/v1/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanFetchRestaurantDetailWhenAuthenticated() throws Exception {
        when(restaurantService.get("REST_ACTIVE")).thenReturn(Optional.of(restaurant("REST_ACTIVE", "ACTIVE")));

        mockMvc.perform(get("/api/v1/restaurants/REST_ACTIVE")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("chef")
                                .roles("CHEF")))
                .andExpect(status().isOk());
    }
}
