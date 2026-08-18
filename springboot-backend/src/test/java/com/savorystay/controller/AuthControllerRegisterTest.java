package com.savorystay.controller;

import com.savorystay.dto.RegisterRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for phone normalization at registration
 * ({@link AuthController#registerUser(RegisterRequest)}).
 *
 * The phone is stored in digits-only form so that whatever the user types later
 * (with/without country code, spaces, dashes) compares cleanly against it, and
 * duplicate checks are format-tolerant ("+91 98765 43210" collides with an
 * existing "919876543210").
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerRegisterTest {

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
    }

    /** Only the tests that actually create the account reach these. */
    private void stubEncodeAndToken() {
        when(passwordEncoder.encode("secret")).thenReturn("$2a$encoded");
        when(tokenProvider.generateToken(any(), any(), any(), any())).thenReturn("jwt-token");
    }

    private void stubOtpVerifiedForSms(String phone) {
        when(otpService.verifyOtp(phone, "123456", OtpRequest.OtpChannel.SMS)).thenReturn(true);
    }

    private void stubNoDuplicateAccount(String username, String email) {
        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(userRepository.existsByEmail(email)).thenReturn(false);
    }

    private void stubNoPhoneMatch(String normalizedPhone) {
        when(userRepository.findByPhone(normalizedPhone)).thenReturn(Optional.empty());
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private RegisterRequest registerWith(String username, String email, String phone) {
        return new RegisterRequest(username, email, "secret", phone, "123456", "SMS");
    }

    @Test
    void phoneWithSpacesAndCountryCodeIsStoredDigitsOnly() {
        stubEncodeAndToken();
        stubOtpVerifiedForSms("+91 98765 43210");
        stubNoDuplicateAccount("newuser", "new@example.com");
        stubNoPhoneMatch("919876543210");

        ResponseEntity<?> response = controller.registerUser(registerWith("newuser", "new@example.com", "+91 98765 43210"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("919876543210", savedUser().getPhone(),
                "formatted phone must be normalized to digits before storing");
    }

    @Test
    void phoneWithDashesIsStoredDigitsOnly() {
        stubEncodeAndToken();
        stubOtpVerifiedForSms("+91-98765-43210");
        stubNoDuplicateAccount("newuser", "new@example.com");
        stubNoPhoneMatch("919876543210");

        controller.registerUser(registerWith("newuser", "new@example.com", "+91-98765-43210"));

        assertEquals("919876543210", savedUser().getPhone());
    }

    @Test
    void blankPhoneIsStoredAsNull() {
        stubEncodeAndToken();
        stubOtpVerifiedForSms("   ");
        stubNoDuplicateAccount("newuser", "new@example.com");

        controller.registerUser(registerWith("newuser", "new@example.com", "   "));

        assertNull(savedUser().getPhone(), "blank phones must be stored as null (unique index safety)");
    }

    @Test
    void nullPhoneIsStoredAsNull() {
        stubEncodeAndToken();
        stubOtpVerifiedForSms(null);
        stubNoDuplicateAccount("newuser", "new@example.com");

        controller.registerUser(registerWith("newuser", "new@example.com", null));

        assertNull(savedUser().getPhone());
    }

    @Test
    void duplicateCheckUsesNormalizedPhoneSoFormatsCollide() {
        User existing = User.builder().id("USR_X").username("old").phone("919876543210").build();
        stubOtpVerifiedForSms("+91 98765 43210");
        stubNoDuplicateAccount("newuser", "new@example.com");
        // The same digits exist already — a differently formatted phone must collide.
        when(userRepository.findByPhone("919876543210")).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = controller.registerUser(registerWith("newuser", "new@example.com", "+91 98765 43210"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("already registered"));
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void duplicateCheckQueriesTheNormalizedForm() {
        stubEncodeAndToken();
        stubOtpVerifiedForSms("+91 98765 43210");
        stubNoDuplicateAccount("newuser", "new@example.com");
        stubNoPhoneMatch("919876543210");

        controller.registerUser(registerWith("newuser", "new@example.com", "+91 98765 43210"));

        // The duplicate lookup is made against digits only — the raw formatted
        // value would never match the stored one.
        verify(userRepository).findByPhone("919876543210");
    }
}
