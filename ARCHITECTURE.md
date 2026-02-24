# Hack VP Notification Service — Arquitetura

## 1. Visão Geral

O **Hack VP Notification Service** é um microserviço Spring Boot responsável por consumir e processar eventos de falha de vídeo publicados no Apache Kafka. Ao receber um evento `VideoFailed.v1`, o serviço valida, processa e registra a ocorrência de forma estruturada, com rastreabilidade por `correlationId`.

O serviço é **stateless**, **event-driven** e segue o padrão de **Arquitetura Hexagonal (Ports & Adapters)**, que garante total desacoplamento entre o núcleo de negócio e os mecanismos de infraestrutura.

---

## 2. Diagrama de Arquitetura Hexagonal

```
╔══════════════════════════════════════════════════════════════════════╗
║                         NOTIFICATION SERVICE                        ║
║                                                                      ║
║  ┌──────────────────────────────────────────────────────────────┐    ║
║  │                  ADAPTERS DE ENTRADA (in/)                   │    ║
║  │                                                              │    ║
║  │  ┌────────────────────────────────────────────────────────┐  │    ║
║  │  │          VideoFailedKafkaListener                      │  │    ║
║  │  │  @KafkaListener(topics = "${notification.topics.videoFailed}")│  │    ║
║  │  │                                                        │  │    ║
║  │  │  1. Deserializa JSON → EventEnvelope<VideoFailedPayload>│  │    ║
║  │  │  2. Valida via Bean Validation (JSR-303)               │  │    ║
║  │  │  3. Verifica eventName == "VideoFailed.v1"             │  │    ║
║  │  │  4. Delega para UseCase                                │  │    ║
║  │  │  5. Realiza ACK manual do offset                       │  │    ║
║  │  └────────────────────────────────────────────────────────┘  │    ║
║  │                                                              │    ║
║  │  DTOs: EventEnvelope<T>  │  VideoFailedPayload               │    ║
║  └─────────────────────────────────────────────────────────────┘    ║
║                              │                                       ║
║                              ▼                                       ║
║  ┌──────────────────────────────────────────────────────────────┐    ║
║  │                   NÚCLEO DE APLICAÇÃO (app/)                 │    ║
║  │                                                              │    ║
║  │  ┌────────────────────────────────────────────────────────┐  │    ║
║  │  │            HandleVideoFailedUseCase                    │  │    ║
║  │  │  @Service                                              │  │    ║
║  │  │                                                        │  │    ║
║  │  │  - Extrai videoId, reason, details do payload          │  │    ║
║  │  │  - Formata mensagem estruturada de log                 │  │    ║
║  │  │  - Delega para NotificationLoggerPort                  │  │    ║
║  │  └────────────────────────────────────────────────────────┘  │    ║
║  │                              │                               │    ║
║  │  ┌────────────────────────────────────────────────────────┐  │    ║
║  │  │           NotificationLoggerPort (interface)           │  │    ║
║  │  │  void logFailure(String correlationId, String message) │  │    ║
║  │  └────────────────────────────────────────────────────────┘  │    ║
║  └─────────────────────────────────────────────────────────────┘    ║
║                              │                                       ║
║                              ▼                                       ║
║  ┌──────────────────────────────────────────────────────────────┐    ║
║  │                  ADAPTERS DE SAÍDA (out/)                    │    ║
║  │                                                              │    ║
║  │  ┌────────────────────────────────────────────────────────┐  │    ║
║  │  │        Slf4jNotificationLoggerAdapter                  │  │    ║
║  │  │  implements NotificationLoggerPort                     │  │    ║
║  │  │                                                        │  │    ║
║  │  │  log.error("correlationId={} {}", correlationId, msg)  │  │    ║
║  │  └────────────────────────────────────────────────────────┘  │    ║
║  └─────────────────────────────────────────────────────────────┘    ║
╚══════════════════════════════════════════════════════════════════════╝

INFRAESTRUTURA EXTERNA

 ┌──────────────────────┐          ┌──────────────────────┐
 │   Apache Kafka        │          │   SLF4J / Logback     │
 │  video.failed.v1     │  (in)    │  (stdout / arquivo)  │
 │  video.failed.v1.dlq │  (out)   │                      │
 └──────────────────────┘          └──────────────────────┘

TRATAMENTO DE ERROS

 Mensagem → Listener → FALHA
                         │
                         ▼
              DefaultErrorHandler
              FixedBackOff(1000ms, 2 retries)
                         │
              Ainda falha após retries?
                         │
                         ▼
           DeadLetterPublishingRecoverer
                  → video.failed.v1.dlq
```

