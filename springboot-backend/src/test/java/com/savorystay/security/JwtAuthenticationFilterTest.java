package com.savorystay.security;

import com.savorystay.controller.MenuController;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.service.MenuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the JWT filter's immediate disable/deletion enforcement.
 *
 * A staff account's token is valid for 24h — but "Disable Account" must cut
 * access NOW, not at token expiry. The filter checks the user's {@code enabled}
 * flag in the DB on every request and fails closed (no authentication) when the
 * account is disabled or no longer exists, so the request is rejected at the
 * security layer even though the token itself would validate.
 *
 * Uses the real {@link SecurityConfig} + JWT/Tenant filters with the token
 * provider and user store mocked.
 */
@WebMvcTest(MenuController.class)
@Import(SecurityConfig.class)
class JwtAuthenticationFilterTest {

    @Autowired MockMvc mockMvc;

    @MockBean MenuService menuService;
    @MockBean JwtTokenProvider jwtTokenProvider;
    @MockBean UserRepository userRepository;

    /** A user whose token claims match what the mocked provider returns. */
    private User user(boolean enabled) {
        return User.builder()
                .id("USR_1")
                .username("chefuser")
                .role("ROLE_CHEF")
                .restaurantId("REST_1")
                .enabled(enabled)
                .build();
    }

    /** Make every filter treat "token" as a valid JWT for chefuser / ROLE_CHEF / REST_1. */
    private void validTokenFor(String username) {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromJWT(anyString())).thenReturn(username);
        when(jwtTokenProvider.getRoleFromJWT(anyString())).thenReturn("ROLE_CHEF");
        when(jwtTokenProvider.getUserIdFromJWT(anyString())).thenReturn("USR_1");
        when(jwtTokenProvider.getRestaurantIdFromJWT(anyString())).thenReturn("REST_1");
    }

    @Test
    void enabledAccountTokenPasses() throws Exception {
        validTokenFor("chefuser");
        when(userRepository.findByUsername("chefuser")).thenReturn(Optional.of(user(true)));
        when(menuService.listMenu("REST_1", false)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/menu").header("Authorization", "Bearer some.jwt.token"))
                .andExpect(status().isOk());
    }

    @Test
    void disabledAccountTokenIsRejectedImmediately() throws Exception {
        validTokenFor("chefuser");
        when(userRepository.findByUsername("chefuser")).thenReturn(Optional.of(user(false)));

        // Token is cryptographically valid, but the account was disabled since
        // it was issued — the request must NOT reach the controller.
        mockMvc.perform(get("/api/v1/menu").header("Authorization", "Bearer some.jwt.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedAccountTokenIsRejectedImmediately() throws Exception {
        validTokenFor("ghostuser");
        when(userRepository.findByUsername("ghostuser")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/menu").header("Authorization", "Bearer some.jwt.token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/menu"))
                .andExpect(status().isForbidden());
    }
}
