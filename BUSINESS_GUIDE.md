# 🍽️ SavoryStay — Business Guide

*What the platform does, why it helps your restaurant, and how to run your day-to-day operations with it.*

> This guide is written for restaurant owners, managers, and staff — no technical knowledge needed. For setup instructions and code-level details, see `DEVELOPER_GUIDE.md`.

---

## 1. What Is SavoryStay?

SavoryStay is a complete restaurant operations platform that connects three sides of your business in one application:

| Who | What they can do |
|---|---|
| **Guests / Customers** | Browse your menu, pre-book guaranteed pickup slots, pay online (Stripe, PayPal, UPI, Cash), and track their order live — from *preparing* to *packed & ready*. Customers can belong to **multiple restaurants** under one account. The system checks item availability at checkout — if something goes out of stock while you're browsing, you'll see a warning before paying. |
| **Kitchen / Chefs** | See a live order queue, cook → pack orders in order, and plan tomorrow's ingredients from pre-orders. |
| **Management / Owners** | Control the menu, prices, pre-order rules, staff accounts, table configurations, time slots, customer memberships, and (as Super Admin) run an entire chain of restaurants from one login. |

One platform can serve **many restaurants** — each restaurant keeps its own menu, staff, orders, settings, and table configuration, fully isolated from the others. A single customer account can be a member of multiple restaurants and switch between them after login.

---

## 2. The Business Problems It Solves

- **No more walk-in uncertainty** — customers pre-book a guaranteed pickup time instead of waiting or being turned away.
- **Know what to cook tomorrow** — pre-orders are turned into a *raw-ingredient estimate* automatically, so you buy and prep exactly what's needed (no guesswork, less wastage).
- **No duplicate ingredients** — each restaurant maintains a controlled ingredient list. When adding a dish, managers select ingredients from this list (not free text). This prevents spelling mistakes ("rice" vs "Rice" vs "basmati Rice") from creating separate records, keeping stock and recipe calculations accurate.
- **Only cook what you plan to cook** — every dish has its own weekly schedule (e.g. Pulao on Sunday & Wednesday only), so customers can never order something you aren't making that day.
- **Control when orders close** — set a cutoff (e.g. 9:00 AM the day before), so the kitchen isn't surprised by late orders.
- **Handle holidays and half-days cleanly** — mark closed days or a "second half closed" day once; the system blocks pre-orders for those days automatically.
- **The right people, the right powers** — chefs can cook and pack; only managers/admins can change prices or menus. No accidental edits, no chaos.
- **Everything stays in sync, live** — customers, kitchen, and management all see the same real-time state.
- **Manage daily plate limits** — each dish can have a maximum number of plates per day. Once the limit is reached, the dish shows as unavailable. This prevents over-commitment and ensures quality.
- **Track table capacity in real-time** — configure your restaurant's seating (2-seater, 4-seater, 6-seater tables with counts) and the system shows live availability for each time slot.
- **Manage operational supplies** — track not just food ingredients but also handwash liquid, parcel boxes (plastic/aluminum, small/medium/large), napkins, cutlery, and garbage bags. Know when supplies are running low before you run out.

---

## 3. Multi-Restaurant Customer Accounts

### One Account, Many Restaurants

A customer creates a single account with one email and one password. They can then **join multiple restaurants** — each restaurant keeps its own menu, orders, and history, but the customer uses the same login for all of them.

### How It Works

1. **Register** — sign up with username, email, and password (verified via OTP).
2. **Join restaurants** — after first login, the system shows available restaurants. Click "Join" on any restaurant you want to order from.
3. **Select a restaurant** — each time you log in, pick which restaurant you want to order from. Your menu, cart, and order history are scoped to that restaurant.
4. **Switch restaurants** — use the restaurant picker in the header to switch to a different restaurant at any time.

### Why This Matters

- **For customers**: one login across all your favorite restaurants. No need to remember separate accounts.
- **For restaurants**: you see only your own customers and orders. Data is fully isolated between restaurants.
- **For the platform**: a single identity system scales to hundreds of restaurants without duplicate accounts.

### What's Scoped Per Restaurant

