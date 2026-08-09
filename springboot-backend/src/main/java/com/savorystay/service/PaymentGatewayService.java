package com.savorystay.service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.orders.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentGatewayService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${paypal.client.id}")
    private String paypalClientId;

    @Value("${paypal.client.secret}")
    private String paypalClientSecret;

    public String createStripePaymentIntent(BigDecimal amount, String currency) throws Exception {
        Stripe.apiKey = stripeSecretKey;
        long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInCents)
            .setCurrency(currency.toLowerCase())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .build();

        PaymentIntent intent = PaymentIntent.create(params);
        return intent.getClientSecret();
    }

    public String createPayPalOrder(BigDecimal amount, String currency) throws Exception {
        PayPalEnvironment environment = new PayPalEnvironment.Sandbox(paypalClientId, paypalClientSecret);
        PayPalHttpClient client = new PayPalHttpClient(environment);

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
            .amountWithBreakdown(new AmountWithBreakdown().currencyCode(currency).value(amount.toString()));
        purchaseUnits.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnits);

        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
        com.paypal.orders.Order order = client.execute(request).result();
        return order.id();
    }

    public boolean verifyStripeWebhook(String payload, String sigHeader, String endpointSecret) {
        try {
            com.stripe.net.Webhook.constructEvent(payload, sigHeader, endpointSecret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
