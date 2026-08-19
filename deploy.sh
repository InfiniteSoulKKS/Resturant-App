#!/usr/bin/env bash
# =============================================================================
# SavoryStay — One-Click Deploy (Oracle Cloud Free Tier VM)
# =============================================================================
# Run this on a fresh Ubuntu 22.04 VM after SSH'ing in:
#   bash deploy.sh
#
# What it does:
#   1. Installs Docker + Docker Compose
#   2. Clones the repo (or pulls latest)
#   3. Generates a JWT secret
#   4. Builds and starts all services: MySQL, Redis, Kafka, Backend, Frontend
#   5. Prints the public URL
# =============================================================================

set -euo pipefail

REPO_URL="https://github.com/InfiniteSoulKKS/Resturant-App.git"
APP_DIR="$HOME/savorystay"
ENV_FILE="$APP_DIR/.env.prod"

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

# ── 0. Detect public IP ─────────────────────────────────────────────────────
info "Detecting public IP..."
PUBLIC_IP=$(curl -s --max-time 5 https://api.ipify.org || echo "")
if [ -z "$PUBLIC_IP" ]; then
    PUBLIC_IP=$(curl -s --max-time 5 https://ifconfig.me || echo "")
fi
if [ -z "$PUBLIC_IP" ]; then
    warn "Could not auto-detect public IP. Using localhost."
    PUBLIC_IP="localhost"
fi
ok "Public IP: $PUBLIC_IP"

# ── 1. Install Docker ───────────────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
    info "Installing Docker..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq ca-certificates curl gnupg lsb-release
    sudo mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null || true
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
    sudo apt-get update -qq
    sudo apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-compose-plugin
    sudo usermod -aG docker "$USER" 2>/dev/null || true
    ok "Docker installed"
else
    ok "Docker already installed"
fi

# Ensure Docker is running
sudo systemctl enable docker
sudo systemctl start docker

# ── 2. Clone or update repo ─────────────────────────────────────────────────
if [ -d "$APP_DIR/.git" ]; then
    info "Pulling latest changes..."
    cd "$APP_DIR"
    git pull origin main
    ok "Repo updated"
else
    info "Cloning repository..."
    rm -rf "$APP_DIR"
    git clone "$REPO_URL" "$APP_DIR"
    cd "$APP_DIR"
    ok "Repo cloned"
fi

# ── 3. Create .env.prod ─────────────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
    info "Generating .env.prod..."
    JWT_SECRET=$(openssl rand -hex 32)
    MYSQL_PASSWORD=$(openssl rand -hex 16)
    
    cat > "$ENV_FILE" << EOF
# SavoryStay Production Environment
# Generated on $(date -u +"%Y-%m-%d %H:%M:%S UTC")

# ── Database ────────────────────────────────────────────────────────────────
MYSQL_DB=savorystay_db
MYSQL_PASSWORD=${MYSQL_PASSWORD}

# ── JWT (required — app won't sign tokens without this) ─────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Redis ───────────────────────────────────────────────────────────────────
REDIS_PASSWORD=

# ── Email (Gmail SMTP) ─────────────────────────────────────────────────────
# Get an App Password: https://myaccount.google.com/apppasswords
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_EMAIL@gmail.com
MAIL_PASSWORD=YOUR_APP_PASSWORD

# ── Kafka ───────────────────────────────────────────────────────────────────
KAFKA_HOST=${PUBLIC_IP}

# ── App URL (public) ────────────────────────────────────────────────────────
APP_URL=http://${PUBLIC_IP}

# ── External ports ──────────────────────────────────────────────────────────
FRONTEND_EXTERNAL_PORT=80
BACKEND_EXTERNAL_PORT=8080
MYSQL_EXTERNAL_PORT=3306
REDIS_EXTERNAL_PORT=6379
KAFKA_EXTERNAL_PORT=29092

# ── Payment / SMS (mock for demo) ──────────────────────────────────────────
STRIPE_SECRET_KEY=sk_test_mock
STRIPE_WEBHOOK_SECRET=whsec_mock
PAYPAL_CLIENT_ID=mock
PAYPAL_CLIENT_SECRET=mock
TWILIO_ACCOUNT_SID=your_account_sid
TWILIO_AUTH_TOKEN=your_auth_token
TWILIO_PHONE_NUMBER=+1234567890
TWILIO_WHATSAPP_NUMBER=+14155238886
EOF
    ok ".env.prod created at $ENV_FILE"
    warn "⚠️  EDIT .env.prod to add your Gmail credentials:"
    warn "    MAIL_USERNAME=your-email@gmail.com"
    warn "    MAIL_PASSWORD=your-16-char-app-password"
    warn "    (Email works in demo mode without this — OTPs logged to console)"
else
    ok ".env.prod already exists"
fi

# ── 4. Build and start ──────────────────────────────────────────────────────
info "Building and starting all services..."
info "(First build takes 3-5 minutes — Maven downloads dependencies)"

docker compose -f docker-compose.prod.yml --env-file "$ENV_FILE" up -d --build 2>&1 | tail -20

# ── 5. Wait for health checks ──────────────────────────────────────────────
info "Waiting for services to be healthy (up to 90s)..."
MAX_WAIT=90
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
    BACKEND_HEALTH=$(curl -s --max-time 3 http://localhost:8080/api/v1/health 2>/dev/null || echo "")
    if echo "$BACKEND_HEALTH" | grep -q '"status"'; then
        ok "Backend is healthy!"
        break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo -n "."
done
echo ""

# ── 6. Print results ────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  🎉 SavoryStay is LIVE!${NC}"
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  🌐 Frontend:     ${CYAN}http://${PUBLIC_IP}${NC}"
echo -e "  🔧 Backend API:  ${CYAN}http://${PUBLIC_IP}:8080/api/v1${NC}"
echo -e "  ❤️  Health Check:  ${CYAN}http://${PUBLIC_IP}:8080/api/v1/health${NC}"
echo -e "  📧 Mail Health:   ${CYAN}http://${PUBLIC_IP}:8080/api/v1/health/mail${NC}"
echo -e "  🗄️  Redis Health:  ${CYAN}http://${PUBLIC_IP}:8080/api/v1/health/redis${NC}"
echo -e "  📨 Kafka Health:  ${CYAN}http://${PUBLIC_IP}:8080/api/v1/health/kafka${NC}"
echo ""
echo -e "  📋 Demo Login:    ${YELLOW}superadmin${NC} / ${YELLOW}SuperAdmin@123${NC}"
echo ""
echo -e "  📂 Config:        ${CYAN}${ENV_FILE}${NC}"
echo -e "  📂 Logs:          ${CYAN}docker compose -f docker-compose.prod.yml logs -f${NC}"
echo -e "  🔄 Update:        ${CYAN}cd $APP_DIR && git pull && docker compose -f docker-compose.prod.yml up -d --build${NC}"
echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
