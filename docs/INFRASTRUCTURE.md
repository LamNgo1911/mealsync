# Infrastructure Guide for MealSync

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Environments](#environments)
4. [Docker Best Practices](#docker-best-practices)
5. [Zero-Downtime Deployment](#zero-downtime-deployment)
6. [Scaling for 10,000+ Users](#scaling-for-10000-users)
7. [Monitoring & Health Checks](#monitoring--health-checks)
8. [Deployment Workflows](#deployment-workflows)
9. [Troubleshooting](#troubleshooting)

---

## Overview

This document explains the infrastructure setup for MealSync, designed to handle production workloads with **zero-downtime deployments** and support for thousands of concurrent users.

### Key Features
- ✅ **Multi-stage Docker builds** for optimized image size
- ✅ **Health checks** for container orchestration
- ✅ **Graceful shutdown** to prevent request loss
- ✅ **Blue-green deployment** strategy
- ✅ **Nginx reverse proxy** with load balancing
- ✅ **Separate environments** (dev, staging, production)
- ✅ **Automated CI/CD** with GitHub Actions
- ✅ **Resource limits** and JVM optimization

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Internet                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
              ┌─────────────────┐
              │  Nginx (Port 80) │
              │  Load Balancer   │
              └────────┬─────────┘
                       │
         ┌─────────────┴─────────────┐
         ▼                           ▼
┌──────────────────┐        ┌──────────────────┐
│  MealSync App 1  │        │  MealSync App 2  │
│  (Port 8081)     │        │  (Port 8081)     │
│  Spring Boot     │        │  Spring Boot     │
└────────┬─────────┘        └────────┬─────────┘
         │                           │
         └─────────────┬─────────────┘
                       ▼
              ┌─────────────────┐
              │  PostgreSQL DB   │
              │  (RDS/Self-host) │
              └──────────────────┘
```

### Components

1. **Nginx**: Reverse proxy, SSL termination, load balancing, rate limiting
2. **Spring Boot App**: Java application running in Docker containers
3. **PostgreSQL**: Database (AWS RDS in production, local in dev)
4. **Docker**: Container runtime
5. **GitHub Actions**: CI/CD pipeline

---

## Environments

### 1. Development (`docker-compose.dev.yml`)

**Purpose**: Local development with hot-reload

**Features**:
- Local PostgreSQL database
- DDL auto: `create-drop` (recreates schema on restart)
- Spring DevTools enabled
- Port 5431 for database, 8081 for app

**Usage**:
```bash
docker compose -f docker-compose.dev.yml up --build
```

### 2. Staging (`docker-compose.staging.yml`)

**Purpose**: Pre-production testing environment

**Features**:
- Local PostgreSQL with persistent volume
- DDL auto: `update` (preserves data)
- Production-like configuration
- Resource limits applied

**Usage**:
```bash
docker compose -f docker-compose.staging.yml up -d
```

### 3. Production (`docker-compose.prod.yml`)

**Purpose**: Live production environment

**Features**:
- AWS RDS PostgreSQL
- Nginx reverse proxy
- Health checks enabled
- Resource limits (2 CPU, 2GB RAM)
- Automatic restart on failure
- Graceful shutdown (30s timeout)

**Usage**:
```bash
docker compose -f docker-compose.prod.yml up -d
```

---

## Docker Best Practices

### Multi-Stage Build

Our `Dockerfile` uses two stages:

1. **BUILDER**: Compiles the Java application with Maven
   - Uses layer caching for dependencies
   - Runs `mvn dependency:go-offline` separately for faster rebuilds

2. **RUNNER**: Minimal runtime image
   - Uses JRE instead of JDK (smaller size)
   - Runs as non-root user for security
   - Includes health check

### Image Optimization

**Before**: ~800MB  
**After**: ~350MB

**Techniques**:
- Multi-stage build (removes build tools)
- Alpine Linux base image
- Dependency layer caching
- `.dockerignore` to exclude unnecessary files

### Security

- ✅ Non-root user (`spring:spring`)
- ✅ Read-only volumes where possible
- ✅ No secrets in image (use env vars)
- ✅ Regular base image updates

---

## Zero-Downtime Deployment

### How It Works

The deployment uses a **rolling update** strategy:

1. **Pull new image** from Docker Hub
2. **Scale up** to 2 containers (old + new)
3. **Health check** the new container (max 120s)
4. **Scale down** to 1 container (removes old)
5. **Graceful shutdown** of old container (30s)

### Graceful Shutdown

Spring Boot configuration:
```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

This ensures:
- Existing requests complete before shutdown
- No new requests accepted during shutdown
- Database connections closed properly

### Health Checks

**Docker health check**:
```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health
```

**Endpoints**:
- `/actuator/health` - Overall health status
- `/actuator/health/liveness` - Is the app running?
- `/actuator/health/readiness` - Is the app ready to serve traffic?

### Rollback Strategy

If health checks fail:
1. Deployment script automatically rolls back
2. Old container continues serving traffic
3. New container is removed
4. Logs are captured for debugging

---

## Scaling for 10,000+ Users

### Current Setup (Single Server)

**Capacity**: ~500-1000 concurrent users per instance

**Bottlenecks**:
- Single EC2 instance
- Single database connection
- No caching layer

### Scaling to 10,000+ Users

#### 1. Horizontal Scaling (Multiple Instances)

**Option A: Docker Swarm** (Easiest)
```bash
docker swarm init
docker stack deploy -c docker-compose.prod.yml mealsync

# Scale to 5 replicas
docker service scale mealsync_mealsync-app=5
```

**Option B: Kubernetes** (Production-grade)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mealsync-app
spec:
  replicas: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
```

**Option C: AWS ECS/Fargate** (Managed)
- Auto-scaling based on CPU/memory
- Load balancer included
- No server management

#### 2. Database Optimization

**Current**: Single RDS instance

**Improvements**:
- **Read replicas** for read-heavy operations
- **Connection pooling** (HikariCP - already included in Spring Boot)
- **Database indexing** on frequently queried columns
- **Query optimization** (use EXPLAIN ANALYZE)

**Configuration**:
```properties
# application.properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

#### 3. Caching Layer

Add **Redis** for caching:

```yaml
# docker-compose.prod.yml
redis:
  image: redis:7-alpine
  ports:
    - "6379:6379"
  volumes:
    - redis_data:/data
```

**Use cases**:
- Session storage
- API response caching
- Rate limiting counters

#### 4. CDN for Static Assets

Use **CloudFront** or **Cloudflare** for:
- Image delivery (S3 + CDN)
- Static file caching
- DDoS protection

#### 5. Load Balancer

**Current**: Nginx on same server

**Production**: AWS Application Load Balancer (ALB)
- Health checks
- SSL termination
- Auto-scaling integration
- Multi-AZ deployment

#### 6. Monitoring & Auto-Scaling

**Metrics to monitor**:
- CPU usage > 70% → scale up
- Memory usage > 80% → scale up
- Request latency > 500ms → investigate
- Error rate > 1% → alert

**Tools**:
- **Prometheus** + **Grafana** for metrics
- **ELK Stack** for logs
- **AWS CloudWatch** for infrastructure

---

## Monitoring & Health Checks

### Spring Boot Actuator Endpoints

| Endpoint | Purpose | Access |
|----------|---------|--------|
| `/actuator/health` | Overall health | Public |
| `/actuator/health/liveness` | Liveness probe | Internal |
| `/actuator/health/readiness` | Readiness probe | Internal |
| `/actuator/metrics` | Application metrics | Internal |
| `/actuator/info` | App information | Public |
| `/actuator/prometheus` | Prometheus metrics | Internal |

### Custom Health Indicators

Create custom health checks for:
- Database connectivity
- External API availability (Gemini, AWS S3)
- Disk space
- Memory usage

Example:
```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check database connection
        if (isDatabaseUp()) {
            return Health.up().build();
        }
        return Health.down().withDetail("Error", "Cannot connect to database").build();
    }
}
```

### Logging

**Current**: Console logs

**Production recommendations**:
- Centralized logging (ELK, CloudWatch Logs)
- Structured logging (JSON format)
- Log levels: ERROR for production, DEBUG for dev
- Log rotation to prevent disk fill

---

## Deployment Workflows

### Workflow 1: Manual Deployment (Current)

```bash
# 1. Build and push image
docker build -t username/mealsync-app:latest .
docker push username/mealsync-app:latest

# 2. SSH to server
ssh user@server

# 3. Pull and restart
cd ~/mealsync
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### Workflow 2: Automated CI/CD (Recommended)

**Trigger**: Push to `main` branch

**Steps**:
1. Run tests
2. Build Docker image
3. Push to Docker Hub
4. Deploy to EC2 with zero downtime
5. Verify health checks
6. Rollback if failed

**File**: `.github/workflows/deploy-zero-downtime.yml`

### Workflow 3: Blue-Green Deployment

**Setup**:
```yaml
# docker-compose.blue-green.yml
services:
  mealsync-app-blue:
    image: ${DOCKER_HUB_USERNAME}/mealsync-app:blue
    # ... config ...

  mealsync-app-green:
    image: ${DOCKER_HUB_USERNAME}/mealsync-app:green
    # ... config ...

  nginx:
    # Switch traffic between blue and green
```

**Process**:
1. Deploy to "green" environment
2. Test green environment
3. Switch nginx to route traffic to green
4. Keep blue as backup
5. Next deployment goes to blue

---

## Troubleshooting

### Issue 1: Container Won't Start

**Symptoms**: Container exits immediately

**Debug**:
```bash
# Check logs
docker compose -f docker-compose.prod.yml logs mealsync-app

# Check container status
docker compose -f docker-compose.prod.yml ps

# Inspect container
docker inspect mealsync-app
```

**Common causes**:
- Missing environment variables
- Database connection failure
- Port already in use
- Insufficient memory

### Issue 2: Health Check Failing

**Symptoms**: Container marked as unhealthy

**Debug**:
```bash
# Check health status
docker inspect --format='{{json .State.Health}}' mealsync-app | jq

# Test health endpoint manually
docker exec mealsync-app wget -qO- http://localhost:8081/actuator/health
```

**Common causes**:
- Application not fully started (increase `start_period`)
- Database not accessible
- Actuator endpoints not exposed

### Issue 3: Out of Disk Space

**Symptoms**: Deployment fails with "no space left on device"

**Solution**:
```bash
# Remove unused images
docker image prune -af

# Remove unused volumes
docker volume prune -f

# Remove unused containers
docker container prune -f

# Check disk usage
df -h
docker system df
```

### Issue 4: Memory Issues

**Symptoms**: Container killed by OOM (Out of Memory)

**Solution**:
```yaml
# Increase memory limit
deploy:
  resources:
    limits:
      memory: 4G
```

**JVM tuning**:
```bash
# In Dockerfile ENTRYPOINT
-XX:MaxRAMPercentage=75.0  # Use 75% of container memory
-XX:+UseG1GC               # Use G1 garbage collector
```

### Issue 5: Slow Deployments

**Symptoms**: Deployment takes >5 minutes

**Optimizations**:
1. Use Docker layer caching
2. Use `mvn dependency:go-offline` in Dockerfile
3. Use Docker BuildKit
4. Reduce image size

---

## Quick Reference

### Common Commands

```bash
# Development
docker compose -f docker-compose.dev.yml up --build

# Staging
docker compose -f docker-compose.staging.yml up -d

# Production
docker compose -f docker-compose.prod.yml up -d

# View logs
docker compose -f docker-compose.prod.yml logs -f mealsync-app

# Restart service
docker compose -f docker-compose.prod.yml restart mealsync-app

# Scale service
docker compose -f docker-compose.prod.yml up -d --scale mealsync-app=3

# Health check
curl http://localhost/actuator/health

# Cleanup
docker system prune -af --volumes
```

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `RDS_ENDPOINT` | Database host | `db.us-east-1.rds.amazonaws.com` |
| `RDS_PORT` | Database port | `5432` |
| `RDS_DB_NAME` | Database name | `mealsync_prod` |
| `JWT_SECRET` | JWT signing key | `your-secret-key-here` |
| `AWS_S3_BUCKET_NAME` | S3 bucket for images | `mealsync-images` |

---

## Next Steps

### Immediate (Week 1)
- [ ] Switch to zero-downtime deployment workflow
- [ ] Set up monitoring (Prometheus + Grafana)
- [ ] Configure SSL certificates (Let's Encrypt)
- [ ] Enable nginx rate limiting

### Short-term (Month 1)
- [ ] Add Redis caching layer
- [ ] Implement database read replicas
- [ ] Set up centralized logging
- [ ] Create staging environment

### Long-term (Quarter 1)
- [ ] Migrate to Kubernetes or ECS
- [ ] Implement auto-scaling
- [ ] Add CDN for static assets
- [ ] Set up disaster recovery plan

---

## Resources

- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Nginx Load Balancing](https://docs.nginx.com/nginx/admin-guide/load-balancer/http-load-balancer/)
- [Zero-Downtime Deployments](https://martinfowler.com/bliki/BlueGreenDeployment.html)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)

---

**Last Updated**: 2025-10-12  
**Maintained By**: MealSync DevOps Team
