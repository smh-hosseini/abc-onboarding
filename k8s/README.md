# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying the ABC Bank Digital Onboarding System.

## Directory Structure

```
k8s/
├── base/                           # Base Kubernetes manifests
│   ├── namespace.yaml              # Namespace definition
│   ├── configmap.yaml              # Application configuration
│   ├── secret.yaml                 # Sensitive configuration (passwords, keys)
│   ├── postgres-statefulset.yaml   # PostgreSQL database
│   ├── redis-deployment.yaml       # Redis cache
│   ├── rabbitmq-deployment.yaml    # RabbitMQ message broker
│   ├── minio-deployment.yaml       # MinIO object storage
│   ├── application-deployment.yaml # Main Spring Boot application
│   ├── application-service.yaml    # Service for the application
│   ├── ingress.yaml                # Ingress rules for external access
│   └── kustomization.yaml          # Kustomize base configuration
└── overlays/                       # Environment-specific overlays
    ├── dev/                        # Development environment
    │   ├── kustomization.yaml
    │   ├── deployment-patch.yaml
    │   └── configmap-patch.yaml
    └── prod/                       # Production environment
        ├── kustomization.yaml
        ├── deployment-patch.yaml
        ├── configmap-patch.yaml
        └── hpa-patch.yaml
```

## Prerequisites

1. **Kubernetes Cluster** (v1.26+)
   - Minikube, Kind, or cloud provider (EKS, GKE, AKS)

2. **kubectl** CLI installed and configured

3. **Kustomize** (v5.0+) - Usually bundled with kubectl

4. **Docker** - To build the application image

5. **NGINX Ingress Controller** (optional, for ingress)
   ```bash
   kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/cloud/deploy.yaml
   ```

6. **cert-manager** (optional, for TLS certificates)
   ```bash
   kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
   ```

## Build Docker Image

Before deploying, build the application Docker image:

```bash
# From project root
docker build -t onboarding-app:latest .

# For production, tag with version
docker build -t onboarding-app:v1.0.0 .

# If using a registry, push the image
docker tag onboarding-app:latest your-registry/onboarding-app:latest
docker push your-registry/onboarding-app:latest
```

For Minikube, load the image directly:
```bash
minikube image load onboarding-app:latest
```

## Configuration

### Secrets

**IMPORTANT**: Before deploying to production, update the secrets in `base/secret.yaml`:

```yaml
# Change these values in production!
SPRING_DATASOURCE_PASSWORD: "your-secure-password"
JWT_SECRET: "your-256-bit-secret-key"
ENCRYPTION_KEY: "your-base64-encoded-key"
POSTGRES_PASSWORD: "your-postgres-password"
RABBITMQ_DEFAULT_PASS: "your-rabbitmq-password"
MINIO_ROOT_PASSWORD: "your-minio-password"
```

For production, consider using:
- **External Secrets Operator**: Sync secrets from AWS Secrets Manager, Azure Key Vault, etc.
- **Sealed Secrets**: Encrypt secrets before committing to Git
- **HashiCorp Vault**: Dynamic secret generation

### ConfigMap

Application configuration can be modified in `base/configmap.yaml` or environment-specific patches.

## Deployment

### Development Environment

Deploy to development environment with reduced resources:

```bash
# Apply development configuration
kubectl apply -k k8s/overlays/dev

# Verify deployment
kubectl get pods -n abc-onboarding-dev

# Check application logs
kubectl logs -f -n abc-onboarding-dev deployment/dev-onboarding-app

# Port-forward to access locally
kubectl port-forward -n abc-onboarding-dev svc/dev-onboarding-service 8080:80
```

### Production Environment

Deploy to production with full resources and autoscaling:

```bash
# Apply production configuration
kubectl apply -k k8s/overlays/prod

# Verify deployment
kubectl get pods -n abc-onboarding
kubectl get hpa -n abc-onboarding

# Check application status
kubectl get deployment -n abc-onboarding
kubectl get statefulset -n abc-onboarding
```

