package com.savorystay.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationInMs;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public String generateToken(String username, String role, String userId) {
        return generateToken(username, role, userId, null);
    }

    public String generateToken(String username, String role, String userId, String restaurantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .claim("userId", userId)
                .claim("restaurantId", restaurantId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        return getClaims(token).getSubject();
    }

    public String getRoleFromJWT(String token) {
        Object role = getClaims(token).get("role");
        return role != null ? role.toString() : "ROLE_CUSTOMER";
    }

    public String getUserIdFromJWT(String token) {
        Object userId = getClaims(token).get("userId");
        return userId != null ? userId.toString() : null;
    }

    public String getRestaurantIdFromJWT(String token) {
        Object restaurantId = getClaims(token).get("restaurantId");
        return restaurantId != null ? restaurantId.toString() : null;
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }
}
