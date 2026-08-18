package com.savorystay.controller;

import com.savorystay.entity.OtpRequest;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
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

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the tolerant phone comparison ({@code phonesMatch}) that gates
 * login OTP sends in {@link OtpController#sendOtpViaSMS(Map, HttpServletRequest)}.
 *
 * The typed number must be the registered number exactly, or a genuine local
 * form of it (registered WITH country code ending in the typed local number).
 * Adding a country code to a locally-registered number — or typing any other
 * number — is rejected, so a different country-code variant can never receive
 * an OTP for someone else's account.
 */
@ExtendWith(MockitoExtension.class)
class OtpControllerPhoneMatchTest {

    @Mock OtpService otpService;
    @Mock AuthRateLimitService rateLimitService;
    @Mock UserRepository userRepository;
    @Mock HttpServletRequest httpRequest;

    private OtpController controller;

    @BeforeEach
    void setUp() {
        controller = new OtpController(otpService, rateLimitService, userRepository);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    }

    /** Only the success-path tests reach OTP generation. */
    private void stubOtpGeneration() {
        when(otpService.generateAndSendOtp(any(), any(), eq(OtpRequest.OtpChannel.SMS), any()))
                .thenReturn(OtpRequest.builder().id(1L).otpCode("123456").build());
        when(otpService.isDemoDelivery(OtpRequest.OtpChannel.SMS)).thenReturn(false);
    }

    private void stubAccount(String username, String phone) {
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(User.builder().id("USR_1").username(username).phone(phone).build()));
    }

    private ResponseEntity<?> sendSms(Map<String, String> body) {
        return controller.sendOtpViaSMS(body, httpRequest);
    }

    @Test
    void localFormOfCountryCodedPhoneIsAccepted() {
        // Registered: "+919876543210" — the local form "9876543210" is a genuine
        // subset ending of the registered number, so it must match.
        stubAccount("rahul", "+919876543210");
        stubOtpGeneration();

        ResponseEntity<?> response = sendSms(Map.of("username", "rahul", "phone", "9876543210"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("OTP sent"));
    }

    @Test
    void formattedVersionOfRegisteredPhoneIsAccepted() {
        // Registered: "+919876543210" — the same number typed with spaces.
        stubAccount("rahul", "+919876543210");
        stubOtpGeneration();

        ResponseEntity<?> response = sendSms(Map.of("username", "rahul", "phone", "+91 98765 43210"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void differentPhoneIsRejected() {
        stubAccount("rahul", "+919876543210");

        ResponseEntity<?> response = sendSms(Map.of("username", "rahul", "phone", "+911234567890"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("not registered to this account"));
        verify(otpService, never()).generateAndSendOtp(any(), any(), any(), any());
    }

    @Test
    void addingCountryCodeToLocallyRegisteredPhoneIsRejected() {
        // Registered locally as "9876543210" — typing it WITH a country code is a
        // different-number variant and must NOT match (the security rule).
        stubAccount("rahul", "9876543210");

        ResponseEntity<?> response = sendSms(Map.of("username", "rahul", "phone", "+919876543210"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("not registered to this account"));
    }

    @Test
    void nonNumericPhoneIsRejectedBeforeAnyMatch() {
        // The format check runs before the account lookup, so no account exists
        // to match against — the request is rejected on format alone.
        ResponseEntity<?> response = sendSms(Map.of("username", "rahul", "phone", "not-a-number"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Invalid phone number format"));
        verify(otpService, never()).generateAndSendOtp(any(), any(), any(), any());
    }

    @Test
    void registrationStyleSendWithoutUsernameSkipsPhoneMatch() {
        // No username → registration-style send: no account is looked up, the
        // formatted phone is accepted as-is (normalization still applies).
        stubOtpGeneration();

        ResponseEntity<?> response = sendSms(Map.of("phone", "+91 98765 43210"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    void unknownUsernameIsRejectedBeforePhoneMatch() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        ResponseEntity<?> response = sendSms(Map.of("username", "nobody", "phone", "+919876543210"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("No account found with this username"));
        verify(otpService, never()).generateAndSendOtp(any(), any(), any(), any());
    }
}
