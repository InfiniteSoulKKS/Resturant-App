package com.savorystay.controller;

import com.savorystay.repository.UserRepository;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.security.SecurityConfig;
import com.savorystay.service.PreOrderAvailabilityService;
import com.savorystay.service.PreOrderConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the pre-order availability calendar endpoint
 * ({@code POST /api/v1/pre-orders/dates}) is PUBLIC — signed-out guests must be
 * able to browse which days are orderable while picking a dish. The endpoint
 * reads the restaurant id from the request body (not the session), so it carries
 * no customer data and is safe to open up.
 *
 * The manager-side configuration endpoints under the same controller
 * ({@code /api/v1/pre-orders/config/**}) must stay protected.
 *
 * Uses the real {@link SecurityConfig} + JWT/Tenant filters (dependencies mocked).
 */
@WebMvcTest(PreOrderController.class)
@Import(SecurityConfig.class)
class PreOrderCalendarPublicTest {

    @Autowired MockMvc mockMvc;

    @MockBean PreOrderConfigService configService;
    @MockBean PreOrderAvailabilityService availabilityService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    private Map<String, Object> calendarDay(String date, boolean orderable) {
        Map<String, Object> day = new LinkedHashMap<>();
        day.put("date", date);
        day.put("weekday", "Wednesday");
        day.put("openTime", "09:00 AM");
        day.put("closeTime", "11:00 PM");
        day.put("orderable", orderable);
        day.put("reasons", List.of());
        day.put("dishes", List.of());
        return day;
    }

    @Test
    void guestCanFetchOrderableDatesWithoutToken() throws Exception {
        when(availabilityService.availableDates(eq("REST_DEMO_1"), anyList(), anyInt()))
                .thenReturn(List.of(calendarDay("2026-08-19", true)));

        mockMvc.perform(post("/api/v1/pre-orders/dates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":\"REST_DEMO_1\",\"menuItemIds\":[\"MI_1\"],\"daysAhead\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.dates[0].date").value("2026-08-19"))
                .andExpect(jsonPath("$.dates[0].orderable").value(true));
    }

    @Test
    void guestWithEmptyBodyGetsClearError() throws Exception {
        mockMvc.perform(post("/api/v1/pre-orders/dates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("restaurantId is required"));
    }

    @Test
    void managerConfigEndpointsStayProtectedFromGuests() throws Exception {
        // No token → the manager-only config endpoints must reject the request
        // at the security layer, proving only the /dates endpoint was opened.
        mockMvc.perform(get("/api/v1/pre-orders/config/hours"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/pre-orders/config/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCanFetchConfigWhenAuthenticated() throws Exception {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromJWT(anyString())).thenReturn("mgr");
        when(jwtTokenProvider.getRoleFromJWT(anyString())).thenReturn("ROLE_MANAGER");
        when(jwtTokenProvider.getUserIdFromJWT(anyString())).thenReturn("USR_1");
        when(jwtTokenProvider.getRestaurantIdFromJWT(anyString())).thenReturn("REST_DEMO_1");
        when(userRepository.findByUsername("mgr")).thenReturn(java.util.Optional.of(
                com.savorystay.entity.User.builder().id("USR_1").username("mgr")
                        .role("ROLE_MANAGER").restaurantId("REST_DEMO_1").enabled(true).build()));
        when(configService.operatingHours("REST_DEMO_1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pre-orders/config/hours")
                        .header("Authorization", "Bearer x.y.z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void authenticatedCustomerStillReadsCalendarThroughBody() throws Exception {
        // The endpoint is public, but it must still work for signed-in users —
        // the restaurant id comes from the body, not the session.
        when(availabilityService.availableDates(anyString(), anyList(), any()))
                .thenReturn(List.of(calendarDay("2026-08-19", true)));

        mockMvc.perform(post("/api/v1/pre-orders/dates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\":\"REST_DEMO_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
