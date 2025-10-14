# MealSync Documentation

Welcome to the MealSync infrastructure and deployment documentation.

## 📚 Documentation Index

### 🏗️ [Infrastructure Guide](./INFRASTRUCTURE.md)
**Complete infrastructure overview and best practices**

Learn about:
- Architecture design
- Docker best practices
- Environment setup (dev/staging/prod)
- Zero-downtime deployment strategies
- Health checks and monitoring
- Troubleshooting common issues

**Start here if**: You're new to the project or need to understand the overall architecture.

---

### 📈 [Scaling Guide](./SCALING_GUIDE.md)
**Step-by-step guide to scale from 500 to 10,000+ users**

Covers:
- Horizontal scaling strategies
- Database optimization (indexing, read replicas)
- Caching with Redis
- CDN setup for static assets
- Load balancing and auto-scaling
- Cost optimization
- Capacity planning

**Start here if**: You need to handle more traffic or improve performance.

---

### 🚀 [Deployment Cheat Sheet](./DEPLOYMENT_CHEATSHEET.md)
**Quick reference for common deployment tasks**

Includes:
- Quick start commands
- Health check endpoints
- Docker commands
- Troubleshooting steps
- Emergency procedures
- Backup and restore

**Start here if**: You need quick answers or copy-paste commands.

---

## 🎯 Quick Navigation

