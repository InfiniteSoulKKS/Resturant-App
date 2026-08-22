package com.savorystay.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    // ─── VALID TRANSITIONS ─────────────────────────────────────

    @Test
    void newToPreparingIsValid() {
        assertTrue(OrderStateMachine.canTransition("NEW", "PREPARING"));
    }

    @Test
    void preparingToPackedReadyIsValid() {
        assertTrue(OrderStateMachine.canTransition("PREPARING", "PACKED_READY"));
    }

    @Test
    void packedReadyToCompletedIsValid() {
        assertTrue(OrderStateMachine.canTransition("PACKED_READY", "COMPLETED"));
    }

    @Test
    void newToDeclinedIsValid() {
        assertTrue(OrderStateMachine.canTransition("NEW", "DECLINED"));
    }

    @Test
    void newToCancelledIsValid() {
        assertTrue(OrderStateMachine.canTransition("NEW", "CANCELLED"));
    }

    @Test
    void preparingToCancelledIsValid() {
        assertTrue(OrderStateMachine.canTransition("PREPARING", "CANCELLED"));
    }

    @Test
    void packedReadyToCancelledIsValid() {
        assertTrue(OrderStateMachine.canTransition("PACKED_READY", "CANCELLED"));
    }

    // ─── INVALID TRANSITIONS ───────────────────────────────────

    @Test
    void newToCompletedIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("NEW", "COMPLETED"));
    }

    @Test
    void completedToPreparingIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("COMPLETED", "PREPARING"));
    }

    @Test
    void completedToNewIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("COMPLETED", "NEW"));
    }

    @Test
    void declinedToPreparingIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("DECLINED", "PREPARING"));
    }

    @Test
    void cancelledToPreparingIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("CANCELLED", "PREPARING"));
    }

    @Test
    void preparingToNewIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("PREPARING", "NEW"));
    }

    @Test
    void packedReadyToPreparingIsInvalid() {
        assertFalse(OrderStateMachine.canTransition("PACKED_READY", "PREPARING"));
    }

    // ─── TERMINAL STATES ───────────────────────────────────────

    @Test
    void completedIsTerminal() {
        assertTrue(OrderStateMachine.TERMINAL_STATES.contains("COMPLETED"));
    }

    @Test
    void declinedIsTerminal() {
        assertTrue(OrderStateMachine.TERMINAL_STATES.contains("DECLINED"));
    }

    @Test
    void cancelledIsTerminal() {
        assertTrue(OrderStateMachine.TERMINAL_STATES.contains("CANCELLED"));
    }

    // ─── ROLE RESTRICTIONS ─────────────────────────────────────

    @Test
    void chefCanCookAndPack() {
        assertTrue(OrderStateMachine.canRolePerform("NEW", "PREPARING", "ROLE_CHEF"));
        assertTrue(OrderStateMachine.canRolePerform("PREPARING", "PACKED_READY", "ROLE_CHEF"));
    }

    @Test
    void chefCannotDeclineOrCancel() {
        assertFalse(OrderStateMachine.canRolePerform("NEW", "DECLINED", "ROLE_CHEF"));
        assertFalse(OrderStateMachine.canRolePerform("NEW", "CANCELLED", "ROLE_CHEF"));
        assertFalse(OrderStateMachine.canRolePerform("PREPARING", "CANCELLED", "ROLE_CHEF"));
    }

    @Test
    void chefCannotComplete() {
        assertFalse(OrderStateMachine.canRolePerform("PACKED_READY", "COMPLETED", "ROLE_CHEF"));
    }

    @Test
    void managerCanPerformAllTransitions() {
        assertTrue(OrderStateMachine.canRolePerform("NEW", "PREPARING", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("NEW", "DECLINED", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("NEW", "CANCELLED", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("PREPARING", "PACKED_READY", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("PREPARING", "DECLINED", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("PREPARING", "CANCELLED", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("PACKED_READY", "COMPLETED", "ROLE_MANAGER"));
        assertTrue(OrderStateMachine.canRolePerform("PACKED_READY", "CANCELLED", "ROLE_MANAGER"));
    }

    @Test
    void adminCanPerformAllTransitions() {
        assertTrue(OrderStateMachine.canRolePerform("NEW", "PREPARING", "ROLE_ADMIN"));
        assertTrue(OrderStateMachine.canRolePerform("NEW", "CANCELLED", "ROLE_ADMIN"));
        assertTrue(OrderStateMachine.canRolePerform("PACKED_READY", "COMPLETED", "ROLE_ADMIN"));
    }

    // ─── VALIDATE METHOD ───────────────────────────────────────

    @Test
    void validateThrowsOnInvalidTransition() {
        com.savorystay.config.OrderStateException ex = assertThrows(com.savorystay.config.OrderStateException.class, () ->
                OrderStateMachine.validate("COMPLETED", "PREPARING", "ROLE_MANAGER"));
        assertTrue(ex.getMessage().contains("cannot move"));
    }

    @Test
    void validateThrowsOnUnauthorizedRole() {
        SecurityException ex = assertThrows(SecurityException.class, () ->
                OrderStateMachine.validate("NEW", "CANCELLED", "ROLE_CHEF"));
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    void validateSucceedsForValidTransitionAndRole() {
        assertDoesNotThrow(() ->
                OrderStateMachine.validate("NEW", "PREPARING", "ROLE_CHEF"));
    }

    // ─── EDGE CASES ────────────────────────────────────────────

    @Test
    void nullValuesReturnFalse() {
        assertFalse(OrderStateMachine.canTransition(null, "PREPARING"));
        assertFalse(OrderStateMachine.canTransition("NEW", null));
        assertFalse(OrderStateMachine.canTransition(null, null));
    }

    @Test
    void unknownStatusReturnsFalse() {
        assertFalse(OrderStateMachine.canTransition("UNKNOWN", "PREPARING"));
        assertFalse(OrderStateMachine.canTransition("NEW", "UNKNOWN"));
    }

    @Test
    void allValidStatusesAreRecognized() {
        for (String status : OrderStateMachine.VALID_STATUSES) {
            assertTrue(OrderStateMachine.VALID_STATUSES.contains(status));
        }
    }
}
