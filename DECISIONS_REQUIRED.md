# SavoryStay — Decisions Required (PHASE 0)

**Date:** August 19, 2026
**Purpose:** These decisions are required before PHASE 1 implementation can begin.
**Status:** ⏳ AWAITING YOUR ANSWERS

> For each item below, reply with your decision. You can answer multiple at once.
> Copy the question number and your answer, e.g.:
> `#1: Yes, add findByIdAndRestaurantId()`
> `#10: No, skip pickup capacity for demo`

---

## P0 — TENANT ISOLATION

### #1 — Payment endpoint cross-restaurant access

**Current behavior:**
`POST /orders/{orderId}/payment` calls `orderRepository.findById(orderId)` without restaurantId.
The service-level check (`isStaff && restaurantId.equals(order.getRestaurantId())`) blocks cross-restaurant staff, but the order lookup itself is unscoped.

**Question:** Should I add `findByIdAndRestaurantId()` to prevent cross-restaurant order lookup in the payment endpoint?

**My recommendation:** Yes — defensive in-depth. The service check is sufficient but an unscoped lookup is a latent risk.

**Your decision:** _______________________________________________

---

### #2 — Customer ordering from non-member restaurants

**Current behavior:**
`placeOrder()` reads `req.restaurantId()` from the request body for customers. A customer can place an order at ANY restaurant, even one they're not a member of. Auto-join happens after the order is placed.

**Question:** Should customers only be able to order from restaurants they're members of? Or is the current "order first, auto-join after" acceptable?

**My recommendation:** Keep current behavior. Auto-join on first order is a good UX. Adding a pre-check would block customers who haven't joined yet.

**Your decision:** _______________________________________________

---

## P0 — AUTHENTICATION CONSISTENCY

### #3 — Refresh tokens

**Current behavior:**
JWT expires after 24 hours. No refresh token mechanism. After expiry, user must re-login.

**Question:** For the demo, is 24h expiry with no refresh tokens acceptable? Or should I add a simple refresh mechanism?

**My recommendation:** Acceptable for demo. Refresh tokens add complexity (token rotation, storage, revocation) that isn't needed for a demo app.

**Your decision:** _______________________________________________

---

### #4 — SSE token in URL

**Current behavior:**
`GET /api/v1/realtime/stream?token=<JWT>` — JWT is passed as a URL query parameter because EventSource cannot send HTTP headers.

**Question:** Is this acceptable for the demo? The alternative is a short-lived SSE-specific token (more complex).

**My recommendation:** Acceptable for demo. This is a standard limitation of the EventSource API. The token is validated inside the controller. URL logging is the main concern, but that's acceptable for a demo.

**Your decision:** _______________________________________________

---

### #5 — Token revocation (blacklist)

**Current behavior:**
No token blacklist. A logged-out user's token remains valid until 24h expiry.

**Question:** Is this acceptable for the demo?

**My recommendation:** Acceptable for demo. Token revocation requires a Redis blacklist + token family tracking, which is over-engineering for a demo.

**Your decision:** _______________________________________________

---

## P0 — PAYMENT CONSISTENCY

### #6 — Payment providers (conflict resolution)

**Current state:**

| Source | Providers Listed |
|--------|-----------------|
| Backend SDK | Stripe (real), PayPal (real) |
| Frontend UI | UPI / GPay, Razorpay, CASH |
| Backend confirmPayment() | STRIPE, PAYPAL, UPI, CASH, MOCK |
| Documentation | Stripe, PayPal, UPI, Razorpay, Cash |

The `confirmOrderPayment()` endpoint is the real payment flow. The actual checkout calls `placeOrder()` → `confirmOrderPayment()` with whatever gateway string the frontend sends.

**Question:** Which provider set should be the source of truth for the demo?

**My recommendation:** UPI, Razorpay (as mock), CASH — since these are what the frontend shows and are more realistic for an Indian restaurant. Stripe/PayPal can remain as fallback options.

**Your decision:** _______________________________________________

---

### #7 — Remove unused payment endpoints

**Current behavior:**
- `POST /payments/create-intent` — returns mock client secret, not used in checkout flow
- `POST /payments/process-realtime` — creates orphan Payment record (not linked to any order)

**Question:** Should I remove these unused endpoints, or keep them as demo helpers?

