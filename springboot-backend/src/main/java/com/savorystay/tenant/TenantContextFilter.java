package com.savorystay.tenant;

import com.savorystay.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the Bearer JWT from each request, validates it, and populates
 * TenantContext with userId, restaurantId, role, and username.
 *
 * Any request without a valid token leaves TenantContext in a clean state
 * (nulls) rather than retaining stale values from a previous request.
 *
 * Corresponds to the reference: TenantResolutionFilter + TenantContextFilter.
 */
@Component
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && tokenProvider.validateToken(token)) {
                TenantContext.setUserId(tokenProvider.getUserIdFromJWT(token));
                TenantContext.setRestaurantId(tokenProvider.getRestaurantIdFromJWT(token));
                TenantContext.setRole(tokenProvider.getRoleFromJWT(token));
                TenantContext.setUsername(tokenProvider.getUsernameFromJWT(token));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}