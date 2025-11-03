# Multi-stage Dockerfile for Spring Boot application with GraalVM native image support
# This Dockerfile provides both JVM and Native Image options

# ==============================================================================
# Stage 1: Build Stage (JVM)
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: GraalVM Native Image Build Stage
# ==============================================================================
FROM ghcr.io/graalvm/native-image-community:21-ol9 AS native-builder

WORKDIR /app

# Install required tools
RUN microdnf install -y findutils

# Copy Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build native image
RUN ./mvnw clean native:compile -Pnative -DskipTests -B

# ==============================================================================
# Stage 3: JVM Runtime Image
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS jvm-runtime

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy jar from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM tuning for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+UseStringDeduplication \
               -Djava.security.egd=file:/dev/./urandom"

ENV JWT_SECRET="myVeryLongSecretKeyThatIs32BytesLongForHMACShA512AlgorithmToWorkProperly123"
ENV ENCRYPTION_KEY="dGVzdEVuY3J5cHRpb25LZXlGb3JHVW5pdFRlc3RzT25seQ=="

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# ==============================================================================
# Stage 4: Native Runtime Image
# ==============================================================================
FROM oraclelinux:9-slim AS native-runtime

# Install required runtime libraries and curl for health checks
RUN microdnf install -y \
    glibc-langpack-en \
    curl \
    && microdnf clean all

# Create non-root user
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m appuser

WORKDIR /app

# Copy native executable from native-builder stage
COPY --from=native-builder /app/target/digital-onboarding ./digital-onboarding

# Set ownership and make executable
RUN chown appuser:appgroup digital-onboarding && \
    chmod +x digital-onboarding

USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["./digital-onboarding"]

# ==============================================================================
# Default Stage: JVM Runtime (for backward compatibility)
# ==============================================================================
FROM jvm-runtime AS final