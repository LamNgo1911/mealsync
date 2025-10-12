# ---------- Stage 1: Build ----------
FROM maven:3.9.6-eclipse-temurin-22-alpine AS BUILDER

WORKDIR /app

# Copy only pom.xml first for better layer caching
COPY pom.xml .

# Download dependencies (cached if pom.xml hasn't changed)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:22-jre-alpine AS RUNNER

# Add non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy JAR from builder stage
COPY --from=BUILDER /app/target/mealsync-0.0.1-SNAPSHOT.jar /app/mealsync.jar

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

EXPOSE 8081

# JVM optimization for containers
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "mealsync.jar"]