---

## 3. Estrutura de Pacotes

```
com.fiap.hackNotification/
│
├── HackNotificationApplication.java          # Bootstrap Spring Boot
│
├── adapters/
│   ├── in/
│   │   └── kafka/
│   │       ├── VideoFailedKafkaListener.java  # Consumer Kafka (adapter in)
│   │       └── dto/
│   │           ├── EventEnvelope.java         # Envelope genérico de evento
│   │           └── VideoFailedPayload.java    # Payload específico do evento
│   └── out/
│       └── Slf4jNotificationLoggerAdapter.java # Implementação de logging (adapter out)
│
├── app/
│   ├── ports/
│   │   └── NotificationLoggerPort.java        # Porta de saída (interface)
│   └── usecase/
│       └── HandleVideoFailedUseCase.java      # Caso de uso (regra de negócio)
│
└── config/
    └── KafkaErrorHandlingConfig.java          # Configuração de DLQ e retry Kafka
```

---

## 4. Responsabilidades dos Componentes

| Componente | Camada | Responsabilidade |
|---|---|---|
| `HackNotificationApplication` | Bootstrap | Ponto de entrada Spring Boot |
| `VideoFailedKafkaListener` | Adapter In | Consumir, desserializar e validar mensagens Kafka |
| `EventEnvelope<T>` | DTO | Envelope imutável (Java Record) para eventos |
| `VideoFailedPayload` | DTO | Payload específico do evento de falha de vídeo |
| `HandleVideoFailedUseCase` | Use Case | Orquestrar o processamento, formatar e delegar log |
| `NotificationLoggerPort` | Port (interface) | Contrato de saída para logging (DIP) |
| `Slf4jNotificationLoggerAdapter` | Adapter Out | Implementação do port via SLF4J |
| `KafkaErrorHandlingConfig` | Config | Configurar Producer DLQ, retry e error handler |

---

## 5. Fluxo de Dados — Caminho Feliz (Happy Path)

```
┌─────────────────┐
│ Kafka Producer  │  Publica JSON no tópico video.failed.v1
│ (sistema externo)│
└────────┬────────┘
         │ Mensagem JSON (String)
         ▼
┌─────────────────────────────────────────────────────┐
│ VideoFailedKafkaListener.onMessage()                │
│                                                     │
│  1. objectMapper.readValue(record.value(), ...)     │
│     → EventEnvelope<VideoFailedPayload>             │
│                                                     │
│  2. validator.validate(event)                       │
│     → violations.isEmpty() == true                  │
│                                                     │
│  3. event.eventName().equals("VideoFailed.v1")      │
│     → true                                          │
│                                                     │
│  4. useCase.handle(event)  ──────────────────────┐  │
│                                                  │  │
│  5. ack.acknowledge()  ← offset commitado        │  │
└─────────────────────────────────────────────────────┘
                                                   │
         ┌─────────────────────────────────────────┘
         ▼
┌─────────────────────────────────────────────────────┐
│ HandleVideoFailedUseCase.handle()                   │
│                                                     │
│  var p = event.payload()                            │
│  logger.logFailure(                                 │
│    event.correlationId(),                           │
│    "NOTIFY Video failed: videoId=%s reason=%s ..."  │
│  )  ─────────────────────────────────────────────┐  │
└─────────────────────────────────────────────────────┘
                                                   │
         ┌─────────────────────────────────────────┘
         ▼
┌─────────────────────────────────────────────────────┐
│ Slf4jNotificationLoggerAdapter.logFailure()         │
│                                                     │
│  log.error("correlationId={} {}", correlationId,   │
│            message)                                 │
└─────────────────────────────────────────────────────┘
         │
         ▼
   [SLF4J → Logback → stdout / arquivo de log]
```

