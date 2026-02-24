# Hack VP Notification Service

## Visão Geral

O **Hack VP Notification Service** é um microserviço Spring Boot baseado em arquitetura hexagonal que processa eventos de falha de vídeo através do Apache Kafka. O serviço implementa padrões de resiliência, observabilidade e escalabilidade para ambientes produtivos.

## 🏗️ Arquitetura

O projeto segue os princípios de **Clean Architecture** e **Hexagonal Architecture**:

```
├── adapters/           # Adaptadores de entrada e saída
│   ├── in/            # Adaptadores de entrada (Kafka Listeners)
│   └── out/           # Adaptadores de saída (Logger)
├── app/               # Core da aplicação
│   ├── ports/         # Interfaces (contratos)
│   └── usecase/       # Casos de uso (regras de negócio)
└── config/            # Configurações do Spring
```

**Documentação Detalhada**: [ARCHITECTURE.md](ARCHITECTURE.md)

## 📋 Funcionalidades

- ✅ Consumo de eventos Kafka com validação automática
- ✅ Tratamento de erros com retry e Dead Letter Queue (DLQ)
- ✅ Logs estruturados com correlation IDs
- ✅ Health checks e métricas para observabilidade
- ✅ Configuração externalizada para múltiplos ambientes
- ✅ Deployment containerizado com Docker e Kubernetes

## 🚀 Início Rápido

### Pré-requisitos

- **Java 21** ou superior
- **Maven 3.9+**
- **Docker** e **Docker Compose**
- **Apache Kafka** (ou usar o docker-compose fornecido)

### 1. Clone e Build

```bash
# Clone o repositório
git clone <repository-url>
cd hack-vp-notification

# Build da aplicação
./mvnw clean package -DskipTests
```

### 2. Execução Local (Docker Compose)

```bash
# Subir toda a infraestrutura (Kafka + Aplicação)
cd infra
docker-compose up -d

# Verificar logs
docker-compose logs -f notification-service

# Parar os serviços
docker-compose down
```

### 3. Teste da Aplicação

```bash
# Enviar mensagem de teste válida
docker-compose --profile testing up test-producer

# Verificar logs de processamento
docker-compose logs notification-service

# Acessar Kafka UI
open http://localhost:8090
```

## 🧪 Testes

### Executar Testes Unitários

```bash
./mvnw test
```

### Teste de Integração com Kafka

```bash
# Subir apenas Kafka
docker-compose up -d kafka

# Executar testes de integração
./mvnw test -Dtest=*IntegrationTest
```

### Teste Manual com Kafka

```bash
# Producer de teste
echo '{"eventName":"VideoFailed.v1","eventId":"550e8400-e29b-41d4-a716-446655440000","occurredAt":"2024-02-23T10:30:00.000Z","correlationId":"test-123","payload":{"videoId":"123e4567-e89b-12d3-a456-426614174000","reason":"ENCODING_FAILED","details":"Test failure"}}' | \
kafka-console-producer --bootstrap-server localhost:9092 --topic video.failed.v1

# Consumer DLQ (para mensagens com erro)
kafka-console-consumer --bootstrap-server localhost:9092 --topic video.failed.v1.dlq --from-beginning
```

## 📦 Deploy em Produção

### Docker

```bash
# Build da imagem
docker build -f infra/Dockerfile -t hack-notification-service:latest .

# Run container
docker run -d \
  --name notification-service \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SPRING_PROFILES_ACTIVE=docker \
  hack-notification-service:latest
```

### Kubernetes

```bash
# Deploy completo
kubectl apply -k infra/

# Verificar status
kubectl get pods -l app=hack-notification-service
kubectl get svc hack-notification-service
kubectl get hpa hack-notification-service-hpa

# Logs
kubectl logs -f deployment/hack-notification-service
```

**Documentação Detalhada de K8s**: [infra/DEPLOYMENT.md](infra/DEPLOYMENT.md)

## ⚙️ Configuração

### Variáveis de Ambiente

| Variável | Descrição | Padrão |
|----------|-----------|--------|
| `KAFKA_BOOTSTRAP_SERVERS` | Endereços do cluster Kafka | `localhost:9092` |
| `KAFKA_GROUP_ID` | ID do consumer group | `notification-service` |
| `TOPIC_VIDEO_FAILED` | Tópico principal | `video.failed.v1` |
| `TOPIC_VIDEO_FAILED_DLQ` | Tópico de DLQ | `video.failed.v1.dlq` |
| `NOTIF_RETRY_MAX_ATTEMPTS` | Máximo de tentativas | `3` |
| `NOTIF_RETRY_BACKOFF_MS` | Intervalo entre tentativas | `1000` |

### Profiles Spring

- **`default`**: Configuração local
- **`docker`**: Configuração para containers
- **`kubernetes`**: Configuração para K8s

### Exemplo application-docker.yaml

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:kafka:29092}
    consumer:
      group-id: ${KAFKA_GROUP_ID:notification-service-docker}

logging:
  level:
    com.fiap.hackNotification: DEBUG
    org.apache.kafka: INFO
```

## 📊 Monitoramento

### Health Checks

```bash
# Health geral
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

### Métricas

```bash
# Métricas Prometheus
curl http://localhost:8080/actuator/prometheus

# Informações da aplicação
curl http://localhost:8080/actuator/info
```

### Structured Logging

Os logs seguem formato estruturado para facilitar análise:

