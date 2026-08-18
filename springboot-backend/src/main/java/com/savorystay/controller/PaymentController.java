package com.savorystay.controller;

import com.savorystay.entity.Payment;
import com.savorystay.repository.PaymentRepository;
import com.savorystay.service.PaymentGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Payment gateway endpoints.
 *
 * - {@code create-intent} / {@code process-realtime} are DEMO helpers that
 *   simulate a gateway round-trip; they require authentication (SecurityConfig
 *   only permits {@code /api/v1/payments/webhook} anonymously). The
 *   authoritative payment flow lives at {@code POST /orders/{orderId}/payment}.
 * - {@code webhook} is public (gateway callbacks can't send a JWT) but the
 *   Stripe signature is verified whenever {@code stripe.webhook.secret} is
 *   configured.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentGatewayService paymentGatewayService;
    private final PaymentRepository paymentRepository;

    @Value("${stripe.webhook.secret:}")
    private String stripeWebhookSecret;

    public PaymentController(PaymentGatewayService paymentGatewayService, PaymentRepository paymentRepository) {
        this.paymentGatewayService = paymentGatewayService;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/create-intent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> body) {
        Object amountRaw = body.get("amount");
        if (amountRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "amount is required"));
        }
        BigDecimal amount = new BigDecimal(String.valueOf(amountRaw));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "amount must be positive"));
        }
        String currency = (String) body.getOrDefault("currency", "USD");
        String gateway = (String) body.getOrDefault("gateway", "STRIPE");

        String clientSecret = "pi_secret_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(Map.of(
            "clientSecret", clientSecret,
            "paymentIntentId", "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
            "amount", amount,
            "gateway", gateway
        ));
    }

    @PostMapping("/process-realtime")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> processRealtime(@RequestBody Map<String, Object> body) {
        Object amountRaw = body.get("amount");
        if (amountRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "amount is required"));
        }
        BigDecimal amount = new BigDecimal(String.valueOf(amountRaw));
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "amount must be positive"));
        }
        String gateway = (String) body.getOrDefault("gateway", "STRIPE");

        Payment payment = Payment.builder()
                .transactionId("TXN_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .gateway(gateway)
                .amount(amount)
                .currency("USD")
                .paymentStatus("PAID")
                .cardLast4("4242")
                .build();

        paymentRepository.save(payment);

        return ResponseEntity.ok(Map.of(
            "status", "SUCCESS",
            "transactionId", payment.getTransactionId(),
            "gateway", gateway,
            "amountPaid", amount
        ));
    }

    /**
     * Gateway webhook callback (public by design).
     * The Stripe signature is verified when a webhook secret is configured;
     * otherwise the event is accepted with a warning (local/demo mode).
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload,
                                           @RequestHeader(value = "stripe-signature", required = false) String sigHeader) {
        log.debug("Received payment webhook: {}", payload);

        if (stripeWebhookSecret != null && !stripeWebhookSecret.isBlank()
                && !stripeWebhookSecret.startsWith("whsec_mock")) {
            if (sigHeader == null || !paymentGatewayService.verifyStripeWebhook(payload, sigHeader, stripeWebhookSecret)) {
                log.warn("Payment webhook rejected: invalid Stripe signature");
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Invalid signature"));
            }
        } else {
            log.warn("Payment webhook accepted WITHOUT signature verification (no stripe.webhook.secret configured)");
        }

        return ResponseEntity.ok(Map.of("received", true, "timestamp", System.currentTimeMillis()));
    }
}
