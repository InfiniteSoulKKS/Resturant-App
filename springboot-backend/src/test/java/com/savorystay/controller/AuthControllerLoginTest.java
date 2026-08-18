package com.savorystay.controller;

import com.savorystay.dto.LoginRequest;
import com.savorystay.dto.LoginWithOtpRequest;
import com.savorystay.entity.OtpRequest;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.AuthContext;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.service.AuthRateLimitService;
import com.savorystay.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the email-or-username login fallback in
 * {@link AuthController#authenticateUser(LoginRequest, HttpServletRequest)}
 * and {@link AuthController#loginWithOtp(LoginWithOtpRequest, HttpServletRequest)}.
 *
 * The login form says "Username or Email", so both endpoints must accept an
 * email address as the identifier: username lookup first, then an email
 * lookup when the identifier contains "@" and the username wasn't found.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider tokenProvider;
    @Mock OtpService otpService;
    @Mock AuthRateLimitService rateLimitService;
    @Mock AuthContext authContext;
    @Mock com.savorystay.service.CustomerRestaurantService customerRestaurantService;
    @Mock HttpServletRequest httpRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                userRepository, passwordEncoder, tokenProvider, otpService, rateLimitService, authContext, customerRestaurantService);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    /** Only the success-path tests reach token generation. */
    private void stubToken() {
        when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("jwt-token");
    }

    private User user(String username, String email, boolean enabled) {
        return User.builder()
                .id("USR_1")
                .username(username)
                .email(email)
                .passwordHash("$2a$hash")
                .role("ROLE_CUSTOMER")
                .enabled(enabled)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    // ==================== PASSWORD LOGIN ====================

    @Test
    void loginByUsernameSucceeds() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("rahul", "secret"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt-token", body(response).get("token"));
        verify(userRepository, never()).findByEmail(any());
        verify(rateLimitService).recordSuccess(eq("user:rahul"), eq("ip:127.0.0.1"));
    }

    @Test
    void loginByEmailFallsBackToEmailLookup() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        // Username lookup misses — the identifier contains "@", so email is tried.
        when(userRepository.findByUsername("rahul@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("rahul@example.com", "secret"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt-token", body(response).get("token"));
        verify(userRepository).findByEmail("rahul@example.com");
    }

    @Test
    void emailLoginTrimsWhitespaceBeforeLookup() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("  rahul@example.com  ", "secret"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findByEmail("rahul@example.com");
    }

    @Test
    void unknownIdentifierWithoutAtSignDoesNotTryEmailLookup() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("nobody", "secret"), httpRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(userRepository, never()).findByEmail(any());
        verify(rateLimitService).recordFailure(eq("user:nobody"), eq("ip:127.0.0.1"));
    }

    @Test
    void unknownEmailReturnsUnauthorized() {
        when(userRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("ghost@example.com", "secret"), httpRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void disabledAccountRejectedEvenWhenFoundByEmail() {
        User user = user("rahul", "rahul@example.com", false);
        when(userRepository.findByUsername("rahul@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("rahul@example.com", "secret"), httpRequest);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void wrongPasswordReturnsUnauthorized() {
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        ResponseEntity<?> response = controller.authenticateUser(
                new LoginRequest("rahul", "wrong"), httpRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rateLimitService).recordFailure(eq("user:rahul"), eq("ip:127.0.0.1"));
    }

    // ==================== OTP LOGIN ====================

    @Test
    void otpLoginByUsernameSucceeds() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(otpService.verifyLoginOtp(anyList(), eq("123456"), eq(OtpRequest.OtpChannel.EMAIL)))
                .thenReturn(true);

        ResponseEntity<?> response = controller.loginWithOtp(
                new LoginWithOtpRequest("rahul", "123456", "EMAIL"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("jwt-token", body(response).get("token"));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void otpLoginByEmailFallsBackToEmailLookup() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(otpService.verifyLoginOtp(anyList(), eq("123456"), eq(OtpRequest.OtpChannel.EMAIL)))
                .thenReturn(true);

        ResponseEntity<?> response = controller.loginWithOtp(
                new LoginWithOtpRequest("rahul@example.com", "123456", "EMAIL"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).findByEmail("rahul@example.com");
    }

    @Test
    void otpLoginVerifiesOtpAgainstRegisteredEmail() {
        stubToken();
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("rahul@example.com")).thenReturn(Optional.of(user));
        when(otpService.verifyLoginOtp(List.of("rahul@example.com"), "123456", OtpRequest.OtpChannel.EMAIL))
                .thenReturn(true);

        ResponseEntity<?> response = controller.loginWithOtp(
                new LoginWithOtpRequest("rahul@example.com", "123456", "EMAIL"), httpRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(otpService).verifyLoginOtp(List.of("rahul@example.com"), "123456", OtpRequest.OtpChannel.EMAIL);
    }

    @Test
    void otpLoginWithBadCodeReturnsUnauthorized() {
        User user = user("rahul", "rahul@example.com", true);
        when(userRepository.findByUsername("rahul")).thenReturn(Optional.of(user));
        when(otpService.verifyLoginOtp(anyList(), eq("000000"), eq(OtpRequest.OtpChannel.EMAIL)))
                .thenReturn(false);

        ResponseEntity<?> response = controller.loginWithOtp(
                new LoginWithOtpRequest("rahul", "000000", "EMAIL"), httpRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(rateLimitService).recordFailure(eq("user:rahul"), eq("ip:127.0.0.1"));
    }

    @Test
    void otpLoginForUnknownEmailReturnsUnauthorized() {
        when(userRepository.findByUsername("ghost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.loginWithOtp(
                new LoginWithOtpRequest("ghost@example.com", "123456", "EMAIL"), httpRequest);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(otpService, never()).verifyLoginOtp(anyList(), any(), any());
    }
}
