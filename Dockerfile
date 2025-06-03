# ---------- Stage 1: Build ----------
FROM maven:3.9.6-eclipse-temurin-22-alpine AS BUILDER

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# ---------- Stage 2: Run ----------
FROM eclipse-temurin:22-jre-alpine AS RUNNER

WORKDIR /app

COPY --from=BUILDER /app/target/mealsync-0.0.1-SNAPSHOT.jar /app/mealsync.jar

EXPOSE 8083

CMD ["java", "-jar", "mealsync.jar"]
