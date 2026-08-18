package com.savorystay.service;

import com.savorystay.entity.Order;
import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.MenuItemRepository;
import com.savorystay.repository.OrderRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RestaurantService#deleteRestaurant(String)}.
 *
 * A restaurant may only be deleted while it has no historical data:
 * <ul>
 *   <li>fresh restaurant (only its own staff accounts) → staff + restaurant deleted;</li>
 *   <li>restaurant with order history → refused with a clear message (suspend instead);</li>
 *   <li>restaurant with menu items → refused with a clear message;</li>
 *   <li>unknown id → clear error.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

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

    private Restaurant restaurant() {
        return Restaurant.builder().id(REST).name("Test Diner").status("ACTIVE").build();
    }

    private User staffAccount() {
        return User.builder().id("USR_1").username("mgr").role("ROLE_ADMIN").restaurantId(REST).build();
    }

    @Test
    void freshRestaurantWithOnlyStaffIsDeleted() {
        when(restaurantRepository.existsById(REST)).thenReturn(true);
        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());
        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());
        when(userRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of(staffAccount()));

        service.deleteRestaurant(REST);

        // Staff accounts that belong to the restaurant are removed with it, and
        // the restaurant itself is deleted — no raw FK error.
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<User>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(userRepository).deleteAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("mgr", captor.getValue().get(0).getUsername());
        verify(restaurantRepository).deleteById(REST);
    }

    @Test
    void freshRestaurantWithoutAnyStaffIsDeleted() {
        when(restaurantRepository.existsById(REST)).thenReturn(true);
        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());
        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());
        when(userRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());

        service.deleteRestaurant(REST);

        // deleteAll is invoked with the (empty) staff query result — harmless,
        // and the restaurant is deleted without any FK violation.
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<User>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(userRepository).deleteAll(captor.capture());
        assertTrue(captor.getValue().isEmpty());
        verify(restaurantRepository).deleteById(REST);
    }

    @Test
    void restaurantWithOrderHistoryRefusesDeletion() {
        when(restaurantRepository.existsById(REST)).thenReturn(true);
        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(REST))
                .thenReturn(List.of(Order.builder().id("ORD_1").restaurantId(REST).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteRestaurant(REST));
        assertTrue(ex.getMessage().contains("order history"));
        verify(restaurantRepository, never()).deleteById(REST);
    }

    @Test
    void restaurantWithMenuItemsRefusesDeletion() {
        when(restaurantRepository.existsById(REST)).thenReturn(true);
        when(orderRepository.findByRestaurantIdOrderByCreatedAtDesc(REST)).thenReturn(List.of());
        when(menuItemRepository.findByRestaurantIdOrderByCreatedAtDesc(REST))
                .thenReturn(List.of(com.savorystay.entity.MenuItem.builder().id("MI_1").restaurantId(REST).build()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.deleteRestaurant(REST));
        assertTrue(ex.getMessage().contains("menu items"));
        verify(restaurantRepository, never()).deleteById(REST);
    }

    @Test
    void unknownRestaurantRefusesDeletion() {
        when(restaurantRepository.existsById(REST)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.deleteRestaurant(REST));
        verify(restaurantRepository, never()).deleteById(REST);
    }

    @Test
    void getReturnsRestaurantWhenPresent() {
        when(restaurantRepository.findById(REST)).thenReturn(Optional.of(restaurant()));
        assertTrue(service.get(REST).isPresent());
    }
}
