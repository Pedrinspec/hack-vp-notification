# =============================================================================
# hack-vp-notification — Dockerfile
# Spring Boot 4 + Java 21 + Kafka consumer (retry + DLQ)
# Multi-stage build: build → runtime
# =============================================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x ./mvnw \
    && ./mvnw dependency:go-offline -B --no-transfer-progress

COPY src/ ./src/

RUN ./mvnw package -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="FIAP Hackathon"
LABEL app="hack-vp-notification"
LABEL version="0.0.1-SNAPSHOT"
LABEL description="Serviço de notificação — Kafka consumer com retry e DLQ"

RUN apk add --no-cache dumb-init

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
