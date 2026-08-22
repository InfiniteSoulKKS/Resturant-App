#!/usr/bin/env bash
# =============================================================================
# SavoryStay — Manual Server Deployment (existing MySQL)
# =============================================================================
# Run this on your Oracle Cloud VM:
#   bash deploy-server.sh
#
# Prerequisites:
#   - MySQL already running on the server
#   - Java 17+ installed (or this script will install it)
#   - Nginx installed (or this script will install it)
# =============================================================================

set -euo pipefail

APP_DIR="$HOME/savorystay"
REPO_URL="https://github.com/InfiniteSoulKKS/Resturant-App.git"

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

# ── 1. Install Java 17 ─────────────────────────────────────────────────────
if ! command -v java &>/dev/null || ! java -version 2>&1 | grep -q "17\|21"; then
    info "Installing Java 17..."
    sudo apt-get update -qq
    sudo apt-get install -y -qq openjdk-17-jdk
    ok "Java installed"
else
    ok "Java already installed"
    java -version 2>&1 | head -1
fi

# ── 2. Install Nginx ───────────────────────────────────────────────────────
if ! command -v nginx &>/dev/null; then
    info "Installing Nginx..."
    sudo apt-get install -y -qq nginx
    sudo systemctl enable nginx
    sudo systemctl start nginx
    ok "Nginx installed"
else
    ok "Nginx already installed"
fi

# ── 3. Install Node.js (for building frontend) ─────────────────────────────
if ! command -v node &>/dev/null || ! node -v | grep -q "v2"; then
    info "Installing Node.js 20..."
    curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
    sudo apt-get install -y -qq nodejs
    ok "Node.js installed"
else
    ok "Node.js already installed"
    node -v
fi

# ── 4. Clone or update repo ────────────────────────────────────────────────
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

# ── 5. Create .env for backend ─────────────────────────────────────────────
ENV_FILE="$APP_DIR/springboot-backend/.env"
if [ ! -f "$ENV_FILE" ]; then
    info "Generating .env..."
    JWT_SECRET=$(openssl rand -hex 32)

    cat > "$ENV_FILE" << EOF
# SavoryStay Backend Environment
# Generated on $(date -u +"%Y-%m-%d %H:%M:%S UTC")

# ── MySQL (use your existing MySQL) ───────────────────────────────────────
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DB=savorystay_db
MYSQL_USER=root
MYSQL_PASSWORD=StrongRoot@2026

# ── JWT ──────────────────────────────────────────────────────────────────
JWT_SECRET=${JWT_SECRET}

# ── Redis (optional — install or use existing) ───────────────────────────
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ── Email (Gmail SMTP — optional for demo) ──────────────────────────────
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=YOUR_EMAIL@gmail.com
MAIL_PASSWORD=YOUR_APP_PASSWORD

# ── Kafka (not needed for manual deploy — outbox will retry) ────────────
KAFKA_BOOTSTRAP_SERVERS=localhost:29092

# ── Public URL ───────────────────────────────────────────────────────────
APP_URL=http://${PUBLIC_IP}
EOF
    ok ".env created at $ENV_FILE"
    warn "⚠️  EDIT .env to set your MySQL password and optionally Gmail credentials"
else
    ok ".env already exists"
fi

# ── 6. Create MySQL database ──────────────────────────────────────────────
info "Ensuring MySQL database exists..."
DB_NAME=$(grep MYSQL_DB "$ENV_FILE" | cut -d= -f2)
DB_PASS=$(grep MYSQL_PASSWORD "$ENV_FILE" | cut -d= -f2)
mysql -u root -p"${DB_PASS}" -e "CREATE DATABASE IF NOT EXISTS ${DB_NAME};" 2>/dev/null || {
    warn "Could not auto-create database. Please create it manually:"
    warn "  mysql -u root -p -e 'CREATE DATABASE savorystay_db;'"
}
ok "Database ready"

# ── 7. Build backend JAR ──────────────────────────────────────────────────
info "Building backend JAR..."
cd "$APP_DIR/springboot-backend"
set -a && source .env && set +a
./mvnw clean package -DskipTests -B -q
JAR_FILE=$(ls target/*.jar | grep -v original | head -1)
if [ -z "$JAR_FILE" ]; then
    err "JAR build failed"
fi
ok "Backend JAR built: $JAR_FILE"

# ── 8. Build frontend ─────────────────────────────────────────────────────
info "Building frontend..."
cd "$APP_DIR"
VITE_API_URL="http://${PUBLIC_IP}:8080" npm run build
if [ ! -d "dist" ]; then
    err "Frontend build failed"
fi
ok "Frontend built: dist/"

# ── 9. Configure Nginx ────────────────────────────────────────────────────
info "Configuring Nginx..."
sudo tee /etc/nginx/sites-available/savorystay > /dev/null << 'NGINX'
server {
    listen 80;
    server_name _;

    # Gzip
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 256;

    # React SPA
    location / {
        root /home/APP_USER/savorystay/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # API proxy
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE support
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 86400s;
    }

    # Static assets cache
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        root /home/APP_USER/savorystay/dist;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
NGINX

# Replace APP_USER with actual username
sed -i "s|APP_USER|$USER|g" /etc/nginx/sites-available/savorystay

sudo ln -sf /etc/nginx/sites-available/savorystay /etc/nginx/sites-enabled/savorystay
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
ok "Nginx configured"

# ── 10. Create systemd service for backend ─────────────────────────────────
info "Creating systemd service..."
sudo tee /etc/systemd/system/savorystay-backend.service > /dev/null << EOF
[Unit]
Description=SavoryStay Backend
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=$USER
WorkingDirectory=$APP_DIR/springboot-backend
EnvironmentFile=$APP_DIR/springboot-backend/.env
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar $APP_DIR/springboot-backend/$JAR_FILE
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable savorystay-backend
sudo systemctl start savorystay-backend
ok "Backend service started"

# ── 11. Wait for backend to start ──────────────────────────────────────────
info "Waiting for backend to start..."
MAX_WAIT=60
ELAPSED=0
while [ $ELAPSED -lt $MAX_WAIT ]; do
    if curl -s --max-time 3 http://localhost:8080/api/v1/health 2>/dev/null | grep -q '"status"'; then
        ok "Backend is healthy!"
        break
    fi
    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo -n "."
done
echo ""

# ── 12. Open firewall ports ───────────────────────────────────────────────
info "Opening firewall ports..."
sudo ufw allow 80/tcp 2>/dev/null || true
sudo ufw allow 8080/tcp 2>/dev/null || true
ok "Ports opened"

# ── 13. Print results ─────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  🎉 SavoryStay is LIVE!${NC}"
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "  🌐 Frontend:     ${CYAN}http://${PUBLIC_IP}${NC}"
echo -e "  🔧 Backend API:  ${CYAN}http://${PUBLIC_IP}:8080/api/v1${NC}"
echo -e "  ❤️  Health:        ${CYAN}http://${PUBLIC_IP}:8080/api/v1/health${NC}"
echo ""
echo -e "  📋 Demo Login:    ${YELLOW}superadmin${NC} / ${YELLOW}SuperAdmin@123${NC}"
echo ""
echo -e "  🔄 Update:        ${CYAN}cd $APP_DIR && git pull && sudo systemctl restart savorystay-backend${NC}"
echo -e "  📊 Logs:          ${CYAN}sudo journalctl -u savorystay-backend -f${NC}"
echo -e "  📂 Config:        ${CYAN}${APP_DIR}/springboot-backend/.env${NC}"
echo ""
echo -e "${GREEN}══════════════════════════════════════════════════════════════${NC}"
