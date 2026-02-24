# Multi-stage build for Spring Boot application
FROM maven:3.9.8-amazoncorretto-21-alpine AS builder

# Set working directory
WORKDIR /app

# Copy Maven configuration files
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (better caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src src

# Build the application
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM amazoncorretto:21-alpine3.19-jre

# Install dumb-init for proper signal handling
RUN apk add --no-cache dumb-init=1.2.5-r2

# Create non-root user for security
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership to spring user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Health check via actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port (for potential management endpoints)
EXPOSE 8080

# Set JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/tmp/heapdump.hprof \
               -Djava.security.egd=file:/dev/./urandom"

# Application configuration
ENV SPRING_PROFILES_ACTIVE=docker

# Use dumb-init to handle signals properly
ENTRYPOINT ["dumb-init", "--"]

# Start the application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Labels for metadata
LABEL maintainer="FIAP Team"
LABEL version="0.0.1-SNAPSHOT"
LABEL description="Hack VP Notification Service - Kafka Consumer for Video Failed Events"
LABEL org.opencontainers.image.source="https://github.com/fiap/hack-vp-notification"
LABEL org.opencontainers.image.title="hack-vp-notification"
LABEL org.opencontainers.image.description="Spring Boot microservice for processing video failure notifications via Kafka"
LABEL org.opencontainers.image.vendor="FIAP"