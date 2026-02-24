# Kubernetes Deployment Strategy - Hack VP Notification Service

## Visão Geral

Esta documentação detalha a estratégia de externalização de configurações e deployment do Hack VP Notification Service em ambientes Kubernetes.

## Estrutura de Configuração

### 1. ConfigMaps - Configurações Não-Sensíveis

**Arquivo**: `k8s-configmap.yaml`

As configurações são externalizadas através de ConfigMaps, permitindo:
- Modificação sem rebuild da imagem
- Configuração específica por ambiente
- Versionamento independente das configurações

```yaml
# Principais configurações externalizadas:
- Spring Profiles
- Kafka Bootstrap Servers
- Topic Names
- Retry Configuration
- Logging Levels
- JVM Options
```

### 2. Secrets - Configurações Sensíveis

**Arquivo**: `k8s-secrets.yaml`

Dados sensíveis são armazenados em Secrets Kubernetes:
- Credenciais Kafka (se aplicável)
- Certificados TLS
- Tokens de autenticação
- Credenciais de banco de dados (futuras)

### 3. Estratégias de Configuração por Ambiente

#### Desenvolvimento
```bash
# Override via Kustomize
kubectl apply -k infra/overlays/development
```

#### Staging
```bash
kubectl apply -k infra/overlays/staging
```

#### Produção
```bash
kubectl apply -k infra/overlays/production
```

## Configuração de Variáveis de Ambiente

### Mapeamento ConfigMap → Environment Variables

| ConfigMap Key | Environment Variable | Valor Padrão |
|---------------|---------------------|--------------|
| `kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `kafka-service:9092` |
| `kafka.group-id` | `KAFKA_GROUP_ID` | `notification-service` |
| `topic.video-failed` | `TOPIC_VIDEO_FAILED` | `video.failed.v1` |
| `topic.video-failed-dlq` | `TOPIC_VIDEO_FAILED_DLQ` | `video.failed.v1.dlq` |
| `notification.retry.max-attempts` | `NOTIF_RETRY_MAX_ATTEMPTS` | `3` |
| `notification.retry.backoff-ms` | `NOTIF_RETRY_BACKOFF_MS` | `1000` |

### Exemplo de Override por Ambiente

```yaml
# overlays/production/configmap-patch.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: hack-notification-config
data:
  kafka.bootstrap-servers: "prod-kafka-cluster:9092"
  notification.retry.max-attempts: "5"
  logging.level.com.fiap.hacknotification: "WARN"
```

## Deployment Resources

### 1. Deployment (`k8s-deployment.yaml`)
- **Replicas**: 2 (mínimo para HA)
- **Resource Limits**: 1Gi RAM / 500m CPU
- **Security Context**: Non-root user (1001)
- **Probes**: Liveness, Readiness, Startup
- **Volume Mounts**: Logs, Temp directories

### 2. Service (`k8s-service.yaml`)
- **ClusterIP**: Para comunicação interna
- **Headless Service**: Para descoberta de pods
- **Ingress**: Para acesso a endpoints de management

### 3. HPA (`k8s-hpa.yaml`)
- **Auto Scaling**: 2-10 replicas
- **Métricas**: CPU (70%), Memory (80%)
- **Comportamento**: Scale up rápido, scale down gradual

### 4. PDB (Pod Disruption Budget)
- **Min Available**: 1 pod sempre disponível
- **Proteção**: Durante updates e maintenance

## Observabilidade

### Health Checks
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```

### Metrics
- **Prometheus**: Endpoint `/actuator/prometheus`
- **ServiceMonitor**: Auto descoberta pelo Prometheus Operator

### Logs
- **Structured Logging**: JSON format
- **Correlation IDs**: Para rastreabilidade
- **Log Aggregation**: Via Logstash (opcional)

## Segurança

### Pod Security
```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1001
  runAsGroup: 1001
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
```

### RBAC
- **ServiceAccount**: Permissões mínimas necessárias
- **Role**: Acesso a ConfigMaps e Pods (read-only)
- **RoleBinding**: Vinculação da conta de serviço

