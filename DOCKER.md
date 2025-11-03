# Docker Deployment Guide

## Overview

This application supports two deployment modes:
1. **JVM Mode** (default) - Traditional Java application
2. **Native Image Mode** - GraalVM compiled native executable

## Quick Start

### Build JVM Image (Default)
```bash
docker build -t abc-onboarding:jvm .
```

### Build Native Image
```bash
docker build --target native-runtime -t abc-onboarding:native .
```

## Image Comparison

| Feature | JVM Mode | Native Mode |
|---------|----------|-------------|
| Build Time | ~3-5 minutes | ~10-15 minutes |
| Image Size | ~250-300 MB | ~80-120 MB |
| Startup Time | ~5-10 seconds | ~0.1-0.5 seconds |
| Memory Usage | ~512 MB - 1 GB | ~128-256 MB |
| Throughput | High | Very High |
| Runtime Optimization | JIT compiler | AOT compiled |

## Running the Containers

### JVM Mode
```bash
docker run -d \
  --name abc-onboarding-jvm \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/onboarding \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=admin123 \
  -e SPRING_REDIS_HOST=redis \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  -e JWT_SECRET=your-secret-key \
  -e ENCRYPTION_KEY=your-encryption-key \
  abc-onboarding:jvm
```

### Native Mode
```bash
docker run -d \
  --name abc-onboarding-native \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/onboarding \
  -e SPRING_DATASOURCE_USERNAME=admin \
  -e SPRING_DATASOURCE_PASSWORD=admin123 \
  -e SPRING_REDIS_HOST=redis \
  -e SPRING_RABBITMQ_HOST=rabbitmq \
  abc-onboarding:native
```

## Building Locally

### Prerequisites
- Docker 24.0+
- Maven 3.9+
- Java 21 (for JVM build)
- GraalVM 21 (for native build)

### Build Native Image Locally (Without Docker)
```bash
# Install GraalVM 21
sdk install java 21-graalvm

# Build native image
./mvnw clean native:compile -Pnative -DskipTests

# Run the native executable
./target/digital-onboarding
```

## Multi-Stage Build Explanation

The Dockerfile uses a multi-stage build with 4 stages:

1. **builder** - Builds the JAR file with Maven
2. **native-builder** - Builds GraalVM native image
3. **jvm-runtime** - Final JVM runtime image (default)
4. **native-runtime** - Final native runtime image

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| SPRING_DATASOURCE_URL | PostgreSQL connection URL | - |
| SPRING_DATASOURCE_USERNAME | Database username | - |
| SPRING_DATASOURCE_PASSWORD | Database password | - |
| SPRING_REDIS_HOST | Redis hostname | localhost |
| SPRING_REDIS_PORT | Redis port | 6379 |
| SPRING_RABBITMQ_HOST | RabbitMQ hostname | localhost |
| SPRING_RABBITMQ_PORT | RabbitMQ port | 5672 |
| JWT_SECRET | JWT signing secret | (set in Dockerfile) |
| ENCRYPTION_KEY | Data encryption key | (set in Dockerfile) |
| JAVA_OPTS | JVM options (JVM mode only) | See Dockerfile |

## Health Checks

Both images include health checks:
- **Endpoint**: `http://localhost:8080/actuator/health`
- **Interval**: 30 seconds
- **Timeout**: 10 seconds
- **Start Period**: 60s (JVM), 5s (Native)
- **Retries**: 3

## Performance Tuning

### JVM Mode
JVM options are pre-configured for containerized environments:
- Uses G1GC garbage collector
- MaxRAMPercentage set to 75%
- String deduplication enabled
- Container support enabled

### Native Mode
Native image build options:
- All security services enabled
- HTTPS/HTTP support included
- Full charset support
- Detailed exception stack traces
- 8GB heap for build process

## Troubleshooting

### Native Image Build Fails

1. **Insufficient Memory**
   ```bash
   # Increase Docker memory limit to 10GB+
   # Or reduce -J-Xmx8g in pom.xml native profile
   ```

2. **Missing Reflection Configuration**
   - Check `src/main/resources/META-INF/native-image/` for missing classes
   - Use GraalVM tracing agent to generate configs:
   ```bash
   java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image/com.abcbank.onboarding \
        -jar target/onboarding-1.0.0.jar
   ```

3. **Serialization Issues**
   - Add missing classes to `serialization-config.json`

### Container Won't Start

1. **Check logs**
   ```bash
   docker logs abc-onboarding-jvm
   ```

2. **Verify environment variables**
   ```bash
   docker inspect abc-onboarding-jvm
   ```

3. **Test database connectivity**
   ```bash
   docker exec -it abc-onboarding-jvm curl localhost:8080/actuator/health
   ```

## Best Practices

1. **Use Native Image for Production** - Faster startup, lower memory footprint
2. **Use JVM Mode for Development** - Faster build times, better debugging
3. **Always Set Custom Secrets** - Never use default JWT_SECRET or ENCRYPTION_KEY
4. **Monitor Resource Usage** - Use container limits and metrics
5. **Use Health Checks** - Configure load balancers and orchestrators accordingly

## Integration with Docker Compose

See `docker-compose.yml` for a complete stack including:
- PostgreSQL database
- Redis cache
- RabbitMQ message broker
- Application (JVM or Native)

## Cloud Deployment

### AWS ECS/Fargate
```bash
# Tag for ECR
docker tag abc-onboarding:native 123456789.dkr.ecr.us-east-1.amazonaws.com/abc-onboarding:native

# Push to ECR
docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/abc-onboarding:native
```

### Kubernetes
```bash
# Build and tag
docker build --target native-runtime -t abc-onboarding:native .

# Deploy to Kubernetes
kubectl apply -f k8s/deployment.yaml
```

## License

Copyright © 2024 ABC Bank. All rights reserved.
