# 🛠️ SavoryStay — Developer Guide

**A multi-restaurant culinary operations platform** — pre-booking, live kitchen tracking, real-time payments, and multi-channel notifications.

This is the single developer-facing reference for the project. It replaces all previous documentation files.

---

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Frontend | React 19 + TypeScript + Vite 6 + Tailwind CSS 4 + lucide-react |
| Backend | Spring Boot 3.2 (Java 17), Spring Security 6.2, Spring Data JPA, Spring Mail, Spring Kafka |
| Database | MySQL 8 (Hibernate `ddl-auto: update`) |
| Cache / rate-limit | Redis (optional at runtime — fails open) |
| Auth | JWT (JJWT 0.11.5) + BCrypt (strength 12) |
| OTP | ElasticEmail SMTP (email), Twilio (SMS + WhatsApp) |
| Payments | Stripe Java SDK v24, PayPal Checkout SDK v2.0, UPI / Cash (pay-on-pickup) / Mock |
| Realtime | Server-Sent Events (SSE) + transactional outbox → Kafka |
| Tests | JUnit 5, Mockito, Testcontainers (Redis) |

---

## 2. Architecture

```
 Browser (React SPA, Vite :5173)
      │  HTTPS /api/v1/*  (Bearer JWT)
      ▼
 Spring Boot 3.2 (:8080)
 ├── Security: JwtAuthenticationFilter → TenantContextFilter → Method Security (@PreAuthorize)
 ├── Controllers: Auth, Otp, Restaurant, Menu, Order, Payment, Ingredient, Staff, Notification, PreOrder,
 │                CustomerRestaurant
 ├── Services: Otp, Order, Menu, Ingredient, PaymentGateway, Restaurant, Notification, Realtime,
 │             ChannelDelivery, EmailTemplate, PreOrderAvailability, PreOrderConfig, Outbox,
 │             CustomerRestaurant
 ├── Schedulers: OtpCleanupScheduler (15 min), OutboxPoller (3 s), PreOrderReminderScheduler (daily 08:45 IST)
 └── JPA Repositories → MySQL
           │
           ├── Redis — rate limits / lockout state (fail-open)
           ├── Kafka — event backbone for notifications (see §7)
           ├── Stripe / PayPal / Twilio / SMTP — external providers
           └── SSE → browser realtime stream
```

**Key architectural decisions**

- **Multi-tenant**: one codebase serves many restaurants. `restaurant_id` scopes every table; `TenantContext` (ThreadLocal, set by `TenantContextFilter`) carries the current `userId` / `restaurantId` / `role` per request. Super admins may override scope via a `restaurantId` request param.
- **Customer–restaurant membership**: customers have a single identity (one login, one email, one password) but can belong to **multiple restaurants** via the `customer_restaurant` join table. After login, the customer selects which restaurant to operate in, and a restaurant-scoped JWT is issued. This keeps tenant isolation intact while letting one customer account serve many restaurants. **Auto-join**: when a restaurant has `autoJoinCustomers = true` (the default), customers are automatically added to the restaurant on their first order, so subsequent logins show the restaurant in the picker.
- **Server-authoritative payments**: `POST /api/v1/orders` always creates orders as `PENDING`. Only `confirmPayment` (which verifies ownership + amount) can mark them `PAID`.
- **Transactional outbox**: business writes (order created, status changed, OTP generated, payment confirmed, stock low) are recorded in the same DB transaction as `outbox_event`. `OutboxPoller` publishes them to Kafka → consumers dispatch Gmail/SMS/WhatsApp/SSE. At-least-once delivery, retries + dead-letter topics.
- **Single source of truth for pre-order rules**: `PreOrderAvailabilityService` — cutoff, horizon, closures, dish availability, slot overrides (see §6).
- **Business timezone**: `BusinessClock` (default `Asia/Kolkata`, overridable via `app.business.timezone`). Never scatter `LocalDate.now()` for business rules.

---

## 3. Project Structure

```
.
├── src/                                # Frontend (React SPA)
│   ├── App.tsx                         # Root: tabs, auth state, cart, realtime SSE wiring
│   ├── types.ts                        # Shared TypeScript domain types
│   ├── lib/
│   │   ├── apiClient.ts                # All backend API calls (fetch wrapper)
│   │   ├── tokenManager.ts             # JWT storage + authenticatedFetch()
│   │   └── roles.ts                    # Role parsing/checks (comma-separated roles)
│   ├── hooks/useRealtimeNotifications.ts  # SSE client for live events
│   ├── data/initialData.ts             # Fallback demo data
│   └── components/
│       ├── Header.tsx / BottomNav.tsx  # Navigation (tabs are role-scoped)
│       ├── AuthModal.tsx               # Login / register / OTP
│       ├── CustomerMenuView.tsx        # Customer menu + cart
│       ├── RealtimePaymentModal.tsx    # Checkout: DINE_IN / PICKUP / PRE_ORDER
│       ├── OrderTracking.tsx           # Customer order progress
│       ├── PreBookingsDashboard.tsx    # Staff order dashboard
│       ├── ChefPrepSummary.tsx         # Legacy prep view (fallback)
│       ├── IngredientPlanning.tsx      # Stock + forecast (date picker + per-dish breakdown)
│       ├── MenuManagement.tsx          # CRUD menu + recipe editor (per-plate ingredients)
│       ├── PreOrderSettings.tsx        # Hours, cutoff, dish availability, slot overrides
│       ├── StaffManagement.tsx         # Admin: staff accounts
│       ├── SuperAdminDashboard.tsx     # Super admin: restaurant chain
│       ├── NotificationsBell.tsx       # Notification center
│       ├── RestaurantPicker.tsx        # In-header restaurant dropdown
│       ├── RestaurantSelector.tsx      # Post-login modal: pick/join a restaurant
│       ├── CustomerMembershipManager.tsx # Admin: view/remove customer members
│       ├── BackendInspectorModal.tsx   # Dev tool
│       └── AuthModal.tsx
├── springboot-backend/                 # Backend (Spring Boot)
│   ├── pom.xml
│   ├── .env.example                    # All environment variables documented
│   └── src/main/java/com/savorystay/
│       ├── SavoryStayApplication.java  # Main + startup secret validation
│       ├── common/ (BusinessClock, IdGenerator, IngredientNormalization, UnitConverter)
│       ├── config/ (SecurityConfig, DataSeeder, GlobalExceptionHandler, KafkaTopicConfig)
│       ├── controller/ (Auth, Otp, Restaurant, Menu, Order, Payment, Ingredient, Staff, Notification, PreOrder, Health)
│       ├── security/ (JwtTokenProvider, JwtAuthenticationFilter, RoleUtils)
│       ├── tenant/ (TenantContext, TenantContextFilter)│   ├── entity/ (User, Restaurant, MenuItem, MenuItemIngredient, Order, OrderItem, Payment,
│       │            Ingredient, InventoryLedger, Notification, PriceRule, OtpRequest,
│       │            RestaurantOperatingHour, PreOrderSettings, DishAvailability, DishSlotOverride,
│       │            CustomerRestaurant, …)
│       ├── repository/ (20+ Spring Data JPA interfaces)
│       ├── service/ (all business logic)
│       ├── scheduler/ (OutboxPoller, OtpCleanupScheduler, PreOrderReminderScheduler)
│       └── consumer/ (Kafka consumers + DltRecorder)
│   └── src/main/resources/
│       ├── application.yml             # Config (env-var driven)
│       └── schema.sql                  # MySQL DDL (runs with ddl-auto, safe CREATE IF NOT EXISTS)
└── docker-compose.yml                  # Local Redis + Kafka (KRaft) + Kafka UI (:8081)
```