| Data | Scoped? | Notes |
|---|---|---|
| Menu | ✅ | Each restaurant has its own menu |
| Orders | ✅ | Your order history is per-restaurant |
| Cart | ✅ | Cart resets when you switch restaurants |
| Pre-order settings | ✅ | Each restaurant has its own hours, cutoff, and schedules |
| Notifications | ✅ | You receive notifications from the restaurant you're currently in |
| Account / password | ❌ | One account works everywhere |

---

## 4. How Pre-Orders Work (The Rules in Plain Language)

### 4.1 Order window
Customers can pre-order for **tomorrow up to a week ahead** (this horizon is adjustable). They pick the *date* and a *pickup time*.

### 4.2 Cutoff time
Orders for a given day **close at the cutoff time on the day before** — by default **9:00 AM** (adjustable per restaurant).

> Example: today is Monday. Customers may pre-order for **Tuesday until Monday 9:00 AM**. After that, Tuesday is closed for pre-orders.

### 4.3 Restaurant open days & half-days
- A day marked **closed** (weekly holiday, festival, etc.) blocks all pre-orders for that day.
- A day that **closes early (by or before 2:00 PM)** is treated as a **"second half closed"** day and also blocks pre-orders.
- On normal days, customers can only pick a pickup time **inside your opening hours** (e.g. between 11:00 AM and 11:00 PM).

### 4.4 Which dishes are cooked on which days
Each dish has a **weekly cooking schedule** — e.g.:

| Dish | Cooked on |
|---|---|
| Pulao | Sunday, Wednesday |
| Butter Chicken | Monday, Wednesday, Friday, Sunday |
| Masala Chai | Every day |
| (any dish with no schedule) | Every day |

Customers can only pre-order a dish for a day it's actually scheduled. The menu shows a **pre-order availability calendar** so guests can see at a glance which days have openings — and they can filter by dish.

### 4.5 Special openings and closures (one-off overrides)
A manager can **open a dish on an extra day** for a special occasion (e.g. Pulao on a one-off Friday), or **close a dish on a day it would normally be available** (e.g. supplier issue). Rules of precedence:

1. If you **explicitly close** a dish for a date → it's closed, no matter what.
2. Otherwise, if you **explicitly open** it → it's open.
3. Otherwise → the normal weekly schedule applies.
4. **Restaurant closure always wins** — a closed/half-closed day blocks everything regardless of dish settings.

### 4.6 Payment methods

| Method | How it works |
|---|---|
| **UPI / Razorpay / Card** | Paid immediately at checkout. Order is confirmed as PAID. |
| **Cash (Pay on Pickup)** | Order is placed but stays **PENDING** until the customer pays at the counter. Restaurant staff mark the order as PAID when cash is collected. |

> **Important:** "Pay on Pickup" orders are never marked as paid automatically. This ensures the payment status accurately reflects when the customer actually pays.

---

## 5. Table Configuration & Time Slots

### 5.1 Table Types
Each restaurant configures its own seating layout:
- **2-Seater tables** — for couples and solo diners
- **4-Seater tables** — for small families and groups
- **6-Seater tables** — for larger parties

The system tracks how many tables of each type are available and how many are booked for a given date + time slot.

### 5.2 Time Slots
- **Pickup orders**: customers choose a wait time (15 Mins, 30 Mins, 45 Mins, 1 Hour, 1.5 Hours)
- **Dine-in orders**: customers choose a time-of-day (12:00 PM, 12:30 PM, 1:00 PM, etc.)

When a customer selects a time slot for dine-in, the system shows real-time table availability. If all tables of a type are booked, that type shows as unavailable.

### 5.3 Plate Availability
Every dish can have a **daily plate limit** — the maximum number of plates that can be ordered per day. For example:
- Butter Chicken: 30 plates/day
- Hyderabadi Biryani: 20 plates/day
- Masala Chai: 80 plates/day

When orders exceed the limit, the dish shows as unavailable to new customers. This prevents over-commitment and ensures quality.

---

## 6. Ingredient Estimation (Smart Prep Planning)

When you configure a dish, you can record the **ingredients needed for one plate**:

