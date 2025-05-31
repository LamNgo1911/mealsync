# ---------- Stage 1: Build ----------
FROM eclipse-temurin:21-jdk-alpine as builder

# Set working directory inside container
WORKDIR /app

# Copy build files first for dependency caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (cached unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy the rest of the project
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:21-jre-alpine

# Create a non-root user for security (optional but recommended)
RUN addgroup --system app && adduser --system --ingroup app appuser

# Set working directory
WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:app /app

# Run the app as a non-root user
USER appuser

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Expose the port used by the Spring Boot app
EXPOSE 8080
