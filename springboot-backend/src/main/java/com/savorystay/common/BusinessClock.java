package com.savorystay.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Single source of truth for the restaurant business timezone (default IST,
 * overridable via {@code app.business.timezone}). All pre-order cutoff,
 * availability and closure decisions MUST go through this clock so date
 * boundaries are consistent — never scatter raw {@code LocalDate.now()}
 * calls for business rules.
 */
@Component
public class BusinessClock {

    private final ZoneId zoneId;

    public BusinessClock(@Value("${app.business.timezone:Asia/Kolkata}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    public ZoneId zone() {
        return zoneId;
    }

    /** Today's date in the business timezone. */
    public LocalDate today() {
        return LocalDate.now(zoneId);
    }

    /** Current date-time in the business timezone (no zone suffix). */
    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }
}
