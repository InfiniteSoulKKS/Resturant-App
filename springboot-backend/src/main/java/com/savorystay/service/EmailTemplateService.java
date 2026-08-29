package com.savorystay.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Renders branded, mobile-friendly HTML emails for every notification type
 * dispatched by the Kafka consumers through Gmail SMTP.
 *
 * All styling is INLINE (email clients strip &lt;style&gt; blocks), and the
 * layout is a single-column table so it renders correctly in Gmail, Outlook
 * and mobile mail apps.
 */
@Service
public class EmailTemplateService {

    @Value("${app.name}")
    private String appName;

    @Value("${app.url:http://localhost:5173}")
    private String appUrl;

    private static final String BRAND_AMBER = "#f59e0b";
    private static final String BRAND_DARK = "#1f2937";
    private static final String BODY_BG = "#f4f1ea";
    private static final String CARD_BG = "#ffffff";
    private static final String TEXT = "#374151";
    private static final String MUTED = "#6b7280";

    // ------------------------------------------------------------------ OTP

    /** OTP verification email — the 6-digit code in a prominent box. */
    public String otpEmail(String otpCode, String purpose) {
        String heading = "Your verification code";
        String lead = purpose != null && purpose.equalsIgnoreCase("LOGIN")
                ? "Use this code to sign in to your " + appName + " account."
                : "Welcome to " + appName + " — use this code to complete your registration.";
        return wrap(heading, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">%s</p>
                <div style="margin:0 auto 24px;max-width:260px;padding:18px 24px;background:#fffbeb;border:2px dashed %s;border-radius:12px;text-align:center">
                    <div style="font-size:10px;text-transform:uppercase;letter-spacing:2px;color:%s;margin-bottom:6px">Your OTP</div>
                    <div style="font-size:34px;font-weight:800;letter-spacing:10px;color:%s;font-family:monospace">%s</div>
                </div>
                <p style="margin:0 0 8px;font-size:13px;color:%s;line-height:1.6">This code is valid for <strong>5 minutes</strong>. For your security, please do not share it with anyone — even someone claiming to be from %s.</p>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">If you did not request this code, you can safely ignore this email.</p>
                """.formatted(TEXT, lead, BRAND_AMBER, BRAND_AMBER, BRAND_AMBER, otpCode, MUTED, appName, MUTED));
    }

    // ---------------------------------------------------------------- Orders

    /** Order confirmation / status update email. Amount may be null on pure status moves. */
    public String orderEmail(String orderNumber, BigDecimal amount, String orderType,
                             String statusLabel, String customerName) {
        String heading = statusLabel != null && statusLabel.contains("ready")
                ? "Your order is ready! 🎉"
                : "Order " + statusLabel + " — " + orderNumber;
        // Total row is only rendered when the amount is known (status-change
        // events carry it too, but stay safe for any legacy/missing payloads).
        String totalRow = amount != null
                ? "<tr><td style=\"padding:4px 0\"><span style=\"font-size:12px;color:" + MUTED + "\">Total</span></td>"
                + "<td align=\"right\" style=\"padding:4px 0\"><strong style=\"font-size:15px;color:" + BRAND_AMBER + "\">₹" + amount + "</strong></td></tr>"
                : "";
        return wrap(heading, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">Hi %s,</p>
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;padding:20px 24px;margin:0 0 24px">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Order</span></td>
                            <td align="right" style="padding:4px 0"><strong style="font-size:14px;color:%s">%s</strong></td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Status</span></td>
                            <td align="right" style="padding:4px 0"><span style="font-size:13px;color:%s;text-transform:capitalize">%s</span></td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Type</span></td>
                            <td align="right" style="padding:4px 0"><span style="font-size:13px;color:%s">%s</span></td>
                        </tr>
                        %s
                    </table>
                </div>
                <p style="margin:0 0 8px;font-size:13px;color:%s;line-height:1.6">We'll keep you posted at every step — from the kitchen to pickup. You can also track it live in the app.</p>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">Thanks for choosing %s! 🍽️</p>
                """.formatted(TEXT, safeName(customerName), MUTED, TEXT, orderNumber,
                MUTED, TEXT, statusLabel, MUTED, TEXT, orderType, totalRow,
                MUTED, MUTED, appName));
    }

