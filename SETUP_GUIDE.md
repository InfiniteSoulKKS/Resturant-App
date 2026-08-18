# 🔧 SavoryStay — Redis & Email Setup Guide

This guide fixes the two common issues: **Redis not connecting** and **email not sending**.

---

## 1. Redis Setup (Rate Limiting & Lockout)

Redis is used for login rate limiting and OTP throttling. Without it, the app "fails open" (allows all requests) — which works but has no abuse protection.

### Option A: Docker (Recommended)

Redis has been added to your `docker-compose.yml`. Just run:

```bash
cd /path/to/Resturant-App
docker compose up -d redis
```

Verify it's running:
```bash
docker compose ps redis
# Should show: savorystay-redis  running

docker compose exec redis redis-cli ping
# Should return: PONG
```

Your `.env` already has the correct config:
```
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
```

No changes needed — restart the Spring Boot app and Redis warnings will disappear.

### Option B: Redis Cloud (Free Tier)

If you don't want to use Docker:

1. Go to [redis.io/cloud](https://redis.io/cloud) and create a free account
2. Create a database (30 MB free tier is enough for development)
3. Copy the **Redis URL** from the dashboard
4. Update your `.env`:

```bash
REDIS_HOST=<your-redis-cloud-host>
REDIS_PORT=<port-from-dashboard>
REDIS_PASSWORD=<password-from-dashboard>
```

### Option C: Install Redis Locally (macOS)

```bash
brew install redis
brew services start redis
```

Verify:
```bash
redis-cli ping
# Should return: PONG
```

---

## 2. Email (SMTP) Setup

Your current config uses ElasticEmail, but the SMTP password is being rejected:
```
535 535 Authentication failed: Access denied
```

This means either:
- The API key is wrong/expired
- The account email doesn't match
- The account hasn't verified the sender email

### Option A: Fix ElasticEmail (Your Current Provider)

1. Go to [app.elasticemail.com](https://app.elasticemail.com)
2. Log in with your account (`kumarkartiksahu3@gmail.com`)
3. Go to **Settings → SMTP**
4. **Regenerate** your SMTP API key (the old one may be expired)
5. Copy the new API key
6. Update your `.env`:

```bash
MAIL_HOST=smtp.elasticemail.com
MAIL_PORT=2525
MAIL_USERNAME=kumarkartiksahu3@gmail.com
MAIL_PASSWORD=<your-new-elasticemail-api-key>
```

**Important:** The `MAIL_USERNAME` must be the email you signed up with on ElasticEmail, and you must verify that email as a sender.

### Option B: Use Gmail SMTP (Simplest for Development)

If you have a Gmail account, you can use Gmail's SMTP with an **App Password**:

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Go to **Security → 2-Step Verification** (enable it if not already)
3. Go to **Security → App passwords**
4. Generate a new app password for "Mail"
5. Copy the 16-character password
6. Update your `.env`:

```bash
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-gmail@gmail.com
MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx
```

**Note:** Gmail has a daily sending limit (500 emails/day for free accounts). Fine for development.

### Option C: Use Mailtrap (Development Only — No Real Emails)

Mailtrap captures emails in a web dashboard instead of sending them real. Great for development:

1. Go to [mailtrap.io](https://mailtrap.io) and create a free account
2. Create an inbox in the dashboard
3. Copy the SMTP credentials from the inbox settings
4. Update your `.env`:

```bash
MAIL_HOST=smtp.mailtrap.io
MAIL_PORT=587
MAIL_USERNAME=<from-mailtrap-dashboard>
MAIL_PASSWORD=<from-mailtrap-dashboard>
```

All emails will appear in the Mailtrap web UI — no real emails are sent.

---

## 3. Restart the Backend

After updating `.env`:

```bash
cd springboot-backend
# Reload environment variables
set -a; source .env; set +a

# Restart
./mvnw spring-boot:run
```

### Verify Redis is Connected

Look for this in the logs (no more "Redis unavailable" warnings):
```
RedisConnectionFactory established
```

### Verify Email is Working

1. Open the app at `http://localhost:5173`
2. Click "Sign In" → "Register"
3. Enter your email and request an OTP
4. Check your inbox (or Mailtrap dashboard) for the OTP email

---

## 4. Troubleshooting

| Symptom | Fix |
|---|---|
| `[rate-limit] Redis unavailable` | Redis not running — `docker compose up -d redis` |
| `535 Authentication failed` | SMTP credentials wrong — regenerate API key or use Gmail/Mailtrap |
| `Connection refused` on port 6379 | Redis not started — check `docker compose ps` |
| OTP email not received | Check Kafka is running (`docker compose up -d`), check email config |
| Emails going to spam | Use a verified sender domain, or switch to Mailtrap for dev |

---

## Quick Start (All Services)

```bash
# Start all infrastructure
cd /path/to/Resturant-App
docker compose up -d

# This starts:
# - Kafka on localhost:29092
# - Kafka UI on http://localhost:8081
# - Redis on localhost:6379

# Start the backend
cd springboot-backend
set -a; source .env; set +a
./mvnw spring-boot:run

# Start the frontend (in another terminal)
cd ..
npm run dev
```
