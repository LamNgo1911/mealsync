# Deployment Cheat Sheet

Quick reference for common deployment tasks.

---

## 🚀 Quick Start

### Development
```bash
# Start local environment
docker compose -f docker-compose.dev.yml up --build

# View logs
docker compose -f docker-compose.dev.yml logs -f

# Stop
docker compose -f docker-compose.dev.yml down
```

### Staging
```bash
# Deploy to staging
docker compose -f docker-compose.staging.yml up -d

# Check health
curl http://localhost:8081/actuator/health

# View logs
docker compose -f docker-compose.staging.yml logs -f mealsync-app
```

### Production
```bash
# Deploy to production (zero-downtime)
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --no-deps mealsync-app

# Check status
docker compose -f docker-compose.prod.yml ps

# View logs
docker compose -f docker-compose.prod.yml logs -f mealsync-app
```

---

## 🔍 Health Checks

```bash
# Application health
curl http://localhost:8081/actuator/health

# Detailed health (with auth)
curl http://localhost:8081/actuator/health?show-details=true

# Liveness probe
curl http://localhost:8081/actuator/health/liveness

# Readiness probe
curl http://localhost:8081/actuator/health/readiness

# Metrics
curl http://localhost:8081/actuator/metrics

# Prometheus metrics
curl http://localhost:8081/actuator/prometheus
```

---

## 🐳 Docker Commands

### Build & Push
```bash
# Build image
docker build -t username/mealsync-app:latest .

# Push to Docker Hub
docker push username/mealsync-app:latest

# Build with tag
docker build -t username/mealsync-app:v1.0.0 .
```

### Run & Manage
```bash
# Run single container
docker run -d -p 8081:8081 --name mealsync-app username/mealsync-app:latest

# Stop container
docker stop mealsync-app

# Remove container
docker rm mealsync-app

# View logs
docker logs -f mealsync-app

# Execute command in container
docker exec -it mealsync-app /bin/sh

# Inspect container
docker inspect mealsync-app
```

### Cleanup
```bash
# Remove unused images
docker image prune -af

# Remove unused volumes
docker volume prune -f

# Remove unused containers
docker container prune -f

# Remove everything
docker system prune -af --volumes

# Check disk usage
docker system df
```

---

## 📊 Monitoring

### Container Stats
```bash
# Real-time stats
docker stats

# Specific container
docker stats mealsync-app

# Memory usage
docker stats --format "table {{.Container}}\t{{.MemUsage}}"
```

### Logs
```bash
# Follow logs
docker compose -f docker-compose.prod.yml logs -f

# Last 100 lines
docker compose -f docker-compose.prod.yml logs --tail=100

# Specific service
docker compose -f docker-compose.prod.yml logs -f mealsync-app

# Since timestamp
docker compose -f docker-compose.prod.yml logs --since 2024-01-01T00:00:00
```

### Database
```bash
# Connect to PostgreSQL
docker exec -it mealsync-postgres-dev psql -U postgres -d mealsync_dev

# Check connections
SELECT count(*) FROM pg_stat_activity;

# Check database size
SELECT pg_size_pretty(pg_database_size('mealsync_dev'));

# Show slow queries
SELECT query, mean_exec_time, calls 
FROM pg_stat_statements 
ORDER BY mean_exec_time DESC 
LIMIT 10;
```

---

## 🔧 Troubleshooting

### Container Won't Start
```bash
# Check logs
docker compose -f docker-compose.prod.yml logs mealsync-app

# Check container status
docker compose -f docker-compose.prod.yml ps

# Inspect container
docker inspect mealsync-app

# Check environment variables
docker exec mealsync-app env

# Test database connection
docker exec mealsync-app wget -qO- http://localhost:8081/actuator/health
```

### Out of Memory
```bash
# Check memory usage
docker stats mealsync-app

# Increase memory limit in docker-compose.yml
deploy:
  resources:
    limits:
      memory: 2G

# Restart container
docker compose -f docker-compose.prod.yml restart mealsync-app
```

### Out of Disk Space
```bash
# Check disk usage
df -h

# Check Docker disk usage
docker system df

# Clean up
docker system prune -af --volumes

# Remove old images
docker image prune -af --filter "until=48h"
```

### Slow Performance
```bash
# Check CPU usage
docker stats mealsync-app

# Check database connections
docker exec mealsync-app curl http://localhost:8081/actuator/metrics/hikari.connections.active

# Check JVM memory
docker exec mealsync-app curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Enable debug logging
docker exec mealsync-app curl -X POST http://localhost:8081/actuator/loggers/com.lamngo.mealsync -H "Content-Type: application/json" -d '{"configuredLevel":"DEBUG"}'
```

---

## 🔄 Scaling

### Scale Up
```bash
# Scale to 3 instances
docker compose -f docker-compose.prod.yml up -d --scale mealsync-app=3

# Verify
docker compose -f docker-compose.prod.yml ps
```