---

## 6. Fluxo de Erros — Caminho de Exceção (Error Path)

```
Mensagem chega ao Kafka
         │
         ▼
VideoFailedKafkaListener.onMessage()
         │
         ├── FALHA de desserialização JSON   ─┐
         ├── FALHA de validação (violations)  ─┤─→ throw Exception
         └── eventName != "VideoFailed.v1"   ─┘
                          │
                          ▼
              DefaultErrorHandler (Spring Kafka)
                          │
              ┌───────────┴────────────┐
              │  FixedBackOff          │
              │  interval: 1000ms      │
              │  maxRetries: 2         │
              │  (maxAttempts - 1 = 2) │
              └───────────────────────┘
                          │
           Tentativa 1 → Falha → aguarda 1000ms
           Tentativa 2 → Falha → aguarda 1000ms
           Tentativa 3 → Falha
                          │
                          ▼
         DeadLetterPublishingRecoverer
                          │
                          ▼
           video.failed.v1.dlq (mesma partição)
```

---

## 7. Configuração de Infraestrutura Kafka

```
┌─────────────────────────────────────────────────────┐
│ KafkaErrorHandlingConfig (@Configuration)           │
│                                                     │
│  ProducerFactory<String, String>                    │
│    └─ StringSerializer (key + value)                │
│                                                     │
│  KafkaTemplate<String, String>                      │
│    └─ Usado pelo DeadLetterPublishingRecoverer      │
│                                                     │
│  DefaultErrorHandler                                │
│    ├─ DeadLetterPublishingRecoverer → DLQ topic     │
│    └─ FixedBackOff(backoffMs, maxAttempts-1)        │
│                                                     │
│  ConcurrentKafkaListenerContainerFactory            │
│    ├─ ConsumerFactory (auto-configurado)            │
│    └─ CommonErrorHandler = DefaultErrorHandler      │
└─────────────────────────────────────────────────────┘
```

---

## 8. Padrões de Design Aplicados

| Padrão | Onde aplicado | Benefício |
|---|---|---|
| **Hexagonal Architecture** | Estrutura de pacotes adapters/app | Isolamento de infraestrutura |
| **Ports & Adapters** | `NotificationLoggerPort` + `Slf4jNotificationLoggerAdapter` | Troca de implementação sem impacto no core |
| **Dependency Inversion (DIP)** | `HandleVideoFailedUseCase` depende de interface | Testabilidade e flexibilidade |
| **Event-Driven Architecture** | Kafka consumer com ACK manual | Desacoplamento e resiliência |
| **Dead Letter Queue** | `video.failed.v1.dlq` | Isolamento de mensagens problemáticas |
| **Retry Pattern** | `FixedBackOff` no `DefaultErrorHandler` | Tolerância a falhas transitórias |
| **Correlation ID** | `EventEnvelope.correlationId` | Rastreabilidade ponta a ponta |
| **Java Records** | `EventEnvelope`, `VideoFailedPayload` | Imutabilidade, menos boilerplate |

---

## 9. Stack Tecnológica

| Tecnologia | Versão | Função |
|---|---|---|
| Java | 21 | Linguagem principal (LTS) |
| Spring Boot | 4.0.2 | Framework de aplicação |
| Spring Kafka | (gerenciado pelo Boot) | Integração com Apache Kafka |
| Spring Actuator | (gerenciado pelo Boot) | Health checks e métricas |
| Spring Validation | (gerenciado pelo Boot) | Bean Validation JSR-303 |
| Jackson (tools.jackson) | (gerenciado pelo Boot) | Serialização/deserialização JSON |
| SLF4J + Logback | (gerenciado pelo Boot) | Logging estruturado |
| Maven | 3.9+ | Gerenciamento de dependências e build |
| Docker | 24+ | Containerização |
| Kubernetes | 1.28+ | Orquestração de containers |
| Kustomize | 5+ | Gerenciamento de configuração K8s |