### Base Configuration (Manual)

If not using Kustomize overlays:

```bash
# Apply base manifests
kubectl apply -k k8s/base

# Or apply files individually
kubectl apply -f k8s/base/namespace.yaml
kubectl apply -f k8s/base/configmap.yaml
kubectl apply -f k8s/base/secret.yaml
kubectl apply -f k8s/base/postgres-statefulset.yaml
kubectl apply -f k8s/base/redis-deployment.yaml
kubectl apply -f k8s/base/rabbitmq-deployment.yaml
kubectl apply -f k8s/base/minio-deployment.yaml
kubectl apply -f k8s/base/application-deployment.yaml
kubectl apply -f k8s/base/application-service.yaml
kubectl apply -f k8s/base/ingress.yaml
```

## Verification

### Check All Resources

```bash
# List all resources in namespace
kubectl get all -n abc-onboarding

# Check pods status
kubectl get pods -n abc-onboarding -w

# Check persistent volumes
kubectl get pvc -n abc-onboarding

# Check services
kubectl get svc -n abc-onboarding

# Check ingress
kubectl get ingress -n abc-onboarding
```

### Verify Application Health

```bash
# Port-forward to access health endpoint
kubectl port-forward -n abc-onboarding svc/onboarding-service 8081:8081

# Check health (in another terminal)
curl http://localhost:8081/actuator/health

# Check readiness
curl http://localhost:8081/actuator/health/readiness

# Check liveness
curl http://localhost:8081/actuator/health/liveness
```

### View Logs

```bash
# Application logs
kubectl logs -f -n abc-onboarding deployment/onboarding-app

# Database logs
kubectl logs -f -n abc-onboarding statefulset/postgres

# Redis logs
kubectl logs -f -n abc-onboarding deployment/redis

# RabbitMQ logs
kubectl logs -f -n abc-onboarding deployment/rabbitmq

# MinIO logs
kubectl logs -f -n abc-onboarding deployment/minio
```

## Scaling

### Manual Scaling

```bash
# Scale application replicas
kubectl scale deployment/onboarding-app -n abc-onboarding --replicas=5

# Scale PostgreSQL (StatefulSet)
kubectl scale statefulset/postgres -n abc-onboarding --replicas=3
```

### Auto Scaling (HPA)

Horizontal Pod Autoscaler is configured in production:

```bash
# Check HPA status
kubectl get hpa -n abc-onboarding

# Describe HPA details
kubectl describe hpa onboarding-hpa -n abc-onboarding

# Test autoscaling with load
kubectl run -it --rm load-generator --image=busybox -n abc-onboarding -- /bin/sh
# Inside the pod:
while true; do wget -q -O- http://onboarding-service/api/health; done
```

## Accessing Services

### Local Development (Port Forwarding)

```bash
# Application API
kubectl port-forward -n abc-onboarding svc/onboarding-service 8080:80

# PostgreSQL
kubectl port-forward -n abc-onboarding svc/postgres-service 5432:5432

# Redis
kubectl port-forward -n abc-onboarding svc/redis-service 6379:6379

# RabbitMQ Management
kubectl port-forward -n abc-onboarding svc/rabbitmq-service 15672:15672

# MinIO Console
kubectl port-forward -n abc-onboarding svc/minio-service 9001:9001
```

### Via Ingress

Configure DNS to point to your ingress controller:

```
onboarding.abcbank.com  -> Ingress Controller IP
minio.abcbank.com       -> Ingress Controller IP
rabbitmq.abcbank.com    -> Ingress Controller IP
```

Get Ingress IP:
```bash
kubectl get ingress -n abc-onboarding
```

## Monitoring

### Check Resource Usage

```bash
# Pod resource usage
kubectl top pods -n abc-onboarding

# Node resource usage
kubectl top nodes
```

### Prometheus Metrics

