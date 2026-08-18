package com.savorystay.dto;

/** Enable/disable payload for a staff account. */
public record SetStaffEnabledRequest(
        Boolean enabled,

        /** Optional — lets a super admin scope the change explicitly. */
        String restaurantId) {
}
