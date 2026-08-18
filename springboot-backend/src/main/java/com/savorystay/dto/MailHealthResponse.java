package com.savorystay.dto;

import java.time.LocalDateTime;

/**
 * View model for the on-demand mailer health check ({@code GET /api/v1/health/mail}).
 *
 * The SMTP password is never part of this response — only the account username
 * (used as the From address) and connectivity details are exposed.
 */
public record MailHealthResponse(
        boolean configured,
        boolean reachable,
        String status,
        String host,
        Integer port,
        String username,
        Long latencyMs,
        String message,
        LocalDateTime checkedAt) {

    public static MailHealthResponse notConfigured(String username) {
        return new MailHealthResponse(false, false, "NOT_CONFIGURED",
                null, null, username, null,
                "SMTP not configured (MAIL_USERNAME missing or still a placeholder)", LocalDateTime.now());
    }

    public static MailHealthResponse up(String host, int port, String username, long latencyMs) {
        return new MailHealthResponse(true, true, "UP",
                host, port, username, latencyMs,
                "SMTP connection and authentication OK", LocalDateTime.now());
    }

    public static MailHealthResponse down(String host, int port, String username, long latencyMs, String error) {
        return new MailHealthResponse(true, false, "DOWN",
                host, port, username, latencyMs, error, LocalDateTime.now());
    }
}
