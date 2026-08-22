package com.savorystay.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.savorystay.tenant.TenantContextFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final TenantContextFilter tenantContextFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, TenantContextFilter tenantContextFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.tenantContextFilter = tenantContextFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints. NOTE: only the payment WEBHOOK is public
                // (gateway callbacks cannot authenticate); the mock create-intent /
                // process-realtime helpers require a valid session like everything else.
                .requestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/health/**",
                    "/api/v1/payments/webhook",
                    "/api/v1/restaurants",
                    "/api/v1/restaurants/*",
                    "/api/v1/restaurants/*/menu",
                    "/api/v1/restaurants/*/plate-availability",
                    "/api/v1/restaurants/*/table-availability",
                    // Guests may browse the pre-order availability calendar while
                    // signed out — it carries no customer data (the endpoint reads
                    // the restaurant id from the request body, not the session).
                    "/api/v1/pre-orders/dates",
                    "/api/v1/realtime/stream",
                    // Customer–restaurant membership (requires auth via @PreAuthorize)
                    "/api/v1/customer-restaurants/**"
                ).permitAll()
                // Platform super admin only
                .requestMatchers("/api/v1/super-admin/**").hasRole("SUPER_ADMIN")
                // Restaurant admin: manage staff
                .requestMatchers("/api/v1/staff/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                // Menu: ANY staff may READ (chefs need recipes/availability), but only
                // manager/admin/super-admin may create, edit prices, or delete.
                .requestMatchers(HttpMethod.GET, "/api/v1/menu/**")
                    .hasAnyRole("CHEF", "MANAGER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/menu/**").hasAnyRole("MANAGER", "ADMIN", "SUPER_ADMIN")
                // Ingredients: any staff may read stock/forecast; writes are manager+.
                .requestMatchers(HttpMethod.GET, "/api/v1/ingredients/**")
                    .hasAnyRole("CHEF", "MANAGER", "ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/v1/ingredients/**").hasAnyRole("MANAGER", "ADMIN", "SUPER_ADMIN")
                // Order placement: any authenticated user can reach the endpoint;
                // the controller returns a clear error for non-CUSTOMER roles.
                .requestMatchers(HttpMethod.POST, "/api/v1/orders").authenticated()
                // Order status: all staff may touch it, but the per-transition rules
                // (chef cooks/packs; manager completes/hands over) are enforced in
                // OrderService.updateStatus using the caller's role.
                .requestMatchers("/api/v1/orders/status").hasAnyRole("CHEF", "MANAGER", "ADMIN", "SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Strength 12 BCrypt Salt Rounds
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