---

## 10. Modelo de Configuração Externalizada

O serviço adota **configuração 12-Factor App**: todas as propriedades mutáveis são externalizadas via variáveis de ambiente, sem necessidade de rebuild da imagem.

```
application.yaml (defaults)
        │
        └── Variável de ambiente (override por ambiente)
                │
                ├── Docker Compose: environment section
                ├── Kubernetes: ConfigMap → envFrom/valueFrom
                └── Kubernetes: Secret → envFrom/valueFrom (credenciais)
```

| Propriedade (`application.yaml`) | Variável de Ambiente | Default |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.consumer.group-id` | `KAFKA_GROUP_ID` | `notification-service` |
| `notification.topics.videoFailed` | `TOPIC_VIDEO_FAILED` | `video.failed.v1` |
| `notification.topics.videoFailedDlq` | `TOPIC_VIDEO_FAILED_DLQ` | `video.failed.v1.dlq` |
| `notification.retry.max-attempts` | `NOTIF_RETRY_MAX_ATTEMPTS` | `3` |
| `notification.retry.backoff-ms` | `NOTIF_RETRY_BACKOFF_MS` | `1000` |

---

## 11. Observabilidade

### Health Checks (Spring Actuator)

| Endpoint | Probe K8s | Finalidade |
|---|---|---|
| `GET /actuator/health` | Startup Probe | Confirma que a app subiu |
| `GET /actuator/health/liveness` | Liveness Probe | App ainda está viva? |
| `GET /actuator/health/readiness` | Readiness Probe | App pronta para receber tráfego? |

### Logging Estruturado

Saída de log para evento processado:
```
ERROR c.f.h.adapters.out.Slf4jNotificationLoggerAdapter
      correlationId=req-abc123 NOTIFY Video failed:
      videoId=123e4567-e89b-12d3-a456-426614174000
      reason=ENCODING_FAILED
      details=Codec not supported: VP9
      eventId=550e8400-e29b-41d4-a716-446655440000
```

### Métricas Kafka (via Micrometer/Actuator)

Disponíveis em `/actuator/metrics`:
- `kafka.consumer.records-consumed-total`
- `kafka.consumer.records-lag`
- `kafka.consumer.fetch-rate`

---

## 12. Escalabilidade e Alta Disponibilidade

```
         Kafka Topic: video.failed.v1
         Partições: 3
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
 Pod-1     Pod-2     Pod-3      ← Consumer Group "notification-service"
 (réplica) (réplica) (réplica)  ← 1 partição por pod (máx)

 HPA: min=2, max=10 réplicas
 PDB: minAvailable=1
 Anti-affinity: pods distribuídos entre nodes
```

- **Escala horizontal**: até 3 réplicas consomem em paralelo (uma por partição)
- **Consumer group**: balanceamento automático de partições pelo Kafka
- **Stateless**: sem estado local, escala elástica sem coordenação
- **PDB**: garante disponibilidade mínima durante updates e manutenções

---

## 13. Segurança

| Prática | Implementação |
|---|---|
| Container não-root | `runAsUser: 1001` (`spring:spring`) |
| Filesystem somente leitura | `readOnlyRootFilesystem: true` |
| Sem escalonamento de privilégios | `allowPrivilegeEscalation: false` |
| Capabilities removidas | `capabilities.drop: [ALL]` |
| Secrets K8s | Credenciais Kafka separadas de ConfigMap |
| RBAC mínimo | ServiceAccount com permissão apenas em pods e configmaps |
| Signal handling | `dumb-init` como PID 1 para shutdown gracioso |
| JVM segura | `-Djava.security.egd=file:/dev/./urandom` |