### For Developers
1. **Setting up local environment**: [Infrastructure Guide - Environments](./INFRASTRUCTURE.md#environments)
2. **Running tests**: [Deployment Cheat Sheet - Testing](./DEPLOYMENT_CHEATSHEET.md#-testing)
3. **Debugging issues**: [Deployment Cheat Sheet - Troubleshooting](./DEPLOYMENT_CHEATSHEET.md#-troubleshooting)

### For DevOps
1. **Production deployment**: [Infrastructure Guide - Deployment Workflows](./INFRASTRUCTURE.md#deployment-workflows)
2. **Zero-downtime updates**: [Infrastructure Guide - Zero-Downtime Deployment](./INFRASTRUCTURE.md#zero-downtime-deployment)
3. **Monitoring setup**: [Scaling Guide - Monitoring & Observability](./SCALING_GUIDE.md#phase-6-monitoring--observability-week-11-12)

### For Product/Business
1. **Scaling roadmap**: [Scaling Guide - Rollout Plan](./SCALING_GUIDE.md#rollout-plan)
2. **Cost estimates**: [Scaling Guide - Capacity Planning](./SCALING_GUIDE.md#capacity-planning)
3. **Performance metrics**: [Scaling Guide - Success Metrics](./SCALING_GUIDE.md#success-metrics)

---

## 🚦 Getting Started

### 1. Local Development (5 minutes)

```bash
# Clone the repository
git clone https://github.com/LamNgo1911/mealsync.git
cd mealsync

# Start development environment
docker compose -f docker-compose.dev.yml up --build

# Access the application
# API: http://localhost:8081
# Health: http://localhost:8081/actuator/health
```

### 2. Deploy to Staging (10 minutes)

```bash
# Build and push Docker image
docker build -t username/mealsync-app:latest .
docker push username/mealsync-app:latest

# Deploy to staging server
ssh user@staging-server
cd ~/mealsync
docker compose -f docker-compose.staging.yml pull
docker compose -f docker-compose.staging.yml up -d
```

### 3. Deploy to Production (Automated)

```bash
# Push to main branch - CI/CD handles the rest
git push origin main

# GitHub Actions will:
# 1. Run tests
# 2. Build Docker image
# 3. Deploy with zero downtime
# 4. Verify health checks
# 5. Rollback if failed
```

---

## 🔑 Key Concepts

### Zero-Downtime Deployment

**Problem**: Traditional deployments cause 30-60 seconds of downtime, losing active user sessions.

**Solution**: Rolling updates with health checks
1. Start new container alongside old one
2. Wait for new container to be healthy
3. Route traffic to new container
4. Gracefully shutdown old container

**Result**: No downtime, no lost requests

---

### Health Checks

**Why**: Ensure containers are ready before receiving traffic

**Endpoints**:
- `/actuator/health` - Overall health
- `/actuator/health/liveness` - Is app alive?
- `/actuator/health/readiness` - Is app ready?

**Usage**: Load balancers use these to route traffic only to healthy instances

---

### Graceful Shutdown

**Why**: Prevent request loss during shutdown

**How**: 
1. Stop accepting new requests
2. Complete existing requests (up to 30s)
3. Close database connections
4. Exit

**Configuration**:
```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

---

## 📊 Current Infrastructure

```
Environment: Production
Deployment: AWS EC2 + RDS
Containers: 1 instance (can scale to 10+)
Database: PostgreSQL on RDS
Load Balancer: Nginx
CI/CD: GitHub Actions
Monitoring: Spring Boot Actuator
```

---

## 🎯 Scaling Roadmap

### ✅ Phase 1: Foundation (Completed)
- Multi-stage Docker build
- Health checks
- Zero-downtime deployment
- Nginx reverse proxy

### 🔄 Phase 2: Horizontal Scaling (Next)
- Deploy 3+ instances
- Application Load Balancer
- Auto-scaling

### 📅 Phase 3: Database Optimization (Planned)
- Database indexing
- Read replicas
- Connection pooling

### 📅 Phase 4: Caching (Planned)
- Redis cache
- HTTP caching
- CDN for images

### 📅 Phase 5: Monitoring (Planned)
- Prometheus + Grafana
- Alerting
- Log aggregation

---

## 🔧 Technology Stack

### Application
- **Language**: Java 22
- **Framework**: Spring Boot 3.4.3
- **Database**: PostgreSQL 15
- **ORM**: JPA/Hibernate
- **Build Tool**: Maven

### Infrastructure
- **Containerization**: Docker
- **Orchestration**: Docker Compose (current), Kubernetes (future)
- **Reverse Proxy**: Nginx
- **CI/CD**: GitHub Actions
- **Cloud**: AWS (EC2, RDS, S3)

### Monitoring
- **Health Checks**: Spring Boot Actuator
- **Metrics**: Prometheus (planned)
- **Dashboards**: Grafana (planned)
- **Logging**: Console (current), ELK Stack (planned)

---

## 📈 Performance Targets

| Metric | Current | Target |
|--------|---------|--------|
| Concurrent Users | 500 | 10,000+ |
| Response Time (p95) | 500ms | <200ms |
| Uptime | 95% | 99.9% |
| Error Rate | <5% | <0.1% |
| Deployment Time | 2 min | <2 min |
| Deployment Downtime | 0s | 0s |

---

## 🆘 Common Questions

### Q: How do I deploy without downtime?

**A**: Use the zero-downtime deployment workflow:
```bash
# Automated (recommended)
git push origin main

# Manual
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d --no-deps mealsync-app
```

See: [Infrastructure Guide - Zero-Downtime Deployment](./INFRASTRUCTURE.md#zero-downtime-deployment)

---

### Q: How do I scale to handle more users?

**A**: Follow the scaling guide phases:
1. Enable horizontal scaling (multiple instances)
2. Add database read replicas
3. Implement Redis caching
4. Set up CDN for images

See: [Scaling Guide](./SCALING_GUIDE.md)

---

### Q: How do I monitor the application?

**A**: Use Spring Boot Actuator endpoints:
```bash
# Health check
curl http://localhost:8081/actuator/health

# Metrics
curl http://localhost:8081/actuator/metrics

# Prometheus format
curl http://localhost:8081/actuator/prometheus
```

See: [Infrastructure Guide - Monitoring](./INFRASTRUCTURE.md#monitoring--health-checks)

---

### Q: What if deployment fails?

**A**: The CI/CD pipeline automatically rolls back:
1. Health checks fail on new container
2. Old container continues serving traffic
3. New container is removed
4. Logs are captured for debugging

Manual rollback:
```bash
# Revert to previous version
git revert HEAD
git push origin main
```

See: [Deployment Cheat Sheet - Emergency Procedures](./DEPLOYMENT_CHEATSHEET.md#-emergency-procedures)

---

### Q: How much does it cost to run at scale?

**A**: Cost estimates by user count:

| Users | Monthly Cost |
|-------|--------------|
| 500 | $50 |
| 2,000 | $150 |
| 5,000 | $300 |
| 10,000 | $600 |

See: [Scaling Guide - Capacity Planning](./SCALING_GUIDE.md#capacity-planning)

---

## 🔗 External Resources

### Docker
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [Multi-stage Builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker Compose](https://docs.docker.com/compose/)

### Spring Boot
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Graceful Shutdown](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.graceful-shutdown)
- [Health Indicators](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.endpoints.health)

### Deployment
- [Blue-Green Deployment](https://martinfowler.com/bliki/BlueGreenDeployment.html)
- [Rolling Updates](https://kubernetes.io/docs/tutorials/kubernetes-basics/update/update-intro/)
- [Zero-Downtime Deployments](https://www.nginx.com/blog/nginx-plus-zero-downtime-deployment/)

### AWS
- [AWS ECS Best Practices](https://docs.aws.amazon.com/AmazonECS/latest/bestpracticesguide/intro.html)
- [RDS Performance](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_BestPractices.html)
- [CloudFront CDN](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/Introduction.html)

---

## 🤝 Contributing

Found an issue or have a suggestion? 

1. Check existing documentation
2. Search [GitHub Issues](https://github.com/LamNgo1911/mealsync/issues)
3. Create a new issue with details
4. Submit a pull request with improvements

---

## 📝 Changelog

### 2025-10-12
- ✅ Added comprehensive infrastructure documentation
- ✅ Created scaling guide for 10,000+ users
- ✅ Implemented zero-downtime deployment
- ✅ Added Nginx reverse proxy configuration
- ✅ Optimized Dockerfile with multi-stage build
- ✅ Added health checks and graceful shutdown
- ✅ Created deployment cheat sheet

---

## 📧 Support

- **Documentation Issues**: Create a GitHub issue
- **Infrastructure Questions**: See [Infrastructure Guide](./INFRASTRUCTURE.md)
- **Scaling Questions**: See [Scaling Guide](./SCALING_GUIDE.md)
- **Quick Help**: See [Deployment Cheat Sheet](./DEPLOYMENT_CHEATSHEET.md)

---

**Last Updated**: 2025-10-12  
**Maintained By**: MealSync DevOps Team
