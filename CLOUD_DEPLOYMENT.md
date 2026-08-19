# SavoryStay — Cloud Deployment Guide (Free Tier)

This guide helps you deploy the full SavoryStay stack to production using **100% free cloud resources**.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                     CLOUD DEPLOYMENT                      │
│                                                            │
│  ┌─────────────┐     ┌──────────────┐     ┌────────────┐  │
│  │  Frontend    │────▶│   Backend    │────▶│   MySQL    │  │
│  │  (Vercel)   │     │  (Render)    │     │ (PlanetScale)│ │
│  └─────────────┘     └──────┬───────┘     └────────────┘  │
│                              │                             │
│                    ┌─────────┼─────────┐                   │
│                    ▼         ▼         ▼                   │
│              ┌──────────┐ ┌───────┐ ┌──────────┐          │
│              │  Redis   │ │ Kafka │ │  Gmail   │          │
│              │(Upstash) │ │(KRaft)│ │  SMTP    │          │
│              └──────────┘ └───────┘ └──────────┘          │
└──────────────────────────────────────────────────────────┘
```

---

## Option A: Managed Services (Recommended — Easiest)

### 1. Frontend → Vercel (Free)

| Feature | Details |
|---------|---------|
| **Free tier** | 100 GB bandwidth/month, unlimited deploys |
| **Setup** | `npm i -g vercel` → `vercel` |
| **Custom domain** | Free `.vercel.app` subdomain |

```bash
# Install Vercel CLI
npm i -g vercel

# Login
vercel login

# Deploy from project root
vercel --prod

# Set environment variable for backend URL
vercel env add VITE_API_URL
# Enter: https://your-backend.onrender.com
```

### 2. Backend → Render (Free)

| Feature | Details |
|---------|---------|
| **Free tier** | 750 hours/month, spins down after 15 min inactivity |
| **Setup** | Connect GitHub repo → auto-deploy |
| **Note** | First deploy takes ~5 min (build + start) |

**Steps:**
1. Push your code to GitHub
2. Go to [render.com](https://render.com) → New → Web Service
3. Connect your GitHub repo
4. Settings:
   - **Runtime**: Java
   - **Build Command**: `cd springboot-backend && ./mvnw package -DskipTests`
   - **Start Command**: `java -jar springboot-backend/target/savory-stay-backend-1.0.0-SNAPSHOT.jar`
   - **Plan**: Free
5. Add **Environment Variables** (see below)

**Required Environment Variables:**
```
JAVA_VERSION=17
MYSQL_HOST=your-planetscale-host
MYSQL_PORT=3306
MYSQL_DB=savorystay_db
MYSQL_USER=your-planetscale-user
MYSQL_PASSWORD=your-planetscale-password
JWT_SECRET=<generate with: openssl rand -hex 32>
REDIS_HOST=your-upstash-host
REDIS_PORT=your-upstash-port
REDIS_PASSWORD=your-upstash-password
KAFKA_BOOTSTRAP_SERVERS=your-confluent-bootstrap-servers
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
APP_URL=https://your-frontend.vercel.app
```

### 3. MySQL → PlanetScale (Free)

| Feature | Details |
|---------|---------|
| **Free tier** | 1 GB storage, 1 billion reads/month |
| **Setup** | Create account → create database → get connection string |

**Steps:**
1. Go to [planetscale.com](https://planetscale.com) → Sign up
2. Create new database: `savorystay_db`
3. Create user with read/write access
4. Copy the connection details to your environment variables

### 4. Redis → Upstash (Free)

| Feature | Details |
|---------|---------|
| **Free tier** | 10,000 commands/day, 256 MB storage |
| **Setup** | Create account → create Redis database |

**Steps:**
1. Go to [upstash.com](https://upstash.com) → Sign up
2. Create new Redis database
3. Copy `UPSTASH_REDIS_REST_URL` and `UPSTASH_REDIS_REST_TOKEN`
4. Or use the TCP connection details

### 5. Kafka → Confluent Cloud (Free)

| Feature | Details |
|---------|---------|
| **Free tier** | $400 credit for first 30 days |
| **Setup** | Create cluster → get bootstrap servers |

**Steps:**
1. Go to [confluent.io](https://confluent.io) → Sign up
2. Create Basic cluster (uses free credit)
3. Create API key → copy bootstrap servers + credentials
4. Add to environment variables

---

## Option B: All-in-One on Oracle Cloud Free Tier (VM)

If you prefer a single VM with everything running via Docker:

### 1. Create Oracle Cloud Free VM

1. Go to [cloud.oracle.com](https://cloud.oracle.com) → Sign up
2. Create Always Free VM (4 OCPU, 24 GB RAM)
3. Choose Ubuntu 22.04 image
4. Open ports in Security List: **22, 80, 443, 8080**

### 2. SSH into your VM and deploy

```bash
# Install Docker + Docker Compose
sudo apt update && sudo apt install -y docker.io docker-compose
sudo usermod -aG docker $USER
newgrp docker