> **Chicken Biryani — 1 plate**
> - Rice → 500 g
> - Chicken → 250 g
> - Ghee → 10 g
> - Oil → 10 g

From then on, the system calculates your requirements automatically from real pre-orders:

| Scenario | Result |
|---|---|
| 10 plates of Biryani pre-ordered | Rice 5 kg · Chicken 2.5 kg · Ghee 100 g · Oil 100 g |
| 5 plates of Pulao (rice 300 g/plate) on the same day | + Rice 1.5 kg |
| **Total rice needed that day** | **6.5 kg** (aggregated across dishes) |

The forecast screen shows:
- a **7-day date picker** (next 7 days) — each day chip shows the day name, date, and your operating hours. Closed days (holidays, 2nd-half closures) are greyed out with a `CLOSED` badge.
- each **dish** and how many **plates** are pre-ordered for the selected date,
- each **ingredient** with the **total quantity** needed (in g / kg / ml / litre / pieces…),
- your **current stock** and how much is **short** (what to buy).

**How to use it:** click any open day chip (the closed days are not clickable), then click **Compute Forecast**. The system shows the ingredient shopping list for that day — including holidays sync from your operating hours so you never plan for a day you're closed.

This turns "rough guess" into "exact shopping list" — and it's always recomputed from the *latest* recipe, so if you change a recipe the estimate updates automatically.

---

## 7. Operational Supplies Management

Beyond food ingredients, the platform tracks operational supplies that affect daily operations:

| Category | Items Tracked | Why It Matters |
|---|---|---|
| **Hygiene** | Handwash Liquid, Dishwashing Liquid | Staff health compliance, inspection readiness |
| **Packaging (Plastic)** | Parcel Box Small/Medium/Large | Takeaway orders — different sizes for different order volumes |
| **Packaging (Aluminum)** | Foil Container Small/Medium/Large | Hot food takeaway — keeps food warm longer |
| **Supplies** | Napkins, Tissue Boxes | Customer experience, table setting |
| **Cutlery** | Plastic Spoons, Forks, Knives | Takeaway orders |
| **Bags** | Plastic Bags (Large), Paper Bags (Takeaway) | Order packaging |
| **Other** | Garbage Bags | Waste management |

Each supply item has:
- **Current stock** (e.g., 200 plastic parcel boxes)
- **Reorder level** (e.g., 60 — alert when stock drops below this)
- **Category** (Hygiene, Packaging, Supplies, etc.)

When stock drops below the reorder level, the ingredient planning screen shows a **low stock alert**. This helps managers reorder before running out during peak hours.

---

## 8. Daily Workflow for Staff

### Customer
1. **Sign up or log in** — create an account with username, email, and password (verified via OTP). One account works across all restaurants.
2. **Join restaurants** — after your first login, join the restaurants you want to order from. You can join as many as you like. **Or simply place an order** — most restaurants auto-join you on your first order, so the restaurant appears in your picker automatically.
3. **Select a restaurant** — when you log in, pick which restaurant you want to order from. Your menu, cart, and orders are scoped to that restaurant.
4. **Browse the menu** — veg/non-veg filters, spice levels, categories. See daily plate counts and remaining plates for each dish.
5. Add dishes to the cart (no account needed to browse).
6. Choose **Dine-in, Pickup, or Pre-Order**. For pre-order, pick an available date + pickup time. For dine-in, see real-time table availability. For pickup, choose a wait time.
7. Pay with **Stripe, PayPal, UPI, or Cash (pay at pickup)**.
8. The system checks item availability at checkout — if something went out of stock while browsing, you'll see a warning and can't proceed until you remove it.
9. Watch the order live: *New → Preparing → Packed & Ready → Completed*.

**Switching restaurants**: use the restaurant picker in the header to switch to a different restaurant at any time. Your orders and history are per-restaurant.