---

## 4. Local Setup

### Prerequisites
- Java 17+, Maven 3.8+
- Node.js 18+ (Vite 6)
- MySQL 8 running on `localhost:3306`
- Redis (optional; rate limiting fails open without it)
- Docker (optional; for Kafka — required for real-time notification delivery)

### 1. Backend environment (`springboot-backend/.env`)

```bash
cp springboot-backend/.env.example springboot-backend/.env
# fill in: MYSQL_PASSWORD, JWT_SECRET, MAIL_*, TWILIO_*, STRIPE_*, PAYPAL_*
```

Required to start: **`JWT_SECRET`** (≥32 bytes, `openssl rand -hex 32`) and **`MYSQL_PASSWORD`**. The app refuses to boot without them (fail-closed at startup).

| Variable | Purpose | Default |
|---|---|---|
| `MYSQL_HOST/PORT/DB/USER/PASSWORD` | MySQL connection | `localhost:3306/savorystay_db/root/—` |
| `JWT_SECRET` | Token signing key (required) | — |
| `REDIS_HOST/PORT/PASSWORD` | Rate-limit / lockout store | `localhost:6379` |
| `MAIL_HOST/PORT/USERNAME/PASSWORD` | Gmail SMTP (emails/OTP) | `smtp.gmail.com:587` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker (host listener) | `localhost:29092` |
| `TWILIO_*` | SMS + WhatsApp OTP / alerts | — |
| `STRIPE_*` / `PAYPAL_*` | Payment gateways | mock defaults |
| `APP_URL` | Public URL in email footers | `http://localhost:5173` |
| `BUSINESS_TIMEZONE` | All pre-order cutoff/availability rules | `Asia/Kolkata` |

### 2. Start Redis & Kafka (notifications + rate limiting)

```bash
docker compose up -d
# Redis  → localhost:6379
# Kafka  → localhost:29092
# Kafka UI → http://localhost:8081
```

Verify Redis is running:
```bash
docker compose exec redis redis-cli ping
# → PONG
```

| Service | Port | Purpose |
|---------|------|---------|
| Redis | `6379` | Login rate limiting, OTP throttling, lockout state |
| Kafka | `29092` | Event backbone for email/SMS/SSE notification pipeline |
| Kafka UI | `8081` | Web dashboard to inspect topics, consumers, lag |

**Redis** is used for login rate limiting (5 failures → 15-min lockout) and OTP throttling. Without it, the app "fails open" (allows all requests) — which works but has no abuse protection. **Kafka** is required for the email/SMS notification pipeline — OTPs and order updates are dispatched through Kafka consumers.

### 3. Email Setup (Gmail SMTP)

The backend uses Gmail SMTP to send OTP emails and order notifications. You need a **Gmail App Password** (not your regular Gmail password).

#### Step 1: Enable 2-Step Verification

