# SavoryStay — Cloud Deployment Guide

This guide deploys the **full stack** (React + Spring Boot + MySQL + Redis + Kafka) to the internet.

---

## 🏆 Recommended: Oracle Cloud Always Free Tier (VM)

**Everything on one VM. Always free. No credit card charges. No spin-down.**

| Resource | Allocation |
|----------|-----------|
| OCPUs | 4 (always free) |
| RAM | 24 GB (always free) |
| Storage | 200 GB boot volume |
| Cost | **$0 forever** |

### Step 1 — Create the VM (5 minutes)

1. Go to **[cloud.oracle.com](https://cloud.oracle.com)** → Sign up (free)
2. After email verification, go to **Compute → Instances → Create Instance**
3. Settings:
   - **Name**: `savorystay`
   - **Image**: Ubuntu 22.04 (or Canonical Ubuntu 22.04)
   - **Shape**: VM.Standard.E2.1.Micro (Always Free eligible) or Ampere A1 (4 OCPUs, 24 GB — also free)
   - **SSH Key**: Paste your public key (`cat ~/.ssh/id_rsa.pub`)
4. Click **Create** and wait ~2 minutes
5. Note the **Public IP** from the instance details

### Step 2 — Open Ports

Go to **Networking → Virtual Cloud Networks → your VCN → Security Lists → Default Security List → Add Ingress Rules**:

| Port | Source | Purpose |
|------|--------|---------|
| 22 | 0.0.0.0/0 | SSH access |
| 80 | 0.0.0.0/0 | Frontend (HTTP) |
| 8080 | 0.0.0.0/0 | Backend API |
| 8081 | 0.0.0.0/0 | Kafka UI (optional) |

### Step 3 — Deploy (one command)

```bash
# SSH into your VM
ssh ubuntu@<YOUR_PUBLIC_IP>

# Run the one-click deploy script
bash <(curl -fsSL https://raw.githubusercontent.com/InfiniteSoulKKS/Resturant-App/main/deploy.sh)
```

That's it! The script:
- Installs Docker
- Clones the repo
- Generates a JWT secret
- Builds and starts all 5 services

### Step 4 — (Optional) Add Gmail for Email OTPs

Edit the config and restart:

```bash
nano ~/savorystay/.env.prod
# Set MAIL_USERNAME=your-email@gmail.com
# Set MAIL_PASSWORD=your-16-char-app-password

cd ~/savorystay
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

> Without Gmail credentials, the system works in **demo mode** — OTPs are logged to the console instead of sent via email.

### Step 5 — Verify

```bash
# Backend health
curl http://localhost:8080/api/v1/health

# All services
curl http://localhost:8080/api/v1/health/mail
curl http://localhost:8080/api/v1/health/redis
curl http://localhost:8080/api/v1/health/kafka
```

---

## Your Live URLs

| Service | URL |
|---------|-----|
| 🌐 Frontend | `http://<YOUR_PUBLIC_IP>` |
| 🔧 Backend API | `http://<YOUR_PUBLIC_IP>:8080/api/v1` |
| ❤️ Health | `http://<YOUR_PUBLIC_IP>:8080/api/v1/health` |
| 📊 Kafka UI | `http://<YOUR_PUBLIC_IP>:8081` |
| 📋 Demo Login | `superadmin` / `SuperAdmin@123` |

---

## Updating the Deployed App

```bash
ssh ubuntu@<YOUR_PUBLIC_IP>
cd ~/savorystay
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

---

## Managing the Stack

```bash
# View logs (all services)
cd ~/savorystay
docker compose -f docker-compose.prod.yml logs -f

# View logs (specific service)
docker compose -f docker-compose.prod.yml logs -f backend
docker compose -f docker-compose.prod.yml logs -f kafka

# Restart a service
docker compose -f docker-compose.prod.yml restart backend

# Stop everything
docker compose -f docker-compose.prod.yml down

# Stop and remove data (fresh start)
docker compose -f docker-compose.prod.yml down -v
```

---

## Gmail SMTP App Password Setup

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. **Security** → **2-Step Verification** (enable if not already)
3. Search **"App passwords"** → Create new
4. Name it "SavoryStay" → Generate
5. Copy the 16-character password (e.g., `abcd efgh ijkl mnop`)
6. Use as `MAIL_PASSWORD` (remove spaces)

---

## Architecture (Running on VM)

```
┌─────────────────────────────────────────────────┐
│              Oracle Cloud VM                     │
│                                                  │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐    │
│  │ Frontend │──▶│ Backend  │──▶│  MySQL   │    │
│  │ (Nginx)  │   │ (Spring) │   │ (Docker) │    │
│  │  :80     │   │  :8080   │   │  :3306   │    │
│  └──────────┘   └────┬─────┘   └──────────┘    │
│                      │                           │
│                ┌─────┼─────┐                     │
│                ▼     ▼     ▼                     │
│          ┌────────┐ ┌────┐ ┌────────┐           │
│          │ Redis  │ │Kafka│ │ Gmail  │           │
│          │ :6379  │ │:9092│ │  SMTP  │           │
│          └────────┘ └────┘ └────────┘           │
└─────────────────────────────────────────────────┘
```

---

## Troubleshooting

### Services won't start
```bash
docker compose -f docker-compose.prod.yml ps          # Check status
docker compose -f docker-compose.prod.yml logs backend # Check backend logs
```

### MySQL connection refused
- Wait 30 seconds after boot — MySQL takes time to initialize
- Check: `docker compose -f docker-compose.prod.yml logs mysql`

### Kafka consumer not processing
- Check: `curl http://localhost:8080/api/v1/health/kafka`
- Look at Kafka UI: `http://<YOUR_IP>:8081`

### Email not sending
- Verify Gmail App Password is correct
- Check: `curl http://localhost:8080/api/v1/health/mail`
- System falls back to demo mode (OTP in console) without email

### Can't connect from browser
- Verify Oracle Cloud Security List allows ports 80 and 8080
- Check: `curl http://<YOUR_IP>:80` from your local machine