### Chef
- **Kitchen Production View** — see a dish-level summary of all active orders: which dishes need how many plates, sorted by urgency (OVERDUE → DUE_SOON → NORMAL).
- **Delay Detection** — the system flags orders past their promised pickup time, showing how many minutes late they are.
- **Kitchen Notes** — add notes to individual items ("Less spicy", "No onion") that are visible in the kitchen view.
- **Sold Out (86)** — mark a dish as sold out instantly. Customers can no longer order it, but existing confirmed orders remain valid.
- **Start Cooking** → **Pack** — that's it. Managers handle completion and handover.

### Manager / Admin
- **Menu & prices** — add dishes, edit prices, upload photos, set veg/non-veg and spice level, define per-plate ingredient recipes. When adding a dish, select ingredients from the restaurant's ingredient master list (not free text). If an ingredient is missing, create it directly from the recipe editor.
- **Ingredients** — manage the restaurant's ingredient master list. Add, edit, deactivate, or reactivate ingredients. Search by name, filter by status. See how many dishes use each ingredient. Stock levels and usage tracking. Track both food ingredients and operational supplies (handwash, parcel boxes, napkins, cutlery).
- **Pre-Orders tab** — set opening hours per day, the cutoff time and booking horizon, each dish's weekly schedule, and open/close special dates.
- **Orders** — complete, decline, or cancel orders. **Cash (Pay on Pickup)** orders stay PENDING until the customer pays at the counter — mark them PAID when cash is collected. **Cancellations** are tracked with who cancelled, when, and why. **Refunds** for paid orders are initiated through the system (not manual).
- **Staff** — create manager/chef accounts, including combined roles (e.g. Manager + Chef).
- **Members** — view and manage customer members who have joined the restaurant. See each member's username, email, phone, and join date. Remove members who should no longer have access (their order history is preserved). A badge on the Members tab shows the live count. Export the member list as CSV.
- **Forecast** — 7-day forecast (pick any date in the next week, closed days are greyed out automatically).
- **Dashboard** — see today's orders, revenue, status breakdown, and tomorrow's pre-order brief at a glance.
- **Shopping List** — see which ingredients need restocking (shortfalls only) for any date.
- **Cash Reconciliation** — expected vs collected cash for any day, with pending breakdown.
- **Payment Reconciliation** — gross, refunds, net, and breakdown by payment method (UPI, Card, Cash).
- **Exception Center** — see all operational exceptions in one place: payment failures, delayed orders, ingredient shortages, pending cash, sold-out dishes.
- **Audit Trail** — see who changed what and when: menu edits, order cancellations, refunds, ingredient changes.
- **Table Configuration** — configure seating types (2-seater, 4-seater, 6-seater) and table counts. Set pickup time slots and dine-in time slots for the checkout modal.

### Owner / Super Admin
- See **every restaurant** in one dashboard.
- Click **Manage** on any restaurant to operate it directly (menu, pre-orders, staff, orders) — no separate login needed.
- Create new restaurants, manage staff across the chain.

---

## 9. Roles at a Glance

| Capability | Customer | Chef | Manager | Admin | Super Admin |
|---|---|---|---|---|---|
| Browse menu, pre-book & pay | ✅ | read-only | read-only | read-only | read-only |
| Place orders | ✅ | ❌ | ❌ | ❌ | ❌ |
| Cancel own orders | ✅ (NEW only) | ❌ | ✅ | ✅ | ✅ |
| Cook & pack orders | — | ✅ | ✅ | ✅ | ✅ |
| Complete / decline / cancel orders | — | ❌ | ✅ | ✅ | ✅ |
| Mark dish sold out (86) | — | ✅ | ✅ | ✅ | ✅ |
| Add kitchen notes | — | ✅ | ✅ | ✅ | ✅ |
| Edit menu, prices, recipes | — | ❌ | ✅ | ✅ | ✅ |
| Manage ingredients / stock | — | ❌ | ✅ | ✅ | ✅ |
| Manage operational supplies | — | ❌ | ✅ | ✅ | ✅ |
| Initiate refunds | — | ❌ | ✅ | ✅ | ✅ |
| View dashboard, reconciliation, audit | — | ❌ | ✅ | ✅ | ✅ |
| Manage staff | — | ❌ | ❌ | ✅ | ✅ |
| View/remove customer members | — | ❌ | ✅ (view) | ✅ | ✅ |
| Configure tables & time slots | — | ❌ | ✅ | ✅ | ✅ |
| Set daily plate limits | — | ❌ | ✅ | ✅ | ✅ |
| Manage all restaurants | — | ❌ | ❌ | ❌ | ✅ |
| Configure pre-orders (hours / cutoff / schedules) | — | ❌ | ✅ | ✅ | ✅ |

