# ==========================================
# Multi-stage Dockerfile for Spring Boot Backend
# ==========================================

# 1. Build Stage
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Caching Gradle dependencies
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source code and build jar
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# 2. Runtime Stage
FROM eclipse-temurin:25-jre-alpine AS runner
WORKDIR /app

# Non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
