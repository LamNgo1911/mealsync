# Scaling MealSync to 10,000+ Users

## Executive Summary

This guide provides a step-by-step approach to scale MealSync from a single-server deployment to a production-ready system capable of handling **10,000+ concurrent users** with **99.9% uptime**.

---

## Current vs Target Architecture

### Current (Single Server)
- **Capacity**: ~500 concurrent users
- **Infrastructure**: 1 EC2 instance + RDS
- **Deployment**: Manual with downtime
- **Cost**: ~$50/month

### Target (Production Scale)
- **Capacity**: 10,000+ concurrent users
- **Infrastructure**: Auto-scaling cluster + managed services
- **Deployment**: Automated zero-downtime
- **Cost**: ~$300-500/month (scales with usage)

---

## Phase 1: Foundation (Week 1-2)

### 1.1 Enable Health Checks & Graceful Shutdown ✅

**Status**: Already implemented in this setup

**What it does**:
- Prevents request loss during deployments
- Allows load balancers to detect unhealthy instances
- Enables zero-downtime deployments

### 1.2 Implement Zero-Downtime Deployment ✅

**Status**: Already implemented in `.github/workflows/deploy-zero-downtime.yml`

**What it does**:
- Rolling updates with health checks
- Automatic rollback on failure
- No service interruption during updates

### 1.3 Add Nginx Reverse Proxy ✅

**Status**: Already configured in `nginx/nginx.conf`

**Benefits**:
- SSL termination
- Rate limiting (prevents abuse)
- Load balancing (for future scaling)
- Static file caching

### 1.4 Database Connection Pooling

**Add to `application.properties`**:
```properties
# HikariCP connection pool (already included in Spring Boot)
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

**Why**: Reusing database connections is 100x faster than creating new ones.

---

## Phase 2: Horizontal Scaling (Week 3-4)

### 2.1 Multi-Instance Deployment

**Option A: Docker Compose (Simple)**

Update `docker-compose.prod.yml`:
```yaml
services:
  mealsync-app:
    image: ${DOCKER_HUB_USERNAME}/mealsync-app:latest
    deploy:
      replicas: 3  # Run 3 instances
      resources:
        limits:
          cpus: '1'
          memory: 1G
