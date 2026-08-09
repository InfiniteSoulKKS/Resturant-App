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

    @Value("${stripe.secret.key:sk_test_mock_secret_key_savorystay}")
    private String stripeSecretKey;

    @Value("${paypal.client.id:mock_paypal_client_id}")
    private String paypalClientId;

    @Value("${paypal.client.secret:mock_paypal_client_secret}")
    private String paypalClientSecret;

    public String createStripePaymentIntent(BigDecimal amount, String currency) throws Exception {
        Stripe.apiKey = (stripeSecretKey != null && !stripeSecretKey.isBlank()) ? stripeSecretKey : "sk_test_mock_secret_key_savorystay";
        long amountInCents = amount.multiply(new BigDecimal("100")).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(amountInCents)
            .setCurrency(currency != null ? currency.toLowerCase() : "usd")
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);
            return intent.getClientSecret();
        } catch (Exception e) {
            return "pi_secret_mock_" + System.currentTimeMillis();
        }
    }

    public String createPayPalOrder(BigDecimal amount, String currency) throws Exception {
        String clientId = (paypalClientId != null && !paypalClientId.isBlank()) ? paypalClientId : "mock_paypal_client_id";
        String clientSecret = (paypalClientSecret != null && !paypalClientSecret.isBlank()) ? paypalClientSecret : "mock_paypal_client_secret";

        PayPalEnvironment environment = new PayPalEnvironment.Sandbox(clientId, clientSecret);
        PayPalHttpClient client = new PayPalHttpClient(environment);

        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");

        List<PurchaseUnitRequest> purchaseUnits = new ArrayList<>();
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
            .amountWithBreakdown(new AmountWithBreakdown().currencyCode(currency != null ? currency : "USD").value(amount.toString()));
        purchaseUnits.add(purchaseUnitRequest);
        orderRequest.purchaseUnits(purchaseUnits);

        OrdersCreateRequest request = new OrdersCreateRequest().requestBody(orderRequest);
        try {
            com.paypal.orders.Order order = client.execute(request).result();
            return order.id();
        } catch (Exception e) {
            return "PAYPAL_MOCK_ORDER_" + System.currentTimeMillis();
        }
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