A staff member can hold **multiple roles** (e.g. Manager + Chef) and automatically gets the combined powers.

---

## 10. What Customers Experience (Sales Pitch)

- 🗓️ **Pre-book guaranteed pickup slots** — no waiting, no disappointment.
- 🍽️ **See daily plate limits** — know how many of each dish are available today.
- 🔍 **Availability calendar on the menu** — see exactly which days each dish can be pre-ordered.
- 🪑 **Real-time table availability** — see which tables are free for your preferred time slot.
- 🔔 **Real-time order tracking** — know when your food is being cooked and when it's ready.
- 💳 **Pay your way** — cards (Stripe), PayPal, UPI, or cash.
- 📱 **Notifications on every step** — in-app, SMS, WhatsApp, and email.

---

## 11. Getting Started (for the business owner)

1. **The system comes pre-loaded** with two demo restaurants, 50 menu items (25 per restaurant) with dish-specific images, 42 ingredients each (food + operational supplies), full recipes, 94+ orders across all statuses, restaurant settings (table config + time slots), customer memberships, and sample notifications.
2. **Set your real operating hours** in the Pre-Orders tab (or ask the team that sets it up for you).
3. **Review the cutoff time** (default 9:00 AM the day before) and booking horizon (default 7 days). Each restaurant sets its **own** cutoff — the only rule is it can't be later than the restaurant's opening time (orders for a day close on the day before, at your chosen time).
4. **Add your dishes with recipes** — ingredients per plate — so the forecast works.
5. **Set each dish's weekly cooking schedule** — this is what protects you from taking orders for dishes you don't cook that day.
6. **Configure your table layout** — set the number of 2-seater, 4-seater, and 6-seater tables in Restaurant Settings.
7. **Set time slots** — configure pickup wait times and dine-in time-of-day options for the checkout modal.
8. **Set daily plate limits** — for dishes with limited supply (e.g., special biryani), set the maximum plates per day.
9. **Open special days** (festival runs) or **close special days** (holidays) as needed.
10. Watch the **forecast each evening** — use the 7-day date picker to plan ahead. Closed days are automatically greyed out.
11. **Configure email** — for OTP delivery and order notifications, the system uses **Gmail SMTP**. The backend owner needs to set up a Gmail App Password (see the Developer Guide for step-by-step instructions). In development, if email credentials are not configured, the system falls back to demo mode (OTP shown in the console) so everything still works.

---

## 12. Reliability & Trust

- **Payments are verified server-side** — the amount you're charged is confirmed before an order is marked paid. Nobody can fake a payment.
- **Accounts are verified with OTP** — reduces fake registrations; login is rate-limited to stop brute-force attacks.
- **Email notifications** — OTP codes, order updates, and receipts are sent via email. The system always returns the OTP code in the API response as a fallback, so even if emails land in spam, customers can still complete signup.
- **Your data is isolated per restaurant** — staff of one restaurant can never see another's data.
- **Nothing gets lost** — notifications are built on a reliable delivery pipeline with retries, so order updates actually reach customers.
- **Outbox cleanup** — old published events are automatically purged every 6 hours to keep the database lean.

---

## 13. Questions & Answers

**Q: Can a customer pre-order more than a week ahead?**
The booking horizon is configurable (default 7 days). Change it in the Pre-Orders settings.

**Q: What happens at exactly 9:00 AM cutoff?**
Cutoff is inclusive — at 9:00 AM sharp, that day's pre-orders close.

**Q: I want to make Pulao available this Friday as a special.**
Open the dish's slot for that date in the Pre-Orders tab — done. (Assuming the restaurant isn't closed that day.)

**Q: I changed a recipe after pre-orders came in.**
Estimates always use the **current** recipe, so the forecast automatically reflects your latest recipe.