1. Go to [myaccount.google.com/security](https://myaccount.google.com/security)
2. Under "How you sign in to Google", click **2-Step Verification**
3. Follow the prompts to enable it (you can use your phone number)

#### Step 2: Generate an App Password

1. Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
   - If you don't see this page, make sure 2-Step Verification is enabled first
2. Under "App name", type `SavoryStay` and click **Create**
3. Google will show a **16-character password** like: `abcd efgh ijkl mnop`
4. **Copy this password** (you won't see it again)

#### Step 3: Configure Environment Variables

Add to your `springboot-backend/.env`:

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx
```

#### Step 4: Configure IntelliJ (if running from IDE)

IntelliJ does **NOT** automatically read `.env` files. You must add env vars to your Run Configuration:

1. **Run → Edit Configurations...**
2. Select `SavoryStayApplication`
3. Find **Environment variables** → click the folder icon
4. Add these variables:

| Name | Value |
|------|-------|
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `your-email@gmail.com` |
| `MAIL_PASSWORD` | `your-16-char-app-password` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |

5. Click **Apply → OK**, then restart the backend

#### How It Works

- When env vars are set → real emails are sent via Gmail SMTP
- When env vars are missing → the app falls back to **demo mode** (OTP is logged to the console and returned in the API response, so local flows still work)
- The `ChannelDeliveryService.isMailConfigured()` method checks for valid credentials before attempting delivery

### 4. Build & run backend

**From terminal** (env vars loaded automatically):

```bash
cd springboot-backend
set -a; source .env; set +a          # load env vars
./mvnw spring-boot:run               # or: mvn clean package && java -jar target/*.jar
# http://localhost:8080  ·  health: /api/v1/health
```

**From IntelliJ:**

1. Configure environment variables in Run Configuration (see §3 above)
2. Run `SavoryStayApplication` directly
3. IntelliJ does **NOT** read `.env` files — without env vars, email falls back to demo mode

On first boot, `DataSeeder` creates a super admin, two demo restaurants, staff, menus (with recipes), stock, price rules and pre-order defaults. Seed data is skipped once any user exists, but pre-order defaults are seeded idempotently for the demo restaurants.

### 4. Frontend

```bash
npm install
npm run dev       # http://localhost:5173
```

`VITE_API_URL` (default `http://localhost:8080`) points at the backend.

### 5. Demo accounts (seeded)

| Role | Username | Password | Restaurant |
|---|---|---|---|
| Super Admin | `superadmin` | `SuperAdmin@123` | — |
| Admin | `savoryadmin` / `spiceadmin` | `Admin@123` | REST_DEMO_1 / REST_DEMO_2 |
| Manager | `savorymanager` / `spicemanager` | `Manager@123` | REST_DEMO_1 / REST_DEMO_2 |
| Chef | `savorychef` / `spicechef` | `Chef@123` | REST_DEMO_1 / REST_DEMO_2 |
| Customer | `customer` | `Customer@123` | — |

> **Note:** After login, the `RestaurantSelector` modal opens for customers who belong to multiple restaurants. Pick a restaurant to scope your session.

---

## 5. Roles & Permissions

Roles are stored/carried as a **comma-separated string** (e.g. `ROLE_MANAGER,ROLE_CHEF`) — a user can hold several. `RoleUtils` (backend) and `roles.ts` (frontend) parse them safely.

| Capability | Customer | Chef | Manager | Admin | Super Admin |
|---|---|---|---|---|---|
| Browse menu / pre-book & pay | ✅ | 👁 | 👁 | 👁 | 👁 |
| Place orders | ✅ | ❌ | ❌ | ❌ | ❌ |
| Cook (`NEW→PREPARING`) / Pack (`PREPARING→PACKED_READY`) | — | ✅ | ✅ | ✅ | ✅ |
| Complete / decline / hand over | — | ❌ | ✅ | ✅ | ✅ |
| Edit menu / prices / recipe | — | ❌ | ✅ | ✅ | ✅ |
| Manage ingredients / stock | — | ❌ | ✅ | ✅ | ✅ |
| Manage staff | — | ❌ | ❌ | ✅ | ✅ |
| Manage all restaurants | — | ❌ | ❌ | ❌ | ✅ |
| Configure pre-orders (hours/cutoff/availability) | — | ❌ | ✅ | ✅ | ✅ |
| Join multiple restaurants | ✅ | ❌ | ❌ | ❌ | ❌ |
| Switch active restaurant after login | ✅ | ❌ | — | — | ✅ |

Method-level `@PreAuthorize` enforces these on every endpoint; the UI hides tabs the role cannot use.

### Customer–Restaurant Membership Model

Customers have a **single identity** (one login, one email, one password) but can belong to **multiple restaurants**. This is tracked via the `customer_restaurant` join table.

- **Joining**: a customer can join a restaurant via `POST /api/v1/customer-restaurants/join`.
- **Membership check**: `GET /api/v1/customer-restaurants/is-member/{restaurantId}`.
- **Listing**: `GET /api/v1/customer-restaurants/my-restaurants` returns all restaurants the customer belongs to, with restaurant details.
- **Leaving**: `DELETE /api/v1/customer-restaurants/leave/{restaurantId}`.
- **Post-login selection**: after authentication, if the customer is a member of multiple restaurants, the frontend shows a `RestaurantSelector` modal. The customer picks a restaurant, and `POST /api/v1/auth/select-restaurant` issues a new JWT scoped to that restaurant.
- **JWT scoping**: the JWT already carries a `restaurantId` claim. For customers, this is set dynamically when they select a restaurant. All downstream `TenantContext` logic, controller scope checks, and order placement work unchanged.

---

## 6. Ingredient Master (restaurant-scoped)

The ingredient master is the canonical identity for all ingredients in a restaurant. **Ingredient names are NEVER the identity** — the ingredient's `id` is. This eliminates duplicate ingredients caused by free-text entry and typos.

### Architecture

```
Restaurant
    ↓
Ingredient Master (id, normalizedName, displayName, unit, category, active)
    ├── Inventory (stock, ledger)
    ├── Recipe (MenuItemIngredient → ingredientId)
    ├── Forecast (aggregates by ingredientId, not name)
    └── Reporting
```

### Key design decisions

- **`normalized_name`** — lowercase, trimmed, whitespace-collapsed form enforced by `UNIQUE(restaurant_id, normalized_name)` at the database level.
- **`IngredientNormalization`** — single reusable normalization utility (trim → lowercase → collapse whitespace). No duplicate normalization logic across controllers/services.
- **No aggressive fuzzy matching** — exact normalized duplicates are rejected. Similar names ("Chicken" vs "Chicken Breast") are allowed as separate ingredients. A "Did you mean?" suggestion is offered but not auto-merged.
- **Active/inactive lifecycle** — ingredients are soft-deleted (`active=false`), never hard-deleted if referenced by recipes or inventory history. Inactive ingredients remain visible in historical data but cannot be used in new recipes.
- **Recipe references by ID** — `MenuItemIngredient.ingredientId` references the ingredient master. The `name` field is denormalized for backward compatibility but is NOT the source of truth.
- **Recipe duplicate prevention** — `UNIQUE(menu_item_id, ingredient_id)` prevents the same ingredient from appearing twice in one recipe.
- **Inline creation** — managers can create ingredients directly from the recipe editor without leaving the dish form. The new ingredient is immediately selectable.
- **Unit system** — controlled units (g, kg, ml, litre, piece). `UnitConverter` handles within-group conversions (g↔kg, ml↔litre). Cross-group conversions are rejected.
- **Tenant isolation** — every recipe operation validates `ingredient.restaurantId == currentRestaurant`. Cross-restaurant ingredient attachment is rejected server-side.

### Key utility classes

| Class | Purpose |
|-------|---------|
| `IngredientNormalization` | `normalize(name)`: trim → lowercase → collapse whitespace. `isDuplicate()`: case-insensitive comparison |
| `UnitConverter` | `convert(qty, from, to)`: weight (g↔kg), volume (ml↔litre), count (piece↔count). `areCompatible()`, `toBaseUnit()` |

### API changes

The ingredient API now supports:
- Search by name fragment (`?q=`)
- Filter active/inactive (`?includeInactive=true`)
- Soft-delete/deactivate/reactivate
- Recipe usage count
- Similar ingredient suggestions ("Did you mean?")

The recipe API now requires `ingredientId` in the request body. The `name` field is denormalized from the ingredient master.

---

## 7. Pre-Order Feature (business rules)

### Data model (all per restaurant)

| Table | Purpose |
|---|---|
| `restaurant_operating_hours` | Weekly hours per day (`day_of_week` 1=Mon…7=Sun, `open_time`, `close_time`, `closed`) |
| `preorder_settings` | `cutoff_time` (default 09:00) + `advance_days` (default 7) |
| `dish_availability` | Weekdays a dish is cooked; **no rows = available every day** (backward compatible) |
| `dish_slot_override` | Manager OPEN/CLOSE for a specific date + dish |

### Rules (all evaluated in the business timezone via `BusinessClock`)

1. **Horizon** — pre-orders only for dates between **tomorrow** and `today + advance_days`.
2. **Cutoff** — orders for date **D** close at `cutoff_time` on **D-1** (*exactly at cutoff = closed*; example: 09:00 cutoff ⇒ Tuesday's pre-orders close Monday 09:00). The cutoff is **per restaurant** (`preorder_settings.cutoff_time`) and may not be **after the restaurant's opening time on any open day** — enforced in `PreOrderConfigService.updateSettings` (cutoff save) and `upsertOperatingHour` (hours save) so the config can never become invalid.
3. **Closure** — a full holiday (`closed=true`), an unset/invalid window, or a day closing **at/before 14:00** ("2nd half closed") blocks **all** pre-orders for that day. On open days, pickup time must fall inside `open_time..close_time`. Same-day PICKUP/DINE_IN are unaffected.
4. **Dish availability** — precedence: **explicit CLOSE > explicit OPEN > weekly schedule**; **restaurant closure always wins**. No schedule ⇒ available daily.
5. **Estimates** — current recipe is always used (recomputed). `GET /api/v1/ingredients/forecast?date=YYYY-MM-DD` returns both an **aggregated** per-ingredient total (across all dishes, e.g. Rice 5 kg + 1.5 kg = 6.5 kg) and a **per-dish breakdown** (plates + ingredient totals), each with current stock and shortfall.
6. **Reminder** — `PreOrderReminderScheduler` runs daily **08:45** (business timezone) and notifies each restaurant's managers/admins if operating hours are missing/incomplete for any of the next 3 days **or** no dish availability is configured. Deliberate closures do **not** trigger it.

### Enforcement point
`OrderService.placeOrder` calls `PreOrderAvailabilityService.validatePreOrder(...)` for `PRE_ORDER` orders before pricing — customers get a clear `400` message (e.g. "Pre-order cutoff … has passed", "Restaurant is closed on …", "Not available for pre-order on …"). `POST /api/v1/pre-orders/dates` powers the checkout date picker (each date carries `orderable`, `reasons`, and per-dish availability).

### Seeded demo defaults
- Mon & Sat close at **14:00** (2nd half closed), Sun = weekly holiday, other days 09:00–23:00.
- Butter Chicken cooked Mon/Wed/Fri/Sun; Masala Chai every day; remaining dishes unconfigured (= daily).

---

## 7. Event Pipeline (Outbox → Kafka → Notifications)

```
Service writes DB row + outbox_event (same transaction)
   → OutboxPoller (every 3s, per-event transaction)
   → Kafka topics: savorystay.orders │ savorystay.otp │ savorystay.inventory │ savorystay.payments
   → Consumers (OrderNotification, OtpNotification, InventoryNotification, PaymentNotification)
        → SSE (in-app) + Twilio SMS/WhatsApp + ElasticEmail Gmail (HTML templates)
   → Failures: non-blocking retries (1s→2s→4s) → DLT topic → failed_delivery audit table
```

- Topics auto-create on startup (`KafkaTopicConfig`). Retry/DLT topics per consumer (`@RetryableTopic`, `@DltHandler`).
- Demo mode (no SMTP/Twilio credentials): delivery methods log and return `false`, API still returns `demoOtp` for local testing; no retries/dead-lettering.
- ⚠️ OTP delivery needs Kafka — an OTP queued while the broker is down expires (5 min) before it can be delivered.

---

## 8. REST API Reference

Base URL: `http://localhost:8080/api/v1`. Errors are uniform: `{ "success": false, "message": "..." }`.

### Auth & OTP
| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/auth/register` | public | username, email, password, phone?, otpCode? |
| POST | `/auth/login` | public | username + password |
| POST | `/auth/login-with-otp` | public | username + otpCode + channel |
| POST | `/auth/otp/send/email` \| `/sms` \| `/whatsapp` | public | returns `demoOtp` in demo mode |
| POST | `/auth/otp/verify` | public | userId + otpCode + channel |
| POST | `/auth/otp/resend` | public | |
| GET | `/auth/me` | authenticated | current profile |
| GET | `/auth/check-availability` | public | early username/email/phone taken checks |
| POST | `/auth/select-restaurant` | authenticated | issues restaurant-scoped JWT for customers |

### Restaurants (multi-tenant)
| Method | Path | Access |
|---|---|---|
| GET | `/restaurants`, `/restaurants/{id}` | public |
| GET | `/restaurants/{id}/menu` | public |
| POST/PUT/DELETE/GET | `/super-admin/restaurants…` | SUPER_ADMIN |
| POST/PATCH/GET | `/staff…` | ADMIN/SUPER_ADMIN |

### Customer–Restaurant Membership
| Method | Path | Access | Notes |
|---|---|---|---|
| POST | `/customer-restaurants/join` | ROLE_CUSTOMER | body: `{ restaurantId, displayName? }` — idempotent |
| DELETE | `/customer-restaurants/leave/{restaurantId}` | ROLE_CUSTOMER | removes membership |
| GET | `/customer-restaurants/my-restaurants` | ROLE_CUSTOMER | list restaurants with details |
| GET | `/customer-restaurants/is-member/{restaurantId}` | ROLE_CUSTOMER | check membership |
| GET | `/customer-restaurants/members?restaurantId=` | ADMIN/MANAGER/SUPER_ADMIN | list all members with user details (username, email, phone, enabled) |
| DELETE | `/customer-restaurants/members/{customerId}?restaurantId=` | ADMIN/SUPER_ADMIN | remove a customer from the restaurant (order history preserved) |

### Menu & recipes
| Method | Path | Access |
|---|---|---|
| GET | `/menu` | any staff |
| GET/POST/PUT/DELETE | `/menu`, `/menu/{id}` | manager+ (writes) |
| GET | `/menu/{id}/ingredients` | any staff |
| POST | `/menu/{id}/price` | manager+ (schedule price change) |

### Orders & payments
| Method | Path | Access |
|---|---|---|
| POST | `/orders` | ROLE_CUSTOMER only (server sets PENDING payment, CASH stays PENDING) |
| POST | `/orders/{id}/payment` | owner or restaurant staff — **rejects CASH gateway** (CASH orders paid at counter) |
| POST | `/orders/{id}/cancel` | order owner (NEW only) or staff (NEW/PREPARING/PACKED_READY) — `reason` optional |
| POST | `/orders/{id}/refund` | MANAGER+ — initiates refund (creates `Refund` record, sets `REFUND_PENDING` on order) |
| POST | `/orders/{id}/items/{itemId}/notes` | staff — add/update kitchen notes ("Less spicy", "No onion") |
| POST | `/orders/status` | staff (per-role transition rules, validated by `OrderStateMachine`) |
| GET | `/orders/{id}/audit` | MANAGER+ — audit trail for an order |
| GET | `/orders/mine` | authenticated |
| GET | `/orders` | restaurant staff |
| POST | `/orders/kitchen/production` | staff — production view: dishes + required plates + urgency |
| GET | `/orders/kitchen/delayed` | staff — orders past promised pickup time |
| POST | `/menu/availability-check` | authenticated — body: `{ restaurantId, items: [{menuItemId, quantity}] }` → `{ allAvailable, unavailableItems }` |
| POST | `/menu/{id}/sold-out` | staff — `{ soldOut: true/false }` — marks dish as Sold Out/Available (86) |
| POST | `/payments/create-intent` · `/payments/webhook` | authenticated / webhook |

### Ingredients & forecast
| Method | Path | Access |
|---|---|---|
| GET | `/ingredients?q=&includeInactive=` | staff read (active only by default; admin can see inactive) |
| GET | `/ingredients/{id}` | staff read |
| POST | `/ingredients` | manager+ — creates ingredient (normalizes name, enforces uniqueness) |
| PUT | `/ingredients/{id}` | manager+ — updates ingredient |
| DELETE | `/ingredients/{id}` | manager+ — hard delete only if no recipe/inventory references |
| PATCH | `/ingredients/{id}/deactivate` | manager+ — soft-delete (sets active=false) |
| PATCH | `/ingredients/{id}/reactivate` | manager+ — reactivates ingredient |
| GET | `/ingredients/{id}/usage` | manager+ → `{ usageCount }` (how many recipes reference it) |
| GET | `/ingredients/similar?name=` | manager+ → `{ similarIngredients }` ("Did you mean?" suggestions) |
| GET | `/ingredients/forecast?date=YYYY-MM-DD` | any staff → `{ ingredients: [], dishes: [] }` |
| GET | `/pre-orders/config/hours` | manager+ → operating hours (used by 7-day forecast picker to show holidays) |

### Pre-orders (config + customer dates)
| Method | Path | Access |
|---|---|---|
| POST | `/pre-orders/dates` | authenticated (checkout) — body `{ restaurantId, menuItemIds, daysAhead? }` |
| GET/PUT | `/pre-orders/config/hours` | manager+ |
| GET/PUT | `/pre-orders/config/settings` | manager+ |
| GET/PUT | `/pre-orders/menu-items/{id}/availability` | manager+ |
| PUT/DELETE | `/pre-orders/menu-items/{id}/slots?date=…` | manager+ |

### Dashboard & Operations (manager/chef)
| Method | Path | Access |
|---|---|---|
| GET | `/dashboard/tomorrow-brief` | staff — pre-order count, expected revenue, production, ingredient requirements |
| GET | `/dashboard/shopping-list` | staff — ingredients with shortfalls only |
| GET | `/dashboard/cash-reconciliation` | MANAGER+ — expected vs collected cash for a day |
| GET | `/dashboard/payment-reconciliation` | MANAGER+ — gross/refunds/net, by-method breakdown |
| GET | `/dashboard/exceptions` | staff — aggregated operational exceptions (failures, delays, shortages, pending) |
| GET | `/dashboard/summary` | MANAGER+ — today's orders/revenue/status + tomorrow's brief |
| GET | `/dashboard/audit` | MANAGER+ — recent audit trail (configurable limit) |

### Notifications
| Method | Path | Access |
|---|---|---|
| GET | `/notifications` | authenticated (own) |
| POST | `/notifications/read-all` | authenticated |
| GET | `/realtime/stream?token=…` | authenticated (SSE) |
| GET | `/health` | public |

---

## 9. Database Schema (highlights)

All tables are created idempotently via `schema.sql` + `ddl-auto: update`.

- **users** — `id, username, email, password_hash, role (comma CSV), phone, restaurant_id, enabled, created_at, last_login`
- **restaurants** — `id, name, description, address, city, cuisine, phone, email, logo_url, status, currency, owner_id, auto_join_customers` (`auto_join_customers BOOLEAN DEFAULT TRUE` — when true, customers are auto-joined on first order)
- **customer_restaurant** — `id, customer_id, restaurant_id, display_name, joined_at` (unique on `customer_id + restaurant_id`; FK to `users` and `restaurants` with `ON DELETE CASCADE`) — tracks which restaurants a customer belongs to
- **ingredients** — `id, restaurant_id, name, display_name, normalized_name, unit, category, stock_quantity, reorder_level, active, version` — **unique on `(restaurant_id, normalized_name)`**. The ingredient's ID is the canonical identity; names are human-readable labels only.
- **menu_items** — dishes with standard fields
- **menu_item_ingredients** — recipe lines: `menu_item_id, ingredient_id (FK→ingredients), name (denormalized), quantity_per_unit, unit` — **unique on `(menu_item_id, ingredient_id)`** prevents duplicate ingredients in one recipe
- **orders / order_items / payments / order_status_history** — orders, line items, audit trail. Orders carry `cancelled_by`, `cancel_reason`, `cancelled_at` for cancellation tracking.
- **refunds** — refund lifecycle: `REQUESTED → PROCESSING → COMPLETED/FAILED`. Links to order + payment. Tracks `provider_refund_id`, `initiated_by`, `reason`.
- **audit_trail** — general-purpose append-only audit for all business mutations (menu, price, recipe, ingredient, inventory, pre-order config, staff, orders, refunds, payments). Columns: `restaurant_id, actor_user_id, actor_role, action, entity_type, entity_id, old_value, new_value, reason, recorded_at`.
- **ingredients / inventory_ledger** — stock (optimistic-lock `version`) + append-only movements. Ledger reasons: `ORDER_CONSUMED, CANCELLATION_RELEASE, MANUAL_RESTOCK, WASTAGE, SPOILAGE, DAMAGE, STOCK_COUNT_CORRECTION, MANUAL_ADJUSTMENT`.
- **notifications** — persisted + delivery status (`channel`, `delivery_phone/email`, `status`, attempts)
- **price_rule** — scheduled price changes (`effective_from`)
- **otp_requests** — OTP lifecycle (status, expiry, attempts)
- **outbox_event / failed_delivery** — event backbone + DLT audit
- **restaurant_operating_hours / preorder_settings / dish_availability / dish_slot_override** — pre-order config (§6)

---

## 10. Frontend Guide

- **State lives in `App.tsx`** — `activeTab`, auth (`currentUser`/`jwtToken`/`userRole`/`userRestaurantId`), `cart`, `menuItems`, `orders`, restaurant selection, `isRestaurantSelectorOpen` (post-login restaurant picker).
- **API calls** go through `src/lib/apiClient.ts` (plain `fetch`; `authenticatedFetch` attaches the Bearer token from `localStorage` key `savory_token`). Each function throws `Error(message)` on failure — components surface `err.message` in red banners.
- **Types** are centralized in `src/types.ts`; backend entities are parsed (e.g. `parseMenuItem` converts price strings → numbers).
- **Realtime** — `useRealtimeNotifications` opens an `EventSource` to `/realtime/stream?token=…`; incoming events update the notification bell and refresh orders.
- **Tabs** (`ViewTab`) are role-scoped in `Header`/`BottomNav`: Menu (all), Menu Mgmt + Pre-Orders (manager+), Orders (customer tracking / staff dashboard), Kitchen (staff), Dashboard (manager+), Staff (admin), Admin (super admin), Ingredients/Prep (staff), Spring (dev inspector).
- **Super-admin scoping** — a super admin's JWT carries no restaurant, so they pick one via the header `RestaurantPicker` or the "Manage" button on `SuperAdminDashboard`; every staff-scoped API call then passes `?restaurantId=` and the backend honors it (`TenantContext.resolveRestaurantScope`). Staff accounts stay locked to their own restaurant.
- **Customer restaurant selection** — after login, if the customer is a member of multiple restaurants, `RestaurantSelector` opens. It calls `getMyRestaurants()` to list memberships, lets the customer pick one (or join a new one via `joinRestaurant()`), then calls `selectRestaurant()` which hits `POST /auth/select-restaurant` to issue a restaurant-scoped JWT. On page reload, the JWT's `restaurantId` claim restores the selected context. The `RestaurantPicker` header dropdown also appears for customers so they can switch restaurants at any time.
- **Customer membership management** (`CustomerMembershipManager.tsx`) — admin/super-admin view accessible via the "Members" tab in the header/bottom nav. Shows all customer members of the current restaurant with username, email, phone, join date, and enabled status. Supports search filtering and member removal (with confirmation). The "Members" tab displays a **sky-blue badge** with the live member count, fetched via `listCustomerMembers()`.
- **Auto-join on first order** — when a restaurant has `autoJoinCustomers = true` (the default), `OrderService.placeOrder` automatically adds the customer to the `customer_restaurant` table if they aren't already a member. This is non-critical: if it fails, the order still goes through. The flag is on the `restaurants` entity and can be toggled via API or DB.
- **Menu calendar** (`CustomerMenuView`): a "Pre-Order Availability" strip sits on the menu itself — one chip per upcoming day (tomorrow → advance horizon) with status (Available / Partial / Closed / Cutoff), a per-day dish breakdown, and a dish filter; selecting a date syncs it into checkout as the default pre-order date.
- **Checkout flow** (`RealtimePaymentModal`): for PRE_ORDER it fetches `/pre-orders/dates` for the cart's dishes, lets the customer pick an available date + pickup time (bounded by operating hours), and submits an ISO `pickupTime` (`YYYY-MM-DDTHH:MM:SS`). Backend re-validates and rejects invalid pre-orders.
- **Cart availability check** (`RealtimePaymentModal`): when the checkout modal opens, `checkCartAvailability()` is called immediately to detect items that went out of stock while the customer was browsing. Unavailable items are shown in a rose warning banner with strikethrough in the cart summary, and the submit button is disabled until the customer removes them from the cart. This prevents wasted payment attempts on sold-out items.
- **CASH / Pay-on-Pickup** (`RealtimePaymentModal`): when the customer selects "Pay on Pickup" (CASH), the frontend skips `confirmOrderPayment()` so the order stays `PENDING`. The backend also rejects CASH in `confirmPayment()` — CASH orders must be marked PAID by restaurant staff when the customer pays at the counter. This ensures the payment status accurately reflects when cash is collected.
- **Auth modal cleanup** (`AuthModal.tsx`): a `useEffect` resets all form fields (email, password, username, phone, OTP codes, error messages) when the modal closes, so stale data from a previous session never leaks into a fresh login attempt.
- **7-day ingredient forecast picker** (`IngredientPlanning.tsx`): the forecast screen now shows a 7-day horizontal date chip grid (tomorrow → +7 days) instead of a single date input. Each chip displays the day name, date, and operating hours (e.g. `09:00–23:00`). Closed days (weekly holidays, 2nd-half closures) are dimmed with a `CLOSED` badge and are not clickable. The operating hours are fetched from `GET /pre-orders/config/hours` on load. The chef clicks a date chip, then "Compute Forecast" to see that day's ingredient requirements.
- **Manager pre-order config** lives in `PreOrderSettings.tsx` (hours grid, cutoff/horizon, per-dish weekdays, OPEN/CLOSE slot overrides). The recipe editor (ingredients per plate) lives in the MenuManagement dish form.
- **Manager Dashboard** (`ManagerDashboard.tsx`): 5-tab operational dashboard — **Today** (orders/revenue/status grid), **Exceptions** (failures/delays/shortages), **Shopping** (shortfall table with date picker), **Cash** (reconciliation), **Payments** (gross/refunds/net by method). Accessible via the Dashboard tab in Header/BottomNav for manager+ roles.
- **Kitchen Production Queue** (`PreBookingsDashboard.tsx`): right sidebar shows dish-level production with urgency badges (OVERDUE/DUE_SOON/NORMAL) and a delayed orders alert with minutes late. Data fetched from `GET /orders/kitchen/production` and `/kitchen/delayed`.
- **Sold-Out / 86 toggle** (`MenuManagement.tsx`): each dish card has an 86/Restock button that calls `POST /menu/{id}/sold-out`. Toggles between Available and Sold Out instantly. Customers see the dish as unavailable; existing confirmed orders are not affected.
- **Order cancellation** (`PreBookingsDashboard.tsx`): staff can cancel orders via the status update flow. The backend validates state machine transitions and records cancellation metadata.

---

## 11. Testing

```bash
# Backend (all unit + integration tests; Docker-dependent tests skip when Docker is unavailable)
cd springboot-backend && ./mvnw test

# Run only integration tests (requires Docker)
./mvnw test -Dtest="RefundAuditIntegrationTest"

# Exclude the Docker-dependent test
./mvnw test -Dtest='!AuthRateLimitServiceIntegrationTest,!RefundAuditIntegrationTest'

# Frontend typecheck + production build
npm run lint && npm run build
```

Test coverage highlights:
- `PreOrderAvailabilityServiceTest` (20 tests) — cutoff before/at/after 09:00, custom cutoff, closure (holiday, 2nd-half-close), pickup-window checks, weekly schedule, OPEN/CLOSE precedence, closure-overrides, horizon, `availableDates` for checkout, reminder triggers.
- `IngredientForecastServiceTest` (5 tests) — 1 dish × N plates, aggregation across dishes sharing an ingredient, multiple pre-orders summing, missing recipe, shortfall vs stock.
- `IngredientMasterTest` (25 tests) — normalization (trim, lowercase, whitespace), uniqueness enforcement (exact, case-insensitive, whitespace variant), cross-restaurant same name allowed, similar names coexist, soft-delete (deactivate/reactivate/toggle), delete prevention when in use, search (active/all), unit conversion (within-group, cross-group rejection, compatibility check), recipe validation, usage count.
- `AuthRateLimitServiceTest` / `AuthRateLimitServiceIntegrationTest` — lockout, throttling, fail-open (needs Docker).
- `CustomerRestaurantServiceTest` (16 tests) — join new restaurant, idempotent join, suspended restaurant rejection, nonexistent restaurant, leave existing/nonexistent membership, isMember, myRestaurants with enriched data and missing restaurant, memberCount, listMembers, listMembersWithDetails (enriched + missing user), removeMember (exists + not found).
- `OrderServiceSuspensionTest` (9 tests) — includes 3 auto-join tests (auto-join on first order, skip if already member, skip if disabled) and 3 CASH payment tests (reject CASH in confirmPayment, reject CASH gateway for non-CASH order, succeed for online gateway).
- `OrderStateMachineTest` (28 tests) — valid transitions (NEW→PREPARING, PREPARING→PACKED_READY, PACKED_READY→COMPLETED), invalid transitions (COMPLETED→PREPARING, DECLINED→PREPARING, NEW→COMPLETED), terminal state protection, chef-only transitions, role authorization.
- `RefundServiceTest` (11 tests) — refund lifecycle (initiate, idempotent, complete), reject non-PAID/customer/cross-tenant, handle missing payment.
- `AuditServiceTest` (7 tests) — record with all fields, convenience overloads (no old value, Map payload), null actor, error resilience, query by entity/action, recent with limit.
- `OrderServiceSuspensionTest` (15 tests) — expanded: 3 auto-join + 3 CASH payment + 6 cancellation (manager cancel, customer cancel, invalid transition, chef cannot decline, terminal state) + 3 payment idempotency (already PAID, payment row exists, amount mismatch).
- **`RefundAuditIntegrationTest`** (18 tests, MySQL Testcontainers) — full lifecycle against real DB: refund initiate→complete, idempotency, reject non-PAID/customer/cross-tenant; audit record+query by entity/action, recent limit, tenant isolation; order cancellation with audit+history; inventory deduction+release with ledger; payment idempotency+CASH rejection+amount mismatch.

### P0 / P1 Implementation Summary

| Category | What was implemented |
|---|---|
| **P0.1 Order State Machine** | `OrderStateMachine.java` — explicit transition rules, role-based validation (chef can only cook+pack, managers handle decline/cancel/complete). `updateStatus()` now delegates to `OrderStateMachine.validate()`. |
| **P0.2 Payment Separation** | Order `orderStatus` and `paymentStatus` are independent. CASH orders stay `PENDING` until counter payment. |
| **P0.3 Payment Idempotency** | `confirmPayment()` is idempotent — checks `PAID` status + existing `Payment` rows before creating new ones. |
| **P0.5 Cancellation** | `POST /orders/{id}/cancel` — customers cancel NEW orders only; staff cancel NEW/PREPARING/PACKED_READY. Records `cancelled_by`, `cancel_reason`, `cancelled_at`. |
| **P0.6 Refund Model** | `Refund` entity with lifecycle (REQUESTED→PROCESSING→COMPLETED/FAILED). `POST /orders/{id}/refund` initiates. `completeRefund()` marks done. Supports future PARTIAL_REFUND. |
| **P0.7 Inventory Release** | `releaseReservation()` adds back stock on cancellation/decline with audit ledger entry (`CANCELLATION_RELEASE`). |
| **P0.10 Audit Trail** | `AuditTrail` entity + `AuditService` — append-only recording of order cancellation, refunds, ingredient changes. Reusable across all modules. |
| **P1.1 Kitchen Production** | `GET /orders/kitchen/production` — aggregates active orders by dish, shows required plates + urgency (OVERDUE/DUE_SOON/NORMAL). |
| **P1.2 Kitchen Priority** | Urgency calculated from earliest pickup time: OVERDUE (past pickup), DUE_SOON (≤30 min), NORMAL. |
| **P1.3 Delay Detection** | `GET /orders/kitchen/delayed` — orders past promised pickup time with delay minutes. |
| **P1.4 Sold-Out 86** | `POST /menu/{id}/sold-out` — chef/manager toggles dish availability without cancelling confirmed orders. |
| **P1.5 Kitchen Notes** | `POST /orders/{id}/items/{itemId}/notes` — add/update notes visible in kitchen view. |
| **P1.7 Tomorrow Brief** | `GET /dashboard/tomorrow-brief` — pre-order count, revenue, production per dish, ingredient requirements. |
| **P1.8 Shopping List** | `GET /dashboard/shopping-list` — filtered to ingredients with shortfalls only. |
| **P1.9 Cash Reconciliation** | `GET /dashboard/cash-reconciliation` — expected vs collected cash for a day. |
| **P1.10 Payment Reconciliation** | `GET /dashboard/payment-reconciliation` — gross/refunds/net, breakdown by payment method. |
| **P1.11 Exception Center** | `GET /dashboard/exceptions` — aggregated failures, delays, shortages, pending payments. |
| **P1.13 Manager Dashboard** | `GET /dashboard/summary` — today's orders/revenue/status breakdown + tomorrow's brief. |

---

---

## 12. Deployment

- **Backend**: build `mvn clean package`, run the executable JAR with env vars set (`java -jar target/savory-stay-backend-1.0.0-SNAPSHOT.jar`). Point `MYSQL_*`/`KAFKA_*`/`MAIL_*`/`TWILIO_*`/`STRIPE_*`/`PAYPAL_*` at production services. Terminate TLS at a reverse proxy.
- **Frontend**: `npm run build` → serve `dist/` (Vercel/Netlify/nginx) with `VITE_API_URL` set to the public backend URL.
- **Infra**: MySQL (backups), Redis, Kafka (multi-node for production), SSL.
- **Production checklist**: strong `JWT_SECRET`, HTTPS, restricted CORS, DB backups, monitoring, `ddl-auto: validate` after schema is stable, rotate secrets, enable logging aggregation.

---

## 13. Security Notes

- Secrets live only in environment variables; **no committed credentials** (the app aborts startup without `JWT_SECRET`/`MYSQL_PASSWORD`).
- Rate limiting: login failures (5 → 15-min lockout), OTP send/verify throttling — Redis-backed, **fails open** if Redis is down.
- OTPs are purpose-tagged (`LOGIN` vs `REGISTRATION`), 6-digit, 5-min expiry, max 5 attempts, auto-cleaned every 15 min.
- Payment status is server-authoritative; amount is verified against the order total before marking PAID.
- Passwords: BCrypt strength 12; `role` claim is server-issued; `TenantContext` + service-level checks enforce restaurant isolation.

---

## 14. Common Troubleshooting

| Symptom | Fix |
|---|---|
| App aborts: "required secrets are missing" | Set `JWT_SECRET` (≥32 bytes) + `MYSQL_PASSWORD` |
| MySQL connection refused | Start MySQL; check `MYSQL_*` vars |
| Port 8080/5173 busy | Change `server.port` / Vite port |
| OTP email not received | Verify ElasticEmail SMTP creds + verified sender; check spam |
| Notifications not delivered | Start Kafka (`docker compose up -d`); check Kafka UI `:8081` and `failed_delivery` table |
| Pre-order rejected unexpectedly | Check hours (a day closing ≤14:00 blocks), cutoff time, dish availability, horizon |
| Redis `Unable to connect` warnings | Start Redis: `docker compose up -d redis` — verify with `redis-cli ping` (should return `PONG`) |
| Email `535 Authentication failed` | Gmail SMTP password is wrong/empty. Generate an App Password at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords). If running from IntelliJ, add env vars to Run Configuration (see §3). Without env vars, email falls back to demo mode (OTP logged to console). |
| Email not sending (demo mode) | The app is using demo mode because `MAIL_PASSWORD` is not set. Add Gmail SMTP credentials to `.env` or IntelliJ Run Configuration. |
| Frontend 401s | Token expired (24 h) — sign in again |
| "You are not a member of this restaurant" | Customer must join the restaurant first via `POST /customer-restaurants/join` before selecting it |
| Restaurant selector not appearing | Check the customer has memberships — the selector opens automatically after login for `ROLE_CUSTOMER` |
| Customer not auto-joined on order | Check `restaurants.auto_join_customers` is `TRUE` for the restaurant; check logs for auto-join errors (non-critical, order still placed) |
| Members badge showing 0 | Ensure the user has ADMIN or SUPER_ADMIN role and a restaurant is selected; check `GET /customer-restaurants/members` returns data |
| Cart shows unavailable items at checkout | The availability check runs when the modal opens; if items were restocked, close and reopen the checkout |
| CASH order shows as PAID | Fixed — CASH orders stay PENDING until staff marks them paid at the counter |
| Auth form shows old data after logout | Fixed — form fields reset when the modal closes |
| Forecast only shows one date | The 7-day date picker replaces the old single date input; click any non-closed date chip |

---

**Status:** production-ready. Backend: **252 tests passing** (234 unit + 18 MySQL Testcontainers integration). Frontend: typecheck + production build clean. P0 correctness (state machine, cancellation, refund, audit, idempotency) and P1 operations (kitchen priority, sold-out 86, tomorrow brief, shopping list, reconciliation, exception center, manager dashboard) fully implemented. Frontend dashboard wired to all backend endpoints.