Application exposes Prometheus metrics at `/actuator/prometheus`:

```bash
# Port-forward to metrics endpoint
kubectl port-forward -n abc-onboarding svc/onboarding-service 8081:8081

# Scrape metrics
curl http://localhost:8081/actuator/prometheus
```

## Troubleshooting

### Pod Not Starting

```bash
# Describe pod to see events
kubectl describe pod <pod-name> -n abc-onboarding

# Check pod logs
kubectl logs <pod-name> -n abc-onboarding

# Check previous container logs (if crashed)
kubectl logs <pod-name> -n abc-onboarding --previous

# Get into a running pod
kubectl exec -it <pod-name> -n abc-onboarding -- /bin/sh
```

### Database Connection Issues

```bash
# Check PostgreSQL is running
kubectl get pods -n abc-onboarding -l app=postgres

# Test database connection
kubectl run psql-test --rm -it --image=postgres:16 -n abc-onboarding -- \
  psql -h postgres-service -U admin -d onboarding

# Check database logs
kubectl logs -n abc-onboarding statefulset/postgres
```

### Service Discovery Issues

```bash
# Check service endpoints
kubectl get endpoints -n abc-onboarding

# Test DNS resolution
kubectl run busybox --rm -it --image=busybox -n abc-onboarding -- \
  nslookup postgres-service.abc-onboarding.svc.cluster.local
```

### Storage Issues

```bash
# Check PVC status
kubectl get pvc -n abc-onboarding

# Describe PVC
kubectl describe pvc <pvc-name> -n abc-onboarding

# Check available storage classes
kubectl get storageclass
```

## Backup and Restore

### PostgreSQL Backup

```bash
# Create backup
kubectl exec -n abc-onboarding statefulset/postgres -- \
  pg_dump -U admin onboarding > backup.sql

# Restore from backup
kubectl exec -i -n abc-onboarding statefulset/postgres -- \
  psql -U admin onboarding < backup.sql
```

### MinIO Backup

```bash
# Port-forward MinIO
kubectl port-forward -n abc-onboarding svc/minio-service 9000:9000

# Use mc (MinIO Client) to backup
mc alias set k8s-minio http://localhost:9000 minioadmin minioadmin123
mc mirror k8s-minio/onboarding-documents ./minio-backup
```

## Cleanup

### Remove Development Environment

```bash
kubectl delete -k k8s/overlays/dev
kubectl delete namespace abc-onboarding-dev
```

### Remove Production Environment

```bash
kubectl delete -k k8s/overlays/prod
```

### Remove All Resources

```bash
kubectl delete -k k8s/base
kubectl delete namespace abc-onboarding
```

**Note**: This will delete all data including persistent volumes!

## Security Considerations

1. **Secrets Management**
   - Never commit real secrets to Git
   - Use external secret managers in production
   - Rotate secrets regularly

2. **Network Policies**
   - Consider implementing NetworkPolicies to restrict pod-to-pod communication
   - Limit ingress/egress traffic

3. **RBAC**
   - Create service accounts with minimal required permissions
   - Use namespace isolation

4. **Image Security**
   - Scan images for vulnerabilities
   - Use specific image tags (not `latest`)
   - Use private registries for production

5. **TLS/SSL**
   - Enable TLS for all external endpoints
   - Use cert-manager for automatic certificate management

## Production Checklist

- [ ] Update all secrets with strong, unique values
- [ ] Configure external secret management
- [ ] Set up proper DNS records
- [ ] Configure TLS certificates
- [ ] Enable monitoring and alerting
- [ ] Set up log aggregation
- [ ] Configure backup strategy
- [ ] Implement network policies
- [ ] Set resource limits and requests
- [ ] Configure PodDisruptionBudgets
- [ ] Set up horizontal pod autoscaling
- [ ] Configure health checks properly
- [ ] Review and harden security settings
- [ ] Set up CI/CD pipeline for deployments
- [ ] Document runbooks for common operations