**Q: What prevents duplicate ingredients?**
Each restaurant has an **ingredient master list**. When creating an ingredient, the system normalizes the name (lowercase, trim whitespace, collapse spaces) and checks for duplicates. "Rice", "rice", and " RICE " are all treated as the same ingredient. The database enforces this — duplicates are rejected at both the application and database level.

**Q: Can I create an ingredient while editing a dish?**
Yes. The recipe editor has a "Create New Ingredient" option. You can create a new ingredient without leaving the dish form. After creation, the ingredient is immediately available in the dropdown for selection.

**Q: What happens if I deactivate an ingredient that's used in recipes?**
The ingredient is soft-deleted (marked inactive). Existing recipes and inventory history remain intact. The ingredient won't appear in new recipe selections, but it stays visible in historical data and can be reactivated by a manager.

**Q: Can one restaurant use another restaurant's ingredients?**
No. Each restaurant has its own ingredient master list. The system enforces tenant isolation — a manager at Restaurant A cannot attach an ingredient belonging to Restaurant B.

**Q: Can staff order as customers?**
No — only customer accounts can place orders. Staff accounts are for operating the restaurant.

**Q: Do I need to be technical to use this?**
No. Everything is managed through simple screens: hours grid, weekly checkboxes per dish, date pickers, and the forecast report.

**Q: Can a customer have accounts in multiple restaurants?**
Yes. A customer creates **one account** (one email, one password) and then **joins** multiple restaurants. After login, they pick which restaurant to order from. Their orders, cart, and history are scoped to the selected restaurant. They can switch restaurants at any time using the restaurant picker in the header.

**Q: How does a customer join a restaurant?**
After logging in, the restaurant selector screen shows available restaurants the customer hasn't joined yet. They can click "Join" on any restaurant. Alternatively, a restaurant can be joined via the "Join Another Restaurant" button in the selector.

**Q: Does a customer need to join a restaurant before ordering?**
No. Customers can browse any restaurant's menu and even build a cart as a guest. However, to place an order, they must be signed in. Joining a restaurant is optional but recommended — it enables the restaurant picker for quick switching.

**Q: Can a restaurant see which customers are members?**
Yes. Admins and managers can view the full member list via the **Members** tab in the header. It shows each member's username, email, phone, and when they joined. A badge on the tab shows the live member count.

**Q: What happens if a customer is removed from a restaurant?**
The admin can remove a customer's membership from the Members tab (with a confirmation step). The customer's order history is preserved, but they can no longer select that restaurant after login.

**Q: Are customers automatically added to a restaurant when they order?**
Yes, by default. When a customer places their first order at a restaurant, they are automatically joined as a member. This means the restaurant appears in their picker on subsequent logins. This can be disabled by the restaurant admin if needed.

**Q: How do I manage customer members?**
Go to the **Members** tab (visible to admins and managers). You'll see all customers who have joined your restaurant, with their details. You can search by name, email, or phone, and remove members who should no longer have access. You can also export the member list as a CSV file.

**Q: What happens if an item goes out of stock while a customer is ordering?**
The system checks item availability when the customer opens the checkout screen. If any item is now sold out or removed, a warning banner appears showing which items are unavailable. The customer can't complete the order until they remove those items from their cart. This prevents wasted payment attempts.

**Q: I selected "Pay on Pickup" but the order shows as "Paid".**
This was a bug — it's now fixed. When a customer selects "Pay on Pickup" (Cash), the order stays **PENDING** until restaurant staff mark it as PAID when the customer pays at the counter. This ensures the payment status accurately reflects when cash is collected.

**Q: The ingredient forecast only shows one date. Can I plan for multiple days?**
The forecast screen now has a **7-day date picker**. You can see the next 7 days as clickable chips — each shows the day name, date, and your operating hours. Closed days (holidays, 2nd-half closures) are greyed out with a `CLOSED` badge and can't be clicked. Click any open day, then "Compute Forecast" to see the ingredient shopping list for that day.

**Q: The login form shows old data after logout.**
The login form now resets all fields (email, password, username, phone, OTP codes) when the modal closes, so you always get a clean form on your next login attempt.