# Clone your repo
git clone https://github.com/yourusername/Resturant-App.git
cd Resturant-App

# Generate JWT secret
JWT_SECRET=$(openssl rand -hex 32)
echo "JWT_SECRET=$JWT_SECRET" > .env.prod

# Add other secrets to .env.prod
cat >> .env.prod << 'EOF'
MYSQL_PASSWORD=StrongProdPassword2026
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-gmail-app-password
APP_URL=http://your-vm-ip
KAFKA_HOST=your-vm-ip
EOF

# Start everything
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# Check status
docker compose -f docker-compose.prod.yml ps
```

### 3. Set up Nginx reverse proxy (optional, for HTTPS)

```bash
sudo apt install -y certbot python3-certbot-nginx

# Create nginx config
sudo tee /etc/nginx/sites-available/savorystay << 'EOF'
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:80;  # Frontend
        proxy_set_header Host $host;
    }

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
EOF

sudo ln -s /etc/nginx/sites-available/savorystay /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d your-domain.com
```

---

## Gmail SMTP App Password Setup

For email delivery to work in production:

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Security → 2-Step Verification (enable if not already)
3. Search "App passwords" → Create new
4. Name it "SavoryStay" → Generate
5. Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)
6. Use it as `MAIL_PASSWORD` (remove spaces)

---

## Quick Start: Deploy to Render (5 minutes)

```bash
# 1. Build the JAR locally to verify
cd springboot-backend
./mvnw package -DskipTests
cd ..

# 2. Push to GitHub
git add -A && git commit -m "feat: cloud deployment ready" && git push

# 3. Go to render.com → New → Web Service → Connect GitHub repo

# 4. Set these environment variables in Render dashboard:
#    JWT_SECRET, MYSQL_HOST, MYSQL_PORT, MYSQL_DB, MYSQL_USER, MYSQL_PASSWORD
#    REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
#    KAFKA_BOOTSTRAP_SERVERS
#    MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD
#    APP_URL

# 5. Deploy! First build takes ~5 minutes.
```

---

## Cost Summary (Free Tier)

| Service | Provider | Free Tier | Monthly Cost |
|---------|----------|-----------|-------------|
| Frontend | Vercel | 100 GB bandwidth | $0 |
| Backend | Render | 750 hours/month | $0 |
| MySQL | PlanetScale | 1 GB storage | $0 |
| Redis | Upstash | 10K commands/day | $0 |
| Kafka | Confluent Cloud | $400 credit (30 days) | ~$0 (then paid) |
| Email | Gmail SMTP | Unlimited | $0 |
| **Total** | | | **$0** |

> **Note**: After 30 days, Confluent Cloud charges ~$0.10/hour for the basic cluster. You can either:
> - Downgrade to their free-tier limited cluster
> - Switch Kafka to the Oracle Cloud VM (Option B)
> - Run Kafka in Docker on the Render service (not recommended for prod)

---

## Post-Deployment Checklist

- [ ] Frontend loads at `https://your-frontend.vercel.app`
- [ ] Backend health check returns `{"status":"UP"}` at `https://your-backend.onrender.com/api/v1/health`
- [ ] Mail health shows `"configured": true` at `/api/v1/health/mail`
- [ ] Redis health shows `"reachable": true` at `/api/v1/health/redis`
- [ ] Kafka health shows `"reachable": true` at `/api/v1/health/kafka`
- [ ] Login with demo user works: `superadmin` / `SuperAdmin@123`
- [ ] OTP email is received in inbox
- [ ] Orders can be placed and status changes trigger notifications
- [ ] Frontend API calls reach the backend (check browser network tab)

---

## Troubleshooting

### Backend won't start on Render
- Check build logs for compilation errors
- Ensure all environment variables are set
- The first deploy is slow (Maven downloads dependencies)

### Email not sending
- Verify Gmail App Password is correct
- Check `/api/v1/health/mail` endpoint
- Ensure `MAIL_PASSWORD` has no extra spaces

### Kafka consumer not processing events
- Check `/api/v1/health/kafka` shows UP
- Look at outbox_event table for pending events (`published_at IS NULL`)
- Check backend logs for `[OUTBOX]` and `[KAFKA]` entries

### Database connection refused
- Verify PlanetScale credentials
- Ensure your IP is whitelisted (PlanetScale uses SSL)
- Check connection string format: `jdbc:mysql://host:3306/db?sslMode=REQUIRED`
