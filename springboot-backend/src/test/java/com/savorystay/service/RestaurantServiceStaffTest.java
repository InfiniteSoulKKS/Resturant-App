package com.savorystay.service;

import com.savorystay.entity.User;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the staff account validation rules in
 * {@link RestaurantService#addStaff(String, String, String, String, String, String)}.
 *
 * A staff member may be created with an email, a phone number, or both —
 * never with neither. Duplicate emails / phone numbers / usernames are
 * rejected, and only MANAGER / CHEF roles are allowed.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantServiceStaffTest {

    private static final String REST = "REST_TEST";

    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock PasswordEncoder passwordEncoder;

    private RestaurantService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantService(
                restaurantRepository, userRepository, orderRepository, menuItemRepository, passwordEncoder);
    }

    /** Only the tests that actually reach user creation need the encoder stub. */
    private void stubEncode() {
        when(passwordEncoder.encode("pass123")).thenReturn("$2a$encoded");
    }

    private User savedUser() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void staffWithPhoneOnlyIsCreated() {
        stubEncode();
        when(userRepository.existsByUsername("kumar")).thenReturn(false);
        when(userRepository.existsByPhone("+91 98765 00001")).thenReturn(false);

        service.addStaff(REST, "kumar", null, "pass123", "+91 98765 00001", "ROLE_CHEF");

        User saved = savedUser();
        assertNull(saved.getEmail(), "staff without an email must store a null email");
        assertEquals("+91 98765 00001", saved.getPhone());
        assertEquals("ROLE_CHEF", saved.getRole());
        assertEquals(REST, saved.getRestaurantId());
    }

    @Test
    void staffWithEmailOnlyIsCreated() {
        stubEncode();
        when(userRepository.existsByUsername("mgr")).thenReturn(false);
        when(userRepository.existsByEmail("mgr@savorystay.com")).thenReturn(false);

        service.addStaff(REST, "mgr", "mgr@savorystay.com", "pass123", null, "ROLE_MANAGER");

        User saved = savedUser();
        assertEquals("mgr@savorystay.com", saved.getEmail());
        assertNull(saved.getPhone(), "staff without a phone must store a null phone");
    }

    @Test
    void staffWithBlankEmailAndNoPhoneIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "mgr", "   ", "pass123", null, "ROLE_MANAGER"));
        assertTrue(ex.getMessage().contains("Email or phone number is required"));
    }

    @Test
    void staffWithNeitherEmailNorPhoneIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "mgr", null, "pass123", null, "ROLE_MANAGER"));
        assertTrue(ex.getMessage().contains("Email or phone number is required"));
    }

    @Test
    void duplicateUsernameIsRejected() {
        when(userRepository.existsByUsername("mgr")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "mgr", "mgr@savorystay.com", "pass123", null, "ROLE_MANAGER"));
        assertTrue(ex.getMessage().contains("Username already exists"));
    }

    @Test
    void duplicateEmailIsRejected() {
        when(userRepository.existsByUsername("mgr")).thenReturn(false);
        when(userRepository.existsByEmail("mgr@savorystay.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "mgr", "mgr@savorystay.com", "pass123", null, "ROLE_MANAGER"));
        assertTrue(ex.getMessage().contains("Email already in use"));
    }

    @Test
    void duplicatePhoneIsRejected() {
        when(userRepository.existsByUsername("kumar")).thenReturn(false);
        when(userRepository.existsByPhone("+91 98765 00001")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "kumar", null, "pass123", "+91 98765 00001", "ROLE_CHEF"));
        assertTrue(ex.getMessage().contains("Phone number already in use"));
    }

    @Test
    void blankEmailIsStoredAsNull() {
        stubEncode();
        when(userRepository.existsByUsername("kumar")).thenReturn(false);
        when(userRepository.existsByPhone("+91 98765 00002")).thenReturn(false);

        service.addStaff(REST, "kumar", "   ", "pass123", "+91 98765 00002", "ROLE_CHEF");

        assertNull(savedUser().getEmail(), "blank emails must be stored as null (unique index safety)");
    }

    @Test
    void combinedManagerChefRolesAreAccepted() {
        stubEncode();
        when(userRepository.existsByUsername("lead")).thenReturn(false);
        when(userRepository.existsByEmail("lead@savorystay.com")).thenReturn(false);

        service.addStaff(REST, "lead", "lead@savorystay.com", "pass123", null, "ROLE_MANAGER,ROLE_CHEF");

        assertEquals("ROLE_MANAGER,ROLE_CHEF", savedUser().getRole());
    }

    @Test
    void disallowedRoleIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addStaff(REST, "mgr", "mgr@savorystay.com", "pass123", null, "ROLE_CUSTOMER"));
        assertTrue(ex.getMessage().contains("Staff role must be one of"));
    }

    @Test
    void usernameDuplicateCheckUsesProvidedValueAsIs() {
        // The duplicate check runs against the raw input — a differently-spaced
        // username does not collide with an existing one (and vice versa).
        stubEncode();
        when(userRepository.existsByUsername(" mgr ")).thenReturn(false);
        when(userRepository.existsByEmail("mgr@savorystay.com")).thenReturn(false);

        service.addStaff(REST, " mgr ", "mgr@savorystay.com", "pass123", null, "ROLE_MANAGER");

        assertEquals(" mgr ", savedUser().getUsername(), "the username itself is stored as provided");
    }
}