    /** Order ready — celebratory pickup instructions. */
    public String orderReadyEmail(String orderNumber, String orderType, String customerName) {
        String pickupMsg = "DINE_IN".equalsIgnoreCase(orderType)
                ? "Your table will be served shortly — enjoy!"
                : "Please collect it from the restaurant counter. It's hot and waiting for you!";
        return wrap("Your order is ready! 🎉", """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">Great news %s!</p>
                <div style="margin:0 auto 24px;padding:22px 28px;background:#fffbeb;border:1px solid #fde68a;border-radius:12px;text-align:center">
                    <div style="font-size:32px;margin-bottom:8px">🍱</div>
                    <div style="font-size:18px;font-weight:700;color:%s">%s is packed &amp; ready</div>
                </div>
                <p style="margin:0 0 8px;font-size:14px;color:%s;line-height:1.6">%s</p>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">We hope it tastes as good as it smells. Bon appétit from the %s team!</p>
                """.formatted(TEXT, safeName(customerName), BRAND_DARK, orderNumber,
                TEXT, pickupMsg, MUTED, appName));
    }

    // ---------------------------------------------------------------- Payments

    /** Payment receipt email. */
    public String receiptEmail(String orderNumber, BigDecimal amount, String gateway, String customerName) {
        return wrap("Payment received ✅", """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">Hi %s, your payment was successful.</p>
                <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;padding:20px 24px;margin:0 0 24px">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Order</span></td>
                            <td align="right" style="padding:4px 0"><strong style="font-size:14px;color:%s">%s</strong></td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Amount paid</span></td>
                            <td align="right" style="padding:4px 0"><strong style="font-size:16px;color:%s">₹%s</strong></td>
                        </tr>
                        <tr>
                            <td style="padding:4px 0"><span style="font-size:12px;color:%s">Method</span></td>
                            <td align="right" style="padding:4px 0"><span style="font-size:13px;color:%s">%s</span></td>
                        </tr>
                    </table>
                </div>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">This is your receipt — keep it for your records. Questions about your order? Reply to this email and we'll help.</p>
                """.formatted(TEXT, safeName(customerName), MUTED, TEXT, orderNumber,
                MUTED, BRAND_AMBER, amount, MUTED, TEXT, gateway != null ? gateway : "Card", MUTED));
    }

    // -------------------------------------------------------------- Inventory