### Network Policies (Recomendado)
```yaml
# Exemplo de NetworkPolicy
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: hack-notification-network-policy
spec:
  podSelector:
    matchLabels:
      app: hack-notification-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: monitoring
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: kafka
    ports:
    - protocol: TCP
      port: 9092
```

## Estratégias de Deploy

### 1. Rolling Update (Padrão)
```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 1
    maxSurge: 1
```

### 2. Blue-Green Deploy
```bash
# Usando Argo Rollouts ou Flagger
kubectl apply -f argo-rollouts/blue-green.yaml
```

### 3. Canary Deploy
```bash
# Usando Istio ou Linkerd
kubectl apply -f istio/canary-deploy.yaml
```

## Configuração por Ambiente

### Development
```yaml
# overlays/development/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
- ../../base

patchesStrategicMerge:
- deployment-dev.yaml
- configmap-dev.yaml

images:
- name: hack-notification-service
  newTag: dev-latest

replicas:
- name: hack-notification-service
  count: 1
```

### Production
```yaml
# overlays/production/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

resources:
- ../../base

patchesStrategicMerge:
- deployment-prod.yaml
- configmap-prod.yaml
- secrets-prod.yaml

images:
- name: hack-notification-service
  newTag: 1.0.0

replicas:
- name: hack-notification-service
  count: 3
```

## Comandos de Deployment

### Deploy Inicial
```bash
# Aplicar todos os recursos
kubectl apply -k infra/

# Verificar status
kubectl get pods -l app=hack-notification-service
kubectl get svc hack-notification-service
kubectl get hpa hack-notification-service-hpa
```

### Atualização de Configuração
```bash
# Atualizar ConfigMap
kubectl patch configmap hack-notification-config -p '{"data":{"kafka.bootstrap-servers":"new-kafka:9092"}}'

# Reiniciar deployment para aplicar nova config
kubectl rollout restart deployment/hack-notification-service
```

### Rollback
```bash
# Verificar histórico
kubectl rollout history deployment/hack-notification-service

# Fazer rollback
kubectl rollout undo deployment/hack-notification-service

# Rollback para versão específica
kubectl rollout undo deployment/hack-notification-service --to-revision=2
```

### Troubleshooting
```bash
# Logs dos pods
kubectl logs -f deployment/hack-notification-service

# Descrição dos pods
kubectl describe pod -l app=hack-notification-service

# Eventos do namespace
kubectl get events --sort-by=.metadata.creationTimestamp

# Shell no pod
kubectl exec -it deployment/hack-notification-service -- /bin/sh
```

## Backup e Restore

### ConfigMaps e Secrets
```bash
# Backup
kubectl get configmap hack-notification-config -o yaml > backup/configmap.yaml
kubectl get secret hack-notification-secrets -o yaml > backup/secrets.yaml

# Restore
kubectl apply -f backup/configmap.yaml
kubectl apply -f backup/secrets.yaml
```

## Monitoramento de Recursos

### Métricas Importantes
- CPU/Memory utilization
- Pod restart count
- Kafka consumer lag
- Processing latency
- Error rate

### Alertas Sugeridos
```yaml
# Exemplo de PrometheusRule
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: hack-notification-alerts
spec:
  groups:
  - name: hack-notification.rules
    rules:
    - alert: HighPodRestarts
      expr: increase(kube_pod_container_status_restarts_total{pod=~"hack-notification-service-.*"}[1h]) > 3
      labels:
        severity: warning
      annotations:
        summary: "High pod restart rate"

    - alert: KafkaConsumerLag
      expr: kafka_consumer_lag_sum > 1000
      labels:
        severity: critical
      annotations:
        summary: "High Kafka consumer lag"
```

Esta estratégia de externalização permite máxima flexibilidade na configuração do serviço em diferentes ambientes Kubernetes, mantendo a segurança e observabilidade necessárias para ambientes produtivos.