### Scale Down
```bash
# Scale to 1 instance
docker compose -f docker-compose.prod.yml up -d --scale mealsync-app=1
```

---

## 🔐 Security

### SSL/TLS Setup (Let's Encrypt)
```bash
# Install certbot
sudo apt-get install certbot

# Get certificate
sudo certbot certonly --standalone -d yourdomain.com

# Copy certificates
sudo cp /etc/letsencrypt/live/yourdomain.com/fullchain.pem nginx/ssl/
sudo cp /etc/letsencrypt/live/yourdomain.com/privkey.pem nginx/ssl/

# Restart nginx
docker compose -f docker-compose.prod.yml restart nginx

# Auto-renewal (add to crontab)
0 0 * * * certbot renew --quiet && docker compose -f ~/mealsync/docker-compose.prod.yml restart nginx
```

### Secrets Management
```bash
# Never commit secrets to git
echo ".env" >> .gitignore
echo "env.properties" >> .gitignore

# Use GitHub Secrets for CI/CD
# Settings > Secrets and variables > Actions > New repository secret

# Rotate secrets regularly
# Update in GitHub Secrets and redeploy
```

---

## 📦 Backup & Restore

### Database Backup
```bash
# Backup production database
docker exec mealsync-postgres-dev pg_dump -U postgres mealsync_dev > backup_$(date +%Y%m%d).sql

# Backup to S3
aws s3 cp backup_$(date +%Y%m%d).sql s3://mealsync-backups/

# Automated daily backup (crontab)
0 2 * * * docker exec mealsync-postgres-dev pg_dump -U postgres mealsync_dev | gzip > /backups/mealsync_$(date +\%Y\%m\%d).sql.gz
```

### Database Restore
```bash
# Restore from backup
docker exec -i mealsync-postgres-dev psql -U postgres mealsync_dev < backup_20240101.sql

# Restore from S3
aws s3 cp s3://mealsync-backups/backup_20240101.sql - | docker exec -i mealsync-postgres-dev psql -U postgres mealsync_dev
```

---

## 🧪 Testing

### Load Testing
```bash
# Install Apache Bench
sudo apt-get install apache2-utils

# Simple load test
ab -n 1000 -c 10 http://localhost/api/recipes

# With authentication
ab -n 1000 -c 10 -H "Authorization: Bearer YOUR_TOKEN" http://localhost/api/recipes

# POST request
ab -n 100 -c 10 -p data.json -T application/json http://localhost/api/recipes
```

### Integration Testing
```bash
# Run tests in Docker
docker compose -f docker-compose.dev.yml run --rm mealsync-app mvn test

# Run specific test
docker compose -f docker-compose.dev.yml run --rm mealsync-app mvn test -Dtest=RecipeServiceTest
```

---

## 🚨 Emergency Procedures

### Rollback Deployment
```bash
# Pull previous image version
docker pull username/mealsync-app:previous-tag

# Update docker-compose.yml to use previous tag
# Then restart
docker compose -f docker-compose.prod.yml up -d

# Or use git to revert
git revert HEAD
git push origin main
# CI/CD will automatically deploy previous version
```

### Emergency Shutdown
```bash
# Stop all services
docker compose -f docker-compose.prod.yml down

# Stop specific service
docker compose -f docker-compose.prod.yml stop mealsync-app

# Emergency restart
docker compose -f docker-compose.prod.yml restart mealsync-app
```

### Database Connection Issues
```bash
# Check database connectivity
docker exec mealsync-app ping -c 3 postgres

# Check database logs
docker compose -f docker-compose.prod.yml logs postgres

# Restart database
docker compose -f docker-compose.prod.yml restart postgres

# Check connection pool
docker exec mealsync-app curl http://localhost:8081/actuator/metrics/hikari.connections
```

---

## 📝 Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Port already in use | `sudo lsof -i :8081` then `kill -9 PID` |
| Permission denied | `sudo chown -R $USER:$USER .` |
| Image not found | `docker pull username/mealsync-app:latest` |
| Container unhealthy | Check logs: `docker logs mealsync-app` |
| Database connection failed | Verify credentials in `.env` file |
| Out of memory | Increase limits in `docker-compose.yml` |
| Slow startup | Increase `start_period` in healthcheck |

---

## 🔗 Useful Links

- **Actuator Endpoints**: http://localhost:8081/actuator
- **Health Check**: http://localhost:8081/actuator/health
- **Metrics**: http://localhost:8081/actuator/metrics
- **Prometheus**: http://localhost:9090 (if enabled)
- **Grafana**: http://localhost:3000 (if enabled)

---

## 📞 Support

- **Documentation**: `/docs/INFRASTRUCTURE.md`
- **Scaling Guide**: `/docs/SCALING_GUIDE.md`
- **GitHub Issues**: https://github.com/LamNgo1911/mealsync/issues