    /** Low-stock alert for restaurant managers. */
    public String inventoryAlertEmail(String ingredientName, BigDecimal stock, BigDecimal reorderLevel) {
        return wrap("⚠️ Low stock: " + ingredientName, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">One of your ingredients is running low and needs attention.</p>
                <div style="margin:0 auto 24px;padding:22px 28px;background:#fff7ed;border:1px solid #fed7aa;border-radius:12px;text-align:center">
                    <div style="font-size:28px;margin-bottom:6px">🧂</div>
                    <div style="font-size:18px;font-weight:700;color:%s">%s</div>
                </div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;margin:0 0 24px">
                    <tr>
                        <td align="center" style="padding:16px;border-right:1px solid #e5e7eb">
                            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:%s;margin-bottom:4px">Current stock</div>
                            <div style="font-size:22px;font-weight:700;color:%s">%s</div>
                        </td>
                        <td align="center" style="padding:16px">
                            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:%s;margin-bottom:4px">Reorder level</div>
                            <div style="font-size:22px;font-weight:700;color:%s">%s</div>
                        </td>
                    </tr>
                </table>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">Please place a restock order so your kitchen never runs dry.</p>
                """.formatted(TEXT, BRAND_DARK, ingredientName,
                MUTED, BRAND_AMBER, stock, MUTED, TEXT, reorderLevel, MUTED));
    }

    // --------------------------------------------------------- Restock

    /** Restock request email — sent to the restaurant when kitchen staff triggers a restock. */
    public String restockRequestEmail(String ingredientName, BigDecimal currentStock,
                                       BigDecimal reorderLevel, String unit,
                                       String requestedBy, String restaurantName) {
        return wrap("🔄 Restock Request: " + ingredientName, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">The kitchen has requested a restock for an ingredient that is running low.</p>
                <div style="margin:0 auto 24px;padding:22px 28px;background:#eff6ff;border:1px solid #bfdbfe;border-radius:12px;text-align:center">
                    <div style="font-size:28px;margin-bottom:6px">📦</div>
                    <div style="font-size:18px;font-weight:700;color:%s">%s</div>
                </div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;margin:0 0 24px">
                    <tr>
                        <td align="center" style="padding:16px;border-right:1px solid #e5e7eb">
                            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:%s;margin-bottom:4px">Current Stock</div>
                            <div style="font-size:22px;font-weight:700;color:%s">%s %s</div>
                        </td>
                        <td align="center" style="padding:16px;border-right:1px solid #e5e7eb">
                            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:%s;margin-bottom:4px">Reorder Level</div>
                            <div style="font-size:22px;font-weight:700;color:%s">%s %s</div>
                        </td>
                        <td align="center" style="padding:16px">
                            <div style="font-size:11px;text-transform:uppercase;letter-spacing:1px;color:%s;margin-bottom:4px">Requested By</div>
                            <div style="font-size:14px;font-weight:700;color:%s">%s</div>
                        </td>
                    </tr>
                </table>
                <p style="margin:0 0 8px;font-size:13px;color:%s;line-height:1.6">Please place a restock order with your supplier as soon as possible to avoid running out during service.</p>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">— %s Kitchen Team</p>
                """.formatted(TEXT, BRAND_DARK, ingredientName,
                MUTED, BRAND_AMBER, currentStock, unit,
                MUTED, TEXT, reorderLevel, unit,
                MUTED, TEXT, requestedBy,
                MUTED, MUTED, restaurantName));
    }

    // ---------------------------------------------------------------- Generic

    /** Fallback branded email used by NotificationService when no specific template matches. */
    public String genericEmail(String title, String message) {
        return wrap(title != null ? title : appName, """
                <p style="margin:0 0 20px;font-size:15px;line-height:1.6;color:%s">%s</p>
                <p style="margin:0;font-size:13px;color:%s;line-height:1.6">— %s team</p>
                """.formatted(TEXT, message != null ? message : "", MUTED, appName));
    }

    // ----------------------------------------------------------------- Layout

    /**
     * Branded single-column layout with the SavoryStay header + footer.
     * Every consumer email is rendered through this shell.
     */
    private String wrap(String heading, String bodyHtml) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s">
                    <tr>
                      <td align="center" style="padding:32px 16px">
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:%s;border-radius:16px;overflow:hidden;border:1px solid #ece9e2">
                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#111827,#1f2937);padding:26px 32px">
                              <table role="presentation" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="font-size:22px;font-weight:800;color:%s">🍽️ %s</td>
                                </tr>
                                <tr>
                                  <td style="font-size:11px;color:#9ca3af;letter-spacing:1px;padding-top:4px">CULINARY OPERATIONS</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <!-- Body -->
                          <tr>
                            <td style="padding:32px">
                              <h1 style="margin:0 0 18px;font-size:22px;font-weight:800;color:%s;line-height:1.3">%s</h1>
                              %s
                            </td>
                          </tr>
                          <!-- Footer -->
                          <tr>
                            <td style="padding:20px 32px;background:#f9fafb;border-top:1px solid #ece9e2">
                              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td style="font-size:11px;color:%s;line-height:1.7">
                                    You're receiving this because you use %s.<br>
                                    © 2026 %s · <a href="%s" style="color:%s;text-decoration:none">Open the app</a>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(BODY_BG, TEXT, BODY_BG, CARD_BG, BRAND_AMBER, appName,
                BRAND_DARK, heading, bodyHtml,
                MUTED, appName, appName, appUrl, BRAND_AMBER);
    }

    private String safeName(String name) {
        return name != null && !name.isBlank() ? name : "there";
    }
}