```

**Deploy**:
```bash
docker compose -f docker-compose.prod.yml up -d --scale mealsync-app=3
```

**Capacity**: ~1,500 concurrent users (3 instances × 500)

---

**Option B: AWS ECS Fargate (Recommended)**

Create `ecs-task-definition.json`:
```json
{
  "family": "mealsync-app",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "containerDefinitions": [
    {
      "name": "mealsync-app",
      "image": "username/mealsync-app:latest",
      "portMappings": [
        {
          "containerPort": 8081,
          "protocol": "tcp"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      },
      "environment": [
        {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://..."}
      ]
    }
  ]
}
```

**Setup**:
```bash
# Create ECS cluster
aws ecs create-cluster --cluster-name mealsync-prod

# Register task definition
aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json

# Create service with auto-scaling
aws ecs create-service \
  --cluster mealsync-prod \
  --service-name mealsync-app \
  --task-definition mealsync-app \
  --desired-count 3 \
  --launch-type FARGATE \
  --load-balancers targetGroupArn=arn:aws:elasticloadbalancing:...
```

**Benefits**:
- Auto-scaling based on CPU/memory
- No server management
- Built-in load balancer
- Pay only for what you use

**Capacity**: Unlimited (scales automatically)

---

### 2.2 Application Load Balancer (ALB)

**Why**: Distribute traffic across multiple instances

**Setup (AWS)**:
```bash
# Create ALB
aws elbv2 create-load-balancer \
  --name mealsync-alb \
  --subnets subnet-12345 subnet-67890 \
  --security-groups sg-12345

# Create target group
aws elbv2 create-target-group \
  --name mealsync-targets \
  --protocol HTTP \
  --port 8081 \
  --vpc-id vpc-12345 \
  --health-check-path /actuator/health \
  --health-check-interval-seconds 30

# Configure auto-scaling
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/mealsync-prod/mealsync-app \
  --min-capacity 2 \
  --max-capacity 10

# Scale based on CPU
aws application-autoscaling put-scaling-policy \
  --policy-name cpu-scaling \
  --service-namespace ecs \
  --scalable-dimension ecs:service:DesiredCount \
  --resource-id service/mealsync-prod/mealsync-app \
  --policy-type TargetTrackingScaling \
  --target-tracking-scaling-policy-configuration \
    '{"TargetValue": 70.0, "PredefinedMetricSpecification": {"PredefinedMetricType": "ECSServiceAverageCPUUtilization"}}'
```

**Result**: Automatically scales from 2 to 10 instances based on load

---

## Phase 3: Database Optimization (Week 5-6)

### 3.1 Database Indexing

**Identify slow queries**:
```sql
-- Enable query logging in PostgreSQL
ALTER DATABASE mealsync_prod SET log_min_duration_statement = 1000; -- Log queries > 1s

-- Find slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

**Add indexes**:
```sql
-- Example: Index on user_id for recipes table
CREATE INDEX idx_recipes_user_id ON recipes(user_id);

-- Composite index for common queries
CREATE INDEX idx_recipes_user_created ON recipes(user_id, created_at DESC);

-- Full-text search index
CREATE INDEX idx_recipes_name_search ON recipes USING gin(to_tsvector('english', name));
```

**Impact**: 10-100x faster queries

---

### 3.2 Read Replicas

**Why**: Offload read traffic from primary database

**Setup (AWS RDS)**:
```bash
# Create read replica
aws rds create-db-instance-read-replica \
  --db-instance-identifier mealsync-db-replica \
  --source-db-instance-identifier mealsync-db-primary \
  --db-instance-class db.t3.medium
```

**Spring Boot configuration**:
```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource primaryDataSource() {
        // Write operations
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://primary.rds.amazonaws.com:5432/mealsync")
            .build();
    }
    
    @Bean
    public DataSource replicaDataSource() {
        // Read operations
        return DataSourceBuilder.create()
            .url("jdbc:postgresql://replica.rds.amazonaws.com:5432/mealsync")
            .build();
    }
}
```

**Usage**:
```java
@Transactional(readOnly = true)  // Uses read replica
public List<Recipe> getAllRecipes() {
    return recipeRepository.findAll();
}

@Transactional  // Uses primary database
public Recipe createRecipe(Recipe recipe) {
    return recipeRepository.save(recipe);
}
```

**Capacity**: 2-3x more read throughput

---

### 3.3 Connection Pooling Optimization

**Current**: Default HikariCP settings

**Optimized** (`application.properties`):
```properties
# Formula: connections = ((core_count * 2) + effective_spindle_count)
# For db.t3.medium (2 vCPU): (2 * 2) + 1 = 5 connections per instance
# With 5 app instances: 5 * 5 = 25 total connections

spring.datasource.hikari.maximum-pool-size=5
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.leak-detection-threshold=60000
```

**Why**: Too many connections waste memory; too few cause bottlenecks

---

## Phase 4: Caching Layer (Week 7-8)

### 4.1 Add Redis Cache

**Update `docker-compose.prod.yml`**:
```yaml
services:
  redis:
    image: redis:7-alpine
    container_name: mealsync-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes --maxmemory 512mb --maxmemory-policy allkeys-lru
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 3
    restart: unless-stopped
    networks:
      - mealsync-network

volumes:
  redis_data:
```

**Add dependency** (`pom.xml`):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**Configure** (`application.properties`):
```properties
spring.cache.type=redis
spring.redis.host=redis
spring.redis.port=6379
spring.cache.redis.time-to-live=600000  # 10 minutes
```

**Use in code**:
```java
@Service
public class RecipeService {
    
    @Cacheable(value = "recipes", key = "#id")
    public Recipe getRecipeById(Long id) {
        // This will be cached for 10 minutes
        return recipeRepository.findById(id).orElseThrow();
    }
    
    @CacheEvict(value = "recipes", key = "#recipe.id")
    public Recipe updateRecipe(Recipe recipe) {
        // This will invalidate the cache
        return recipeRepository.save(recipe);
    }
}
```

**Impact**: 
- 50-100x faster for cached data
- Reduces database load by 60-80%

---

### 4.2 HTTP Caching Headers

**Add to controllers**:
```java
@GetMapping("/api/recipes/{id}")
public ResponseEntity<Recipe> getRecipe(@PathVariable Long id) {
    Recipe recipe = recipeService.getRecipeById(id);
    
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
        .eTag(String.valueOf(recipe.getUpdatedAt().hashCode()))
        .body(recipe);
}
```

**Impact**: Reduces server requests by 30-40%

---

## Phase 5: CDN & Static Assets (Week 9-10)

### 5.1 CloudFront CDN

**Setup**:
```bash
# Create CloudFront distribution
aws cloudfront create-distribution \
  --origin-domain-name mealsync-images.s3.amazonaws.com \
  --default-root-object index.html
```

**Update S3 URLs in code**:
```java
// Before
String imageUrl = "https://mealsync-images.s3.amazonaws.com/recipe123.jpg";

// After
String imageUrl = "https://d1234567890.cloudfront.net/recipe123.jpg";
```

**Benefits**:
- 10x faster image loading (edge locations)
- Reduced S3 costs (CloudFront is cheaper)
- Better user experience globally

---

### 5.2 Image Optimization

**Add image processing**:
```java
@Service
public class ImageService {
    
    public String uploadImage(MultipartFile file) {
        // Resize and compress before upload
        BufferedImage image = ImageIO.read(file.getInputStream());
        BufferedImage resized = Scalr.resize(image, 800); // Max width 800px
        
        // Compress to WebP format (smaller than JPEG)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "webp", baos);
        
        // Upload to S3
        s3Client.putObject(bucket, key, baos.toByteArray());
    }
}
```

**Impact**: 70% smaller images = faster loading

---

## Phase 6: Monitoring & Observability (Week 11-12)

### 6.1 Prometheus + Grafana

**Add to `docker-compose.prod.yml`**:
```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    container_name: mealsync-prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - mealsync-network

  grafana:
    image: grafana/grafana:latest
    container_name: mealsync-grafana
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana
    networks:
      - mealsync-network

volumes:
  prometheus_data:
  grafana_data:
```

**Prometheus config** (`prometheus/prometheus.yml`):
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'mealsync-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['mealsync-app:8081']
```

**Access**:
- Prometheus: http://your-server:9090
- Grafana: http://your-server:3000

**Key metrics to monitor**:
- Request rate (requests/second)
- Response time (p50, p95, p99)
- Error rate (%)
- CPU usage (%)
- Memory usage (MB)
- Database connections (active/idle)
- Cache hit rate (%)

---

### 6.2 Alerting

**Add to Prometheus** (`prometheus/alerts.yml`):
```yaml
groups:
  - name: mealsync_alerts
    interval: 30s
    rules:
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors/sec"
      
      - alert: HighResponseTime
        expr: histogram_quantile(0.95, http_server_requests_seconds_bucket) > 1
        for: 5m
        annotations:
          summary: "High response time"
          description: "95th percentile response time is {{ $value }}s"
      
      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9
        for: 5m
        annotations:
          summary: "High memory usage"
          description: "Memory usage is {{ $value }}%"
```

---

## Phase 7: Cost Optimization

### 7.1 Resource Right-Sizing

**Current**: t3.medium (2 vCPU, 4GB RAM) = $30/month

**Optimized**:
- Use **t3.small** for low traffic periods
- Use **t3.large** for peak hours
- Enable **auto-scaling** to adjust automatically

**Savings**: 40-60% on compute costs

---

### 7.2 Reserved Instances

**Current**: On-demand pricing

**Optimized**: 1-year reserved instances

**Savings**: 30-40% discount

---

### 7.3 S3 Lifecycle Policies

**Move old images to cheaper storage**:
```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket mealsync-images \
  --lifecycle-configuration '{
    "Rules": [{
      "Id": "archive-old-images",
      "Status": "Enabled",
      "Transitions": [{
        "Days": 90,
        "StorageClass": "GLACIER"
      }]
    }]
  }'
```

**Savings**: 80% on storage costs for old data

---

## Capacity Planning

### Single Instance Capacity

**Assumptions**:
- Average request: 50ms
- Concurrent requests per instance: 100
- Requests per second: 100 / 0.05 = 2,000 req/s
- Concurrent users: ~500 (assuming 4 requests per user session)

### Scaling Table

| Users | Instances | Database | Redis | Monthly Cost |
|-------|-----------|----------|-------|--------------|
| 500 | 1 | db.t3.small | 512MB | $50 |
| 2,000 | 3 | db.t3.medium | 1GB | $150 |
| 5,000 | 5 | db.t3.large + 1 replica | 2GB | $300 |
| 10,000 | 10 | db.r5.xlarge + 2 replicas | 4GB | $600 |
| 50,000 | 30 | db.r5.2xlarge + 3 replicas | 8GB | $2,000 |

---

## Load Testing

### Before Scaling
```bash
# Install Apache Bench
sudo apt-get install apache2-utils

# Test current capacity
ab -n 10000 -c 100 http://your-server/api/recipes

# Results:
# Requests per second: 500 req/s
# Time per request: 200ms (mean)
# Failed requests: 0
```

### After Scaling (Target)
```bash
ab -n 100000 -c 1000 http://your-server/api/recipes

# Expected results:
# Requests per second: 5,000 req/s
# Time per request: 200ms (mean)
# Failed requests: 0
```

---

## Rollout Plan

### Week 1-2: Foundation
- ✅ Enable health checks
- ✅ Implement zero-downtime deployment
- ✅ Add nginx reverse proxy
- [ ] Optimize database connection pool

### Week 3-4: Horizontal Scaling
- [ ] Deploy 3 instances with load balancer
- [ ] Set up auto-scaling
- [ ] Load test with 2,000 concurrent users

### Week 5-6: Database Optimization
- [ ] Add database indexes
- [ ] Create read replica
- [ ] Optimize slow queries

### Week 7-8: Caching
- [ ] Deploy Redis
- [ ] Implement application caching
- [ ] Add HTTP caching headers

### Week 9-10: CDN & Assets
- [ ] Set up CloudFront
- [ ] Optimize images
- [ ] Implement lazy loading

### Week 11-12: Monitoring
- [ ] Deploy Prometheus + Grafana
- [ ] Set up alerts
- [ ] Create dashboards

---

## Success Metrics

| Metric | Current | Target | Status |
|--------|---------|--------|--------|
| Concurrent users | 500 | 10,000 | 🔄 In Progress |
| Response time (p95) | 500ms | <200ms | 🔄 In Progress |
| Uptime | 95% | 99.9% | 🔄 In Progress |
| Error rate | 5% | <0.1% | 🔄 In Progress |
| Deployment time | 5 min | <2 min | ✅ Done |
| Deployment downtime | 30s | 0s | ✅ Done |

---

## Conclusion

Scaling to 10,000+ users is a journey, not a destination. Start with the foundation (health checks, zero-downtime deployment), then progressively add horizontal scaling, caching, and monitoring.

**Key Takeaways**:
1. **Horizontal scaling** is easier than vertical scaling
2. **Caching** provides the biggest performance wins
3. **Monitoring** is essential to know when to scale
4. **Auto-scaling** reduces costs and improves reliability
5. **Zero-downtime deployment** is non-negotiable for production

**Next Steps**: Start with Phase 1 (Foundation) and work your way up. Each phase builds on the previous one.