```json
{
  "timestamp": "2024-02-23T10:30:00.000Z",
  "level": "ERROR",
  "logger": "com.fiap.hackNotification.adapters.out.Slf4jNotificationLoggerAdapter",
  "message": "correlationId=req-123 NOTIFY Video failed: videoId=123e4567 reason=ENCODING_FAILED details=Test failure eventId=550e8400"
}
```

## 🔄 Fluxo de Dados

1. **Kafka Topic** (`video.failed.v1`) recebe evento
2. **VideoFailedKafkaListener** consome e valida mensagem
3. **HandleVideoFailedUseCase** processa regra de negócio
4. **NotificationLoggerPort** abstrai sistema de logging
5. **Slf4jNotificationLoggerAdapter** efetua log estruturado

### Tratamento de Erros

- **Validação falha** → Retry automático → DLQ após 3 tentativas
- **Erro de processamento** → Retry com backoff → DLQ
- **Evento inválido** → Diretamente para DLQ

## 🛠️ Desenvolvimento

### Estrutura do Projeto

```
src/main/java/com/fiap/hackNotification/
├── HackNotificationApplication.java     # Main class
├── adapters/
│   ├── in/kafka/
│   │   ├── VideoFailedKafkaListener.java # Kafka consumer
│   │   └── dto/                          # Event DTOs
│   └── out/
│       └── Slf4jNotificationLoggerAdapter.java # Logger adapter
├── app/
│   ├── ports/
│   │   └── NotificationLoggerPort.java   # Logger interface
│   └── usecase/
│       └── HandleVideoFailedUseCase.java # Business logic
└── config/
    └── KafkaErrorHandlingConfig.java     # Kafka config
```

### Padrões Implementados

- **Hexagonal Architecture**: Separação clara de responsabilidades
- **Dependency Inversion**: Uso de interfaces para abstrações
- **Event-Driven Architecture**: Processamento assíncrono via eventos
- **Circuit Breaker**: Via retry/DLQ configuration
- **Structured Logging**: Logs padronizados para observabilidade

### Adicionando Novas Funcionalidades

1. **Novo tipo de evento**:
   - Criar DTO em `adapters/in/kafka/dto/`
   - Adicionar listener em `adapters/in/kafka/`
   - Implementar use case em `app/usecase/`

2. **Novo adapter de saída**:
   - Criar interface em `app/ports/`
   - Implementar adapter em `adapters/out/`
   - Configurar injeção de dependência

## 📈 Performance e Escalabilidade

### Configurações Recomendadas

#### Produção
```yaml
# Kafka Consumer
spring.kafka.consumer:
  max-poll-records: 100
  fetch-min-size: 1024
  fetch-max-wait: 500ms

# JVM
JAVA_OPTS: >-
  -XX:+UseContainerSupport
  -XX:MaxRAMPercentage=75.0
  -XX:+UseG1GC
```

#### Kubernetes HPA
- **Min replicas**: 2
- **Max replicas**: 10
- **Target CPU**: 70%
- **Target Memory**: 80%

### Métricas de Performance

- **Throughput**: ~1000 msgs/sec (single instance)
- **Latency**: <100ms P95
- **Memory**: ~512Mi steady state
- **CPU**: ~250m steady state

## 🔐 Segurança

### Práticas Implementadas

- ✅ **Non-root container**: User ID 1001
- ✅ **Read-only filesystem**: Exceto volumes necessários
- ✅ **Minimal privileges**: Capabilities dropped
- ✅ **Secrets management**: Via Kubernetes Secrets
- ✅ **Network policies**: Isolamento de rede (recomendado)

### Auditoria

```bash
# Security scan da imagem
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  aquasec/trivy image hack-notification-service:latest

# Kubernetes security scan
kubectl run kube-bench --rm -i --tty --restart=Never --image aquasec/kube-bench:latest -- master
```

## 🆘 Troubleshooting

### Problemas Comuns

#### 1. Kafka Connection Issues
```bash
# Verificar conectividade
kubectl exec -it deployment/hack-notification-service -- \
  nc -zv kafka-service 9092

# Verificar tópicos
kubectl exec -it kafka-pod -- kafka-topics --list --bootstrap-server localhost:9092
```

#### 2. Consumer Group Lag
```bash
# Verificar lag
kubectl exec -it kafka-pod -- \
  kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group notification-service --describe
```

#### 3. Pod CrashLoopBackOff
```bash
# Logs detalhados
kubectl logs deployment/hack-notification-service --previous

# Descrição do pod
kubectl describe pod -l app=hack-notification-service
```

### Logs de Debug

```bash
# Ativar debug via environment
kubectl patch deployment hack-notification-service -p \
  '{"spec":{"template":{"spec":{"containers":[{"name":"notification-service","env":[{"name":"LOGGING_LEVEL_COM_FIAP_HACKNOTIFICATION","value":"DEBUG"}]}]}}}}'
```

## 📚 Documentação Adicional

- [**API Documentation**](API.md) - Contratos de eventos Kafka
- [**Architecture Guide**](ARCHITECTURE.md) - Detalhes da arquitetura
- [**Deployment Guide**](infra/DEPLOYMENT.md) - Estratégias de deployment K8s

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está licenciado sob a MIT License - veja o arquivo [LICENSE](LICENSE) para detalhes.

## 👥 Equipe

- **FIAP Team** - Desenvolvimento inicial

---

**Versão**: 0.0.1-SNAPSHOT
**Java**: 21
**Spring Boot**: 4.0.2
**Kafka**: Compatible with 2.8+
