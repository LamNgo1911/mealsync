#!/bin/bash

# Initial Setup Script for MealSync Production Deployment
# This script prepares a fresh server for running MealSync

set -e

echo "=== MealSync Initial Setup ==="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo -e "${RED}Please run as root (use sudo)${NC}"
    exit 1
fi

echo -e "${GREEN}Step 1: Updating system packages...${NC}"
apt-get update
apt-get upgrade -y

echo ""
echo -e "${GREEN}Step 2: Installing Docker...${NC}"
if ! command -v docker &> /dev/null; then
    # Install Docker
    apt-get install -y \
        ca-certificates \
        curl \
        gnupg \
        lsb-release

    # Add Docker's official GPG key
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg

    # Set up the repository
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null

    # Install Docker Engine
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    # Start and enable Docker
    systemctl start docker
    systemctl enable docker

    echo -e "${GREEN}Docker installed successfully!${NC}"
else
    echo -e "${YELLOW}Docker already installed${NC}"
fi

echo ""
echo -e "${GREEN}Step 3: Installing Docker Compose...${NC}"
if ! command -v docker compose &> /dev/null; then
    apt-get install -y docker-compose-plugin
    echo -e "${GREEN}Docker Compose installed successfully!${NC}"
else
    echo -e "${YELLOW}Docker Compose already installed${NC}"
fi

echo ""
echo -e "${GREEN}Step 4: Installing Git...${NC}"
if ! command -v git &> /dev/null; then
    apt-get install -y git
    echo -e "${GREEN}Git installed successfully!${NC}"
else
    echo -e "${YELLOW}Git already installed${NC}"
fi

echo ""
echo -e "${GREEN}Step 5: Installing useful utilities...${NC}"
apt-get install -y \
    htop \
    curl \
    wget \
    vim \
    net-tools \
    ufw \
    fail2ban

echo ""
echo -e "${GREEN}Step 6: Configuring firewall...${NC}"
# Allow SSH, HTTP, HTTPS
ufw --force enable
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP
ufw allow 443/tcp   # HTTPS
ufw status

echo ""
echo -e "${GREEN}Step 7: Setting up swap (if needed)...${NC}"
if [ ! -f /swapfile ]; then
    # Create 2GB swap file
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' | tee -a /etc/fstab
    echo -e "${GREEN}Swap file created (2GB)${NC}"
else
    echo -e "${YELLOW}Swap file already exists${NC}"
fi

echo ""
echo -e "${GREEN}Step 8: Optimizing system for Docker...${NC}"
# Increase file descriptors
echo "fs.file-max = 65536" >> /etc/sysctl.conf
echo "* soft nofile 65536" >> /etc/security/limits.conf
echo "* hard nofile 65536" >> /etc/security/limits.conf
sysctl -p

echo ""
echo -e "${GREEN}Step 9: Setting up log rotation for Docker...${NC}"
cat > /etc/docker/daemon.json <<EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF
systemctl restart docker

echo ""
echo -e "${GREEN}Step 10: Creating deployment directory...${NC}"
DEPLOY_DIR="/opt/mealsync"
mkdir -p $DEPLOY_DIR
cd $DEPLOY_DIR

echo ""
echo -e "${GREEN}Step 11: Cloning repository...${NC}"
if [ ! -d "$DEPLOY_DIR/.git" ]; then
    read -p "Enter GitHub repository URL (or press Enter for default): " REPO_URL
    REPO_URL=${REPO_URL:-"https://github.com/LamNgo1911/mealsync.git"}
    git clone $REPO_URL .
    echo -e "${GREEN}Repository cloned successfully!${NC}"
else
    echo -e "${YELLOW}Repository already exists, pulling latest changes...${NC}"
    git pull origin main
fi

echo ""
echo -e "${GREEN}Step 12: Setting up environment variables...${NC}"
if [ ! -f "$DEPLOY_DIR/.env" ]; then
    echo -e "${YELLOW}Creating .env file template...${NC}"
    cat > $DEPLOY_DIR/.env <<EOF
# Docker Hub
DOCKER_HUB_USERNAME=your_username

# Database Configuration
RDS_ENDPOINT=your-rds-endpoint.rds.amazonaws.com
RDS_PORT=5432
RDS_DB_NAME=mealsync_prod
RDS_USERNAME=postgres
RDS_PASSWORD=your_secure_password

# JWT Configuration
JWT_SECRET=your_jwt_secret_key_here_minimum_256_bits
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# AWS Configuration
AWS_ACCESS_KEY_ID=your_aws_access_key
AWS_SECRET_ACCESS_KEY=your_aws_secret_key
AWS_REGION=us-east-1
AWS_S3_BUCKET_NAME=mealsync-images

# Gemini 2.5 Flash Image (Nano Banana) - Image generation
GEMINI_API_BASE_URL=https://generativelanguage.googleapis.com
GEMINI_API_KEY=your_gemini_api_key

# Google OAuth
GOOGLE_CLIENT_ID=your_google_client_id
EOF
    echo -e "${RED}IMPORTANT: Edit $DEPLOY_DIR/.env with your actual credentials!${NC}"
    echo -e "${YELLOW}Run: nano $DEPLOY_DIR/.env${NC}"
else
    echo -e "${YELLOW}.env file already exists${NC}"
fi

echo ""
echo -e "${GREEN}Step 13: Creating nginx directories...${NC}"
mkdir -p $DEPLOY_DIR/nginx/ssl

echo ""
echo -e "${GREEN}Step 14: Setting up Docker cleanup cron job...${NC}"
CLEANUP_CRON="0 3 * * * docker system prune -af --filter 'until=48h' >> /var/log/docker-cleanup.log 2>&1"
(crontab -l 2>/dev/null | grep -v "docker system prune"; echo "$CLEANUP_CRON") | crontab -

echo ""
echo -e "${GREEN}=== Setup Complete! ===${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo ""
echo "1. Edit environment variables:"
echo "   nano $DEPLOY_DIR/.env"
echo ""
echo "2. Add your Google credentials JSON file:"
echo "   # Upload your mealsync-*.json file to $DEPLOY_DIR/"
echo ""
echo "3. (Optional) Set up SSL certificate:"
echo "   cd $DEPLOY_DIR"
echo "   sudo ./scripts/setup-ssl.sh yourdomain.com"
echo ""
echo "4. Deploy the application:"
echo "   cd $DEPLOY_DIR"
echo "   docker compose -f docker-compose.prod.yml up -d"
echo ""
echo "5. Check deployment status:"
echo "   docker compose -f docker-compose.prod.yml ps"
echo "   curl http://localhost/actuator/health"
echo ""
echo -e "${GREEN}System Information:${NC}"
echo "  Deployment Directory: $DEPLOY_DIR"
echo "  Docker Version: $(docker --version)"
echo "  Docker Compose Version: $(docker compose version)"
echo "  Available Memory: $(free -h | awk '/^Mem:/ {print $7}')"
echo "  Available Disk: $(df -h / | awk 'NR==2 {print $4}')"
echo ""
