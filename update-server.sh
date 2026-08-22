#!/usr/bin/env bash
# =============================================================================
# SavoryStay — Quick Update (pull + rebuild + restart)
# =============================================================================
# Run this on the server after pushing new code:
#   bash update-server.sh
# =============================================================================

set -euo pipefail

APP_DIR="$HOME/savorystay"
ENV_FILE="$APP_DIR/springboot-backend/.env"

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}[1/5]${NC} Pulling latest code..."
cd "$APP_DIR"
git pull origin main

echo -e "${CYAN}[2/5]${NC} Building backend..."
cd "$APP_DIR/springboot-backend"
set -a && source .env 2>/dev/null && set +a
./mvnw clean package -DskipTests -B -q

echo -e "${CYAN}[3/5]${NC} Building frontend..."
cd "$APP_DIR"
VITE_API_URL=$(grep APP_URL "$ENV_FILE" 2>/dev/null | cut -d= -f2 || echo "http://localhost")
npm run build

echo -e "${CYAN}[4/5]${NC} Restarting backend..."
sudo systemctl restart savorystay-backend

echo -e "${CYAN}[5/5]${NC} Reloading Nginx..."
sudo systemctl reload nginx

echo ""
echo -e "${GREEN}✅ Update complete!${NC}"
echo -e "   Frontend:  http://$(curl -s https://api.ipify.org)"
echo -e "   Backend:   http://$(curl -s https://api.ipify.org):8080/api/v1/health"
