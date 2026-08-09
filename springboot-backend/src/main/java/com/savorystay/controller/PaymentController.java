package com.savorystay.controller;

import com.savorystay.entity.Payment;
import com.savorystay.repository.PaymentRepository;
import com.savorystay.service.PaymentGatewayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentGatewayService paymentGatewayService;
    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentGatewayService paymentGatewayService, PaymentRepository paymentRepository) {
        this.paymentGatewayService = paymentGatewayService;
        this.paymentRepository = paymentRepository;
    }

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String currency = (String) body.getOrDefault("currency", "USD");
        String gateway = (String) body.getOrDefault("gateway", "STRIPE");

        String clientSecret = "pi_secret_" + UUID.randomUUID().toString().replace("-", "");
        return ResponseEntity.ok(Map.of(
            "clientSecret", clientSecret,
            "paymentIntentId", "pi_" + System.currentTimeMillis(),
            "amount", amount,
            "gateway", gateway
        ));
    }

    @PostMapping("/process-realtime")
    public ResponseEntity<?> processRealtime(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String gateway = (String) body.getOrDefault("gateway", "STRIPE");

        Payment payment = Payment.builder()
                .transactionId("TXN_" + System.currentTimeMillis())
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

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(@RequestBody String payload, @RequestHeader(value = "stripe-signature", required = false) String sigHeader) {
        System.out.println("Received Real-Time Stripe Webhook Payload: " + payload);
        return ResponseEntity.ok(Map.of("received", true, "timestamp", System.currentTimeMillis()));
    }
}
