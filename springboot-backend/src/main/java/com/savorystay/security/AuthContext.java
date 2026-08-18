package com.savorystay.security;

import com.savorystay.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Reads the authenticated user's claims from the current TenantContext ThreadLocal.
 * Falls back to the Authorization header for SSE / token-in-query-param requests.
 *
 * The primary source is TenantContext (populated by TenantContextFilter);
 * fallback is direct token decoding via JwtTokenProvider.
 *
 * Prefer using TenantContext directly in new code:
 *   TenantContext.getUserId()
 *   TenantContext.getRestaurantId()
 */
@Component
@RequiredArgsConstructor
public class AuthContext {

    private final JwtTokenProvider tokenProvider;

    public String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    public String userId(HttpServletRequest request) {
        // Try TenantContext first (set by filter)
        String id = TenantContext.getUserId();
        if (id != null) return id;
        // Fallback: decode from header
        String token = extractToken(request);
        return token != null ? tokenProvider.getUserIdFromJWT(token) : null;
    }

    public String role(HttpServletRequest request) {
        String role = TenantContext.getRole();
        if (role != null) return role;
        String token = extractToken(request);
        return token != null ? tokenProvider.getRoleFromJWT(token) : null;
    }

    public String restaurantId(HttpServletRequest request) {
        String rid = TenantContext.getRestaurantId();
        if (rid != null) return rid;
        String token = extractToken(request);
        return token != null ? tokenProvider.getRestaurantIdFromJWT(token) : null;
    }

    public String username(HttpServletRequest request) {
        String un = TenantContext.getUsername();
        if (un != null) return un;
        String token = extractToken(request);
        return token != null ? tokenProvider.getUsernameFromJWT(token) : null;
    }

    /**
     * @deprecated Use TenantContext.resolveRestaurantScope(String) instead.
     */
    @Deprecated
    public String resolveRestaurantScope(HttpServletRequest request, String requestedRestaurantId) {
        return TenantContext.resolveRestaurantScope(requestedRestaurantId);
    }
}