#!/bin/bash

# Monitoring Script for MealSync
# Displays real-time status of the application

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

clear

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║           MealSync Production Monitor                      ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Function to check if service is running
check_service() {
    local service=$1
    if docker ps | grep -q "$service"; then
        echo -e "${GREEN}✓${NC}"
    else
        echo -e "${RED}✗${NC}"
    fi
}

# Function to get container status
get_status() {
    local service=$1
    local status=$(docker inspect --format='{{.State.Status}}' $service 2>/dev/null)
    if [ "$status" == "running" ]; then
        echo -e "${GREEN}Running${NC}"
    elif [ "$status" == "exited" ]; then
        echo -e "${RED}Exited${NC}"
    else
        echo -e "${YELLOW}Unknown${NC}"
    fi
}

# Function to get health status
get_health() {
    local service=$1
    local health=$(docker inspect --format='{{.State.Health.Status}}' $service 2>/dev/null)
    if [ "$health" == "healthy" ]; then
        echo -e "${GREEN}Healthy${NC}"
    elif [ "$health" == "unhealthy" ]; then
        echo -e "${RED}Unhealthy${NC}"
    elif [ "$health" == "starting" ]; then
        echo -e "${YELLOW}Starting${NC}"
    else
        echo -e "${YELLOW}N/A${NC}"
    fi
}

# Container Status
echo -e "${BLUE}Container Status:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
printf "%-20s %-15s %-15s\n" "Service" "Status" "Health"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
printf "%-20s %-15b %-15b\n" "mealsync-app" "$(get_status mealsync-app)" "$(get_health mealsync-app)"
printf "%-20s %-15b %-15b\n" "mealsync-nginx" "$(get_status mealsync-nginx)" "$(get_health mealsync-nginx)"
echo ""

# Application Health
echo -e "${BLUE}Application Health:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
HEALTH_RESPONSE=$(curl -s http://localhost/actuator/health 2>/dev/null)
if [ $? -eq 0 ]; then
    HEALTH_STATUS=$(echo $HEALTH_RESPONSE | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    if [ "$HEALTH_STATUS" == "UP" ]; then
        echo -e "Status: ${GREEN}UP${NC}"
    else
        echo -e "Status: ${RED}DOWN${NC}"
    fi
    echo "Response: $HEALTH_RESPONSE"
else
    echo -e "Status: ${RED}UNREACHABLE${NC}"
fi
echo ""

# Resource Usage
echo -e "${BLUE}Resource Usage:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if docker ps | grep -q "mealsync-app"; then
    STATS=$(docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" mealsync-app 2>/dev/null | tail -n 1)
    echo "$STATS"
else
    echo -e "${RED}Container not running${NC}"
fi
echo ""

# System Resources
echo -e "${BLUE}System Resources:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "CPU Load: $(uptime | awk -F'load average:' '{print $2}')"
echo "Memory: $(free -h | awk '/^Mem:/ {printf "%s / %s (%.1f%%)", $3, $2, $3/$2 * 100}')"
echo "Disk: $(df -h / | awk 'NR==2 {printf "%s / %s (%s)", $3, $2, $5}')"
echo ""

# Docker Disk Usage
echo -e "${BLUE}Docker Disk Usage:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
docker system df
echo ""

# Recent Logs
echo -e "${BLUE}Recent Logs (last 10 lines):${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if docker ps | grep -q "mealsync-app"; then
    docker logs --tail 10 mealsync-app 2>&1 | tail -10
else
    echo -e "${RED}Container not running${NC}"
fi
echo ""

# Active Connections (if nginx is running)
if docker ps | grep -q "mealsync-nginx"; then
    echo -e "${BLUE}Active Connections:${NC}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    CONNECTIONS=$(docker exec mealsync-nginx sh -c 'ps aux | grep nginx | wc -l' 2>/dev/null || echo "N/A")
    echo "Nginx workers: $CONNECTIONS"
    echo ""
fi

# Database Connections (if we can access actuator metrics)
echo -e "${BLUE}Database Connections:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
ACTIVE_CONN=$(curl -s http://localhost/actuator/metrics/hikari.connections.active 2>/dev/null | grep -o '"value":[0-9]*' | cut -d':' -f2)
IDLE_CONN=$(curl -s http://localhost/actuator/metrics/hikari.connections.idle 2>/dev/null | grep -o '"value":[0-9]*' | cut -d':' -f2)
if [ ! -z "$ACTIVE_CONN" ]; then
    echo "Active: $ACTIVE_CONN"
    echo "Idle: $IDLE_CONN"
else
    echo -e "${YELLOW}Metrics not available${NC}"
fi
echo ""

# Quick Actions
echo -e "${BLUE}Quick Actions:${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "View logs:     docker compose -f docker-compose.prod.yml logs -f"
echo "Restart app:   docker compose -f docker-compose.prod.yml restart mealsync-app"
echo "Scale up:      docker compose -f docker-compose.prod.yml up -d --scale mealsync-app=3"
echo "Health check:  curl http://localhost/actuator/health"
echo "Cleanup:       docker system prune -af"
echo ""

echo -e "${YELLOW}Last updated: $(date)${NC}"
echo ""