**Q: How does the kitchen know which dishes are most urgent?**
The Kitchen Production View shows all active orders grouped by dish, sorted by urgency. **OVERDUE** means the pickup time has passed. **DUE_SOON** means within 30 minutes. **NORMAL** means there's still time. The chef can see exactly how many plates of each dish are needed and which orders they belong to.

**Q: Can a chef mark a dish as sold out?**
Yes. The chef can toggle any dish between Available and Sold Out (86). Customers will see the dish as unavailable and can't order it. **Important:** marking a dish sold out does NOT cancel orders that were already confirmed. Those orders remain valid and will be fulfilled.

**Q: What happens if an order is delayed?**
The system detects when current time passes the promised pickup time. The order is flagged as **delayed** with the number of minutes it's late. Managers see this in the Exception Center and can prioritize accordingly. The customer may also receive a notification.

**Q: Can I cancel an order after it's been placed?**
Customers can cancel orders that are still in NEW status (before cooking starts). Managers and chefs can cancel orders that are NEW, PREPARING, or PACKED_READY. Cancelled orders are tracked with who cancelled them, when, and why. For paid online orders, a refund is initiated automatically.

**Q: How do refunds work?**
When a manager cancels a paid order or manually initiates a refund, the system creates a refund record in REQUESTED status. The refund is processed through the original payment gateway. The order status changes to REFUND_PENDING, then REFUNDED when the provider confirms. The audit trail records who initiated the refund and why.

**Q: Can I see a summary of today's operations?**
Yes. The **Dashboard Summary** shows today's total orders, revenue, status breakdown (pending/preparing/ready/completed), delayed orders, cash payments pending, ingredient shortages, and sold-out dishes — all in one view. It also shows tomorrow's pre-order count and expected revenue.

**Q: What is the Shopping List feature?**
The Shopping List shows ingredients where the required quantity (from tomorrow's pre-orders) exceeds the current stock. It lists only the shortfalls — what you need to buy — with the exact quantities and units. You can also export this as a CSV.

**Q: How does cash reconciliation work?**
At the end of the day, the Cash Reconciliation shows all cash orders: how many, the total expected amount, how many have been paid, and how many are still pending. This helps you verify that cash collected matches what was ordered.

**Q: What is the Exception Center?**
The Exception Center aggregates all operational issues in one place: payment failures, delayed orders, ingredient shortages, pending cash payments, sold-out dishes, and pending refunds. This lets managers quickly see what needs attention without hunting through individual screens.

**Q: Is there an audit trail for changes?**
Yes. Every important change is recorded: who made it, when, what changed, and why. This covers menu edits, order cancellations, refunds, ingredient changes, and inventory adjustments. Managers can view the audit trail from the dashboard.

**Q: How do I configure tables for my restaurant?**
Go to Restaurant Settings (accessible via the settings endpoint). Configure the number of 2-seater, 4-seater, and 6-seater tables. The system uses this to show real-time table availability to customers during checkout.

**Q: What are pickup time slots?**
Pickup time slots are the wait-time options shown to customers when they choose Pickup order type. Examples: "15 Mins", "30 Mins", "1 Hour". These are configurable per restaurant.

**Q: What are dine-in time slots?**
Dine-in time slots are the time-of-day options shown to customers when they choose Dine-in. Examples: "12:00 PM", "7:30 PM", "9:00 PM". These are configurable per restaurant.

**Q: Can I limit the number of plates for a special dish?**
Yes. Set the `dailyPlateCount` for any menu item. For example, if you only prepare 20 portions of Hyderabadi Biryani per day, set the plate count to 20. Once 20 orders include that dish, it shows as unavailable to new customers.

**Q: What operational supplies should I track?**
Track anything you need to fulfill orders: handwash liquid, dishwashing liquid, parcel boxes (plastic and aluminum in various sizes), napkins, tissue boxes, plastic cutlery (spoons, forks, knives), plastic bags, paper bags, and garbage bags. Each has a stock level and reorder alert.

---

*SavoryStay — "Crafted Delicacies, Scheduled for Perfection."*