**My recommendation:** Remove them. They create confusion (orphan Payment records, mock data that doesn't connect to the real flow). The real flow is `placeOrder()` → `confirmOrderPayment()`.

**Your decision:** _______________________________________________

---

### #8 — Wire Stripe webhook to update order/payment state

**Current behavior:**
`POST /payments/webhook` accepts Stripe events but does NOT update any order or payment state. Just returns `{ received: true }`.

**Question:** Should I wire the webhook to update payment status on Stripe events, or leave it as a stub?

**My recommendation:** Leave as stub. The current flow uses `confirmOrderPayment()` for payment confirmation. Webhook integration is only needed for real Stripe production mode, which isn't the demo goal.

**Your decision:** _______________________________________________

---

### #9 — CASH mark-paid endpoint for staff

**Current behavior:**
CASH orders stay PENDING forever. There is NO endpoint for staff to mark a cash order as PAID when the customer pays at the counter. The order stays in limbo.

**Question:** Should I add `POST /orders/{id}/mark-paid` for staff to mark CASH orders as paid?

**My recommendation:** **YES — this is a gap.** The CASH flow is incomplete without a way to mark it paid. The frontend shows "Pay on Pickup" but there's no backend path to complete the payment lifecycle.

**Your decision:** _______________________________________________

---

## P0 — PICKUP SLOT CAPACITY

### #10 — Pickup capacity limits

**Current behavior:**
No capacity model. Unlimited orders accepted for any time slot.

**Question:** Should I add a simple pickup capacity model (e.g., 12:00–12:15 slot, capacity 5, bookedCount tracking) for the demo?

**My recommendation:** No — skip for demo. Adds a new entity, concurrency logic, and booking management. The current system works without it.

**Your decision:** _______________________________________________

---

## P1 — RECIPE MANAGEMENT

### #11 — Recipe versioning

**Current behavior:**
Current recipe is always used for forecasting. No historical recipe versions. If a recipe is changed, the forecast retroactively reflects the new recipe for past dates.

**Question:** Is recipe versioning needed for the demo?

**My recommendation:** No — skip for demo. The current model is simple and sufficient. Recipe versioning adds significant complexity (version tracking, historical lookups, migration).

**Your decision:** _______________________________________________

---

## P1 — KITCHEN

### #12 — Kitchen stations

**Current behavior:**
No station model. The kitchen production view shows all dishes in a single list grouped by urgency.

**Question:** Should I add a simple station model (GRILL, CURRY, RICE, BEVERAGE, PACKING) for the demo?

**My recommendation:** No — skip for demo. Adds a new entity, assignment logic, and frontend filtering. The current dish-level view with urgency badges is sufficient.

**Your decision:** _______________________________________________

---

### #13 — Formal Batch cooking entity

**Current behavior:**
The kitchen production view already aggregates orders by dish (e.g., "Biryani × 10"). But there's no explicit batch tracking entity.

**Question:** Should I add a formal Batch entity, or is the current aggregation sufficient?

**My recommendation:** No — the current aggregation already provides batch visibility. A formal Batch entity adds unnecessary complexity for the demo.

**Your decision:** _______________________________________________

---

### #14 — Quantity-based sold-out

**Current behavior:**
Binary: `menuItem.status = "Available" | "Sold Out"`. No dish-level quantity tracking.

**Question:** Should I add dish-level availability tracking (Available/Reserved/Remaining based on ingredient stock)?

**My recommendation:** No — skip for demo. The current binary sold-out works. Quantity-based requires ingredient-to-dish mapping at the availability level and adds complexity.

**Your decision:** _______________________________________________

---

## P1 — CUSTOMER EXPERIENCE

### #15 — Pickup PIN/QR verification

**Current behavior:**
No pickup verification. Staff hand over food based on order number.

**Question:** Should I add a pickup PIN or QR code for the demo?

**My recommendation:** No — skip for demo. Adds a new field to Order, a verification endpoint, and frontend UI. The current "show order number" flow is sufficient for a demo.

**Your decision:** _______________________________________________

---

### #16 — Customer ETA estimation

**Current behavior:**
Customers see `pickupTime` and the kitchen view shows urgency (OVERDUE/DUE_SOON/NORMAL). No estimated preparation time.

**Question:** Should I add a simple ETA estimate (e.g., based on kitchen queue position + average prep time)?

**My recommendation:** No — skip for demo. Adds estimation logic that would be inaccurate without real preparation time data. The current pickup time is sufficient.

**Your decision:** _______________________________________________

---

### #17 — "Order Again" reorder feature

**Current behavior:**
No reorder button or flow in the order history.

**Question:** Should I add an "Order Again" feature that re-checks availability and current prices before recreating the order?

**My recommendation:** **Yes — this is a good UX improvement.** The backend already has `checkCartAvailability` and `getEffectivePrice`. The frontend just needs a "Reorder" button that adds items to cart (re-checking availability/prices).

**Your decision:** _______________________________________________

---

### #18 — Structured customization options

**Current behavior:**
Free-text `OrderItem.notes` field ("Less spicy", "No onion"). `MenuItem` has `spiceLevel` and `isVeg` but no structured customization.

**Question:** Should I add structured options (spice level: Mild/Medium/Hot, add-ons: Extra Cheese, removals: No Onion)?

**My recommendation:** No — skip for demo. Free-text notes are sufficient. Structured customization adds a new entity, frontend UI, and pricing logic.

**Your decision:** _______________________________________________

---

## P1 — MANAGER / ADMIN

### #19 — Additional dashboard metrics

**Current metrics:** Total orders, revenue, status breakdown, delayed, cash pending, ingredient shortages, sold-out, tomorrow's brief.

**Missing metrics:** Average Order Value, Average Preparation Time, On-time %, Cancellation %, Refund %.

**Question:** Should I add these additional metrics to the manager dashboard?

**My recommendation:** **Yes for AOV and Cancellation %** — these are simple to compute and valuable. Skip Avg Prep Time and On-time % (no preparation time tracking exists). Refund % is trivial to add.

**Your decision:** _______________________________________________

---

### #20 — Inventory visibility (reserved/available/consumed)

**Current behavior:**
`Ingredient` has `stockQuantity` and `reorderLevel`. No reserved/available/consumed breakdown.

**Question:** Should I add reserved/available/consumed breakdown to the inventory view?

**My recommendation:** No — skip for demo. The current stock/reorder model is sufficient. Adding reserved requires tracking reservations separately, which adds complexity.

**Your decision:** _______________________________________________

---

### #21 — Procurement module

**Current behavior:**
No Supplier, PurchaseOrder, or GoodsReceipt entities. The shopping list shows shortfalls but there's no procurement flow.

**Question:** Should I add a simple procurement module (Supplier → PurchaseOrder → GoodsReceipt → Inventory) for the demo?

**My recommendation:** No — skip for demo. This is a significant new domain. The shopping list is sufficient for showing what needs to be bought.

**Your decision:** _______________________________________________

---

### #22 — DINE_IN table management

**Current behavior:**
`orderType = "DINE_IN"` is a label. `Order` has `tableNumber` and `guests` fields. No `RestaurantTable`, `DiningArea`, or table status model.

**Question:** Is DINE_IN purely an order type (no table management), or should I add basic table management?

**My recommendation:** No — keep DINE_IN as purely an order type. Table management adds significant new domain (tables, areas, status, QR ordering) that isn't needed for the demo.

**Your decision:** _______________________________________________

---

## SUMMARY

| # | Topic | My Recommendation | Your Decision |
|---|-------|-------------------|---------------|
| 1 | Payment cross-restaurant | Yes, add findByIdAndRestaurantId | ⬜ |
| 2 | Customer ordering | Keep current (auto-join) | ⬜ |
| 3 | Refresh tokens | Skip for demo | ⬜ |
| 4 | SSE token in URL | Acceptable for demo | ⬜ |
| 5 | Token revocation | Skip for demo | ⬜ |
| 6 | Payment providers | UPI/Razorpay(mock)/CASH | ⬜ |
| 7 | Remove unused endpoints | Yes, remove | ⬜ |
| 8 | Stripe webhook | Leave as stub | ⬜ |
| 9 | CASH mark-paid | **Yes, add endpoint** | ⬜ |
| 10 | Pickup capacity | Skip for demo | ⬜ |
| 11 | Recipe versioning | Skip for demo | ⬜ |
| 12 | Kitchen stations | Skip for demo | ⬜ |
| 13 | Batch cooking | Skip (aggregation exists) | ⬜ |
| 14 | Quantity sold-out | Skip for demo | ⬜ |
| 15 | Pickup PIN/QR | Skip for demo | ⬜ |
| 16 | Customer ETA | Skip for demo | ⬜ |
| 17 | Reorder feature | **Yes, add** | ⬜ |
| 18 | Structured customization | Skip for demo | ⬜ |
| 19 | Dashboard metrics | **Yes for AOV/Cancel%/Refund%** | ⬜ |
| 20 | Inventory visibility | Skip for demo | ⬜ |
| 21 | Procurement | Skip for demo | ⬜ |
| 22 | DINE_IN tables | Skip (order type only) | ⬜ |

---

## ADDITIONAL FINDINGS (no decision needed — I'll fix these)

These are clear issues I'll address regardless of your answers:

| Finding | Action |
|---------|--------|
| Dashboard endpoints load ALL orders into memory | Add date-bounded SQL queries |
| `PaymentController.processRealtime()` creates orphan Payment | Remove (see #7) |
| `OrderController.cancelOrder()` duplicates state machine logic | Refactor to delegate to OrderStateMachine |
| Missing tenant isolation tests | Add integration tests |
| Missing payment idempotency tests | Add tests for duplicate requests |
| Missing concurrent inventory test | Add test for simultaneous orders |
| Documentation conflicts with code (providers, email, etc.) | Reconcile after decisions |
| No audit trail for menu/price/recipe/staff/membership changes | Add AuditService calls |

---

*SavoryStay — "Crafted Delicacies, Scheduled for Perfection."*
