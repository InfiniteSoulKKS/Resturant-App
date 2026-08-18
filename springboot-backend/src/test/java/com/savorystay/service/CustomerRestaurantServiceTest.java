package com.savorystay.service;

import com.savorystay.entity.CustomerRestaurant;
import com.savorystay.entity.Restaurant;
import com.savorystay.entity.User;
import com.savorystay.repository.CustomerRestaurantRepository;
import com.savorystay.repository.RestaurantRepository;
import com.savorystay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerRestaurantServiceTest {

    private static final String CUSTOMER_ID = "USR_CUST_01";
    private static final String REST_1 = "REST_01";
    private static final String REST_2 = "REST_02";
    private static final String MEMBERSHIP_ID = "CR_01";

    @Mock CustomerRestaurantRepository membershipRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;

    private CustomerRestaurantService service;

    @BeforeEach
    void setUp() {
        service = new CustomerRestaurantService(membershipRepository, restaurantRepository, userRepository);
    }

    // ==================== JOIN ====================

    @Test
    void joinNewRestaurant() {
        Restaurant restaurant = Restaurant.builder().id(REST_1).name("Test Place").status("ACTIVE").build();
        when(restaurantRepository.findById(REST_1)).thenReturn(Optional.of(restaurant));
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            CustomerRestaurant cr = inv.getArgument(0);
            cr.setId(MEMBERSHIP_ID);
            return cr;
        });

        CustomerRestaurant result = service.join(CUSTOMER_ID, REST_1, "Rahul");

        assertEquals(CUSTOMER_ID, result.getCustomerId());
        assertEquals(REST_1, result.getRestaurantId());
        assertEquals("Rahul", result.getDisplayName());
        verify(membershipRepository).save(any());
    }

    @Test
    void joinExistingRestaurantIsIdempotent() {
        Restaurant restaurant = Restaurant.builder().id(REST_1).name("Test Place").status("ACTIVE").build();
        CustomerRestaurant existing = CustomerRestaurant.builder()
                .id(MEMBERSHIP_ID).customerId(CUSTOMER_ID).restaurantId(REST_1).displayName("Old").build();
        when(restaurantRepository.findById(REST_1)).thenReturn(Optional.of(restaurant));
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.of(existing));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerRestaurant result = service.join(CUSTOMER_ID, REST_1, "New Name");

        assertEquals("New Name", result.getDisplayName());
        // No new record created — the existing one was updated
        verify(membershipRepository, never()).save(argThat(cr -> cr.getId() == null));
    }

    @Test
    void joinSuspendedRestaurantThrows() {
        Restaurant restaurant = Restaurant.builder().id(REST_1).name("Suspended Place").status("SUSPENDED").build();
        when(restaurantRepository.findById(REST_1)).thenReturn(Optional.of(restaurant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.join(CUSTOMER_ID, REST_1, null));
        assertTrue(ex.getMessage().contains("suspended"));
    }

    @Test
    void joinNonexistentRestaurantThrows() {
        when(restaurantRepository.findById(REST_1)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.join(CUSTOMER_ID, REST_1, null));
        assertTrue(ex.getMessage().contains("not found"));
    }

    // ==================== LEAVE ====================

    @Test
    void leaveExistingMembership() {
        CustomerRestaurant existing = CustomerRestaurant.builder()
                .id(MEMBERSHIP_ID).customerId(CUSTOMER_ID).restaurantId(REST_1).build();
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.of(existing));

        service.leave(CUSTOMER_ID, REST_1);

        verify(membershipRepository).delete(existing);
    }

    @Test
    void leaveNonexistentMembershipIsNoOp() {
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.empty());

        service.leave(CUSTOMER_ID, REST_1);

        verify(membershipRepository, never()).delete(any());
    }

    // ==================== IS MEMBER ====================

    @Test
    void isMemberReturnsTrue() {
        when(membershipRepository.existsByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(true);
        assertTrue(service.isMember(CUSTOMER_ID, REST_1));
    }

    @Test
    void isMemberReturnsFalse() {
        when(membershipRepository.existsByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(false);
        assertFalse(service.isMember(CUSTOMER_ID, REST_1));
    }

    // ==================== MY RESTAURANTS ====================

    @Test
    void myRestaurantsReturnsEnrichedData() {
        CustomerRestaurant m1 = CustomerRestaurant.builder()
                .id("CR_1").customerId(CUSTOMER_ID).restaurantId(REST_1).displayName("R").build();
        CustomerRestaurant m2 = CustomerRestaurant.builder()
                .id("CR_2").customerId(CUSTOMER_ID).restaurantId(REST_2).build();

        Restaurant r1 = Restaurant.builder().id(REST_1).name("Place One").cuisine("Indian").currency("INR").build();
        Restaurant r2 = Restaurant.builder().id(REST_2).name("Place Two").cuisine("Italian").currency("EUR").build();

        when(membershipRepository.findByCustomerIdOrderByJoinedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(m1, m2));
        when(restaurantRepository.findById(REST_1)).thenReturn(Optional.of(r1));
        when(restaurantRepository.findById(REST_2)).thenReturn(Optional.of(r2));

        List<Map<String, Object>> result = service.myRestaurants(CUSTOMER_ID);

        assertEquals(2, result.size());
        assertEquals("Place One", result.get(0).get("name"));
        assertEquals("Indian", result.get(0).get("cuisine"));
        assertEquals("INR", result.get(0).get("currency"));
        assertEquals("R", result.get(0).get("displayName"));
        assertEquals("Place Two", result.get(1).get("name"));
    }

    @Test
    void myRestaurantsHandlesMissingRestaurantGracefully() {
        CustomerRestaurant m = CustomerRestaurant.builder()
                .id("CR_1").customerId(CUSTOMER_ID).restaurantId("REST_GONE").build();

        when(membershipRepository.findByCustomerIdOrderByJoinedAtDesc(CUSTOMER_ID))
                .thenReturn(List.of(m));
        when(restaurantRepository.findById("REST_GONE")).thenReturn(Optional.empty());

        List<Map<String, Object>> result = service.myRestaurants(CUSTOMER_ID);

        assertEquals(1, result.size());
        // Restaurant data is null-safe — the entry still exists
        assertNull(result.get(0).get("name"));
    }

    // ==================== MEMBER COUNT ====================

    @Test
    void memberCountDelegatesToRepository() {
        when(membershipRepository.countByRestaurantId(REST_1)).thenReturn(42L);
        assertEquals(42L, service.memberCount(REST_1));
    }

    // ==================== LIST MEMBERS ====================

    @Test
    void listMembersDelegatesToRepository() {
        CustomerRestaurant m = CustomerRestaurant.builder()
                .id("CR_1").customerId(CUSTOMER_ID).restaurantId(REST_1).build();
        when(membershipRepository.findByRestaurantIdOrderByJoinedAtDesc(REST_1))
                .thenReturn(List.of(m));

        List<CustomerRestaurant> result = service.listMembers(REST_1);
        assertEquals(1, result.size());
    }

    // ==================== LIST MEMBERS WITH DETAILS ====================

    @Test
    void listMembersWithDetailsEnrichesWithUserData() {
        CustomerRestaurant m = CustomerRestaurant.builder()
                .id("CR_1").customerId(CUSTOMER_ID).restaurantId(REST_1).displayName("Rahul").build();
        User user = User.builder()
                .id(CUSTOMER_ID).username("rahul_dev").email("rahul@test.com").phone("+91123").enabled(true).build();

        when(membershipRepository.findByRestaurantIdOrderByJoinedAtDesc(REST_1))
                .thenReturn(List.of(m));
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(user));

        List<Map<String, Object>> result = service.listMembersWithDetails(REST_1);

        assertEquals(1, result.size());
        assertEquals("rahul_dev", result.get(0).get("username"));
        assertEquals("rahul@test.com", result.get(0).get("email"));
        assertEquals("Rahul", result.get(0).get("displayName"));
        assertEquals(true, result.get(0).get("enabled"));
    }

    @Test
    void listMembersWithDetailsHandlesMissingUserGracefully() {
        CustomerRestaurant m = CustomerRestaurant.builder()
                .id("CR_1").customerId("USR_GONE").restaurantId(REST_1).build();

        when(membershipRepository.findByRestaurantIdOrderByJoinedAtDesc(REST_1))
                .thenReturn(List.of(m));
        when(userRepository.findById("USR_GONE")).thenReturn(Optional.empty());

        List<Map<String, Object>> result = service.listMembersWithDetails(REST_1);

        assertEquals(1, result.size());
        assertNull(result.get(0).get("username"));
    }

    // ==================== REMOVE MEMBER ====================

    @Test
    void removeMemberDeletesExistingMembership() {
        CustomerRestaurant existing = CustomerRestaurant.builder()
                .id("CR_1").customerId(CUSTOMER_ID).restaurantId(REST_1).build();
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.of(existing));

        boolean removed = service.removeMember(CUSTOMER_ID, REST_1);

        assertTrue(removed);
        verify(membershipRepository).delete(existing);
    }

    @Test
    void removeMemberReturnsFalseWhenNotExists() {
        when(membershipRepository.findByCustomerIdAndRestaurantId(CUSTOMER_ID, REST_1))
                .thenReturn(Optional.empty());

        boolean removed = service.removeMember(CUSTOMER_ID, REST_1);

        assertFalse(removed);
        verify(membershipRepository, never()).delete(any());
    }
}
