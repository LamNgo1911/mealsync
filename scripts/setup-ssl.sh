#!/bin/bash

# SSL Setup Script for MealSync
# This script helps you set up SSL certificates using Let's Encrypt

set -e

echo "=== MealSync SSL Setup ==="
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then 
    echo "Please run as root (use sudo)"
    exit 1
fi

# Check if domain is provided
if [ -z "$1" ]; then
    echo "Usage: sudo ./setup-ssl.sh yourdomain.com"
    echo "Example: sudo ./setup-ssl.sh mealsync.example.com"
    exit 1
fi

DOMAIN=$1
EMAIL=${2:-"admin@$DOMAIN"}

echo "Domain: $DOMAIN"
echo "Email: $EMAIL"
echo ""

# Install certbot if not already installed
if ! command -v certbot &> /dev/null; then
    echo "Installing certbot..."
    apt-get update
    apt-get install -y certbot
fi

# Stop nginx if running (to free port 80)
echo "Stopping nginx temporarily..."
docker compose -f docker-compose.prod.yml stop nginx || true

# Obtain certificate
echo "Obtaining SSL certificate from Let's Encrypt..."
certbot certonly \
    --standalone \
    --non-interactive \
    --agree-tos \
    --email "$EMAIL" \
    -d "$DOMAIN"

# Create nginx ssl directory
echo "Creating SSL directory..."
mkdir -p nginx/ssl

# Copy certificates
echo "Copying certificates..."
cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem nginx/ssl/
cp /etc/letsencrypt/live/$DOMAIN/privkey.pem nginx/ssl/

# Set permissions
chmod 644 nginx/ssl/fullchain.pem
chmod 600 nginx/ssl/privkey.pem

# Update nginx configuration
echo "Updating nginx configuration..."
sed -i "s/your-domain.com/$DOMAIN/g" nginx/nginx.conf

# Uncomment HTTPS server block
sed -i 's/# server {/server {/g' nginx/nginx.conf
sed -i 's/#     listen 443/    listen 443/g' nginx/nginx.conf
sed -i 's/#     server_name/    server_name/g' nginx/nginx.conf
sed -i 's/#     ssl_/    ssl_/g' nginx/nginx.conf
sed -i 's/#     add_header/    add_header/g' nginx/nginx.conf
sed -i 's/#     location/    location/g' nginx/nginx.conf
sed -i 's/#     }/    }/g' nginx/nginx.conf
sed -i 's/# }/}/g' nginx/nginx.conf

# Uncomment HTTP to HTTPS redirect
sed -i 's/# location \/ {/location \/ {/g' nginx/nginx.conf
sed -i 's/#     return 301/    return 301/g' nginx/nginx.conf

# Start nginx
echo "Starting nginx with SSL..."
docker compose -f docker-compose.prod.yml up -d nginx

# Setup auto-renewal
echo "Setting up auto-renewal..."
CRON_CMD="0 0 * * * certbot renew --quiet --post-hook 'cp /etc/letsencrypt/live/$DOMAIN/fullchain.pem $(pwd)/nginx/ssl/ && cp /etc/letsencrypt/live/$DOMAIN/privkey.pem $(pwd)/nginx/ssl/ && docker compose -f $(pwd)/docker-compose.prod.yml restart nginx'"

# Add to crontab if not already present
(crontab -l 2>/dev/null | grep -v "certbot renew"; echo "$CRON_CMD") | crontab -

echo ""
echo "=== SSL Setup Complete! ==="
echo ""
echo "Your site is now available at:"
echo "  https://$DOMAIN"
echo ""
echo "Certificate will auto-renew every night at midnight."
echo ""
echo "To verify SSL:"
echo "  curl https://$DOMAIN/actuator/health"
echo ""
