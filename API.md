# Hack VP Notification Service — Event API

> Documentação no estilo AsyncAPI dos contratos de eventos consumidos pelo serviço.
> O serviço é um **Kafka Consumer** puro — não expõe endpoints REST de negócio.

---

## Informações Gerais

| Campo | Valor |
|---|---|
| **Serviço** | `hack-vp-notification` |
| **Versão** | `0.0.1-SNAPSHOT` |
| **Protocolo** | Apache Kafka |
| **Padrão de Mensagem** | Event Envelope genérico + Payload específico |
| **Serialização** | JSON (UTF-8) |
| **Autenticação** | PLAINTEXT (dev) / SASL_SCRAM (produção) |

---

## Tópicos Kafka

| Tópico | Direção | Partições | Replicação | Descrição |
|---|---|---|---|---|
| `video.failed.v1` | **CONSUME** (entrada) | 3 | 1 (dev) / 3 (prod) | Eventos de falha de vídeo |
| `video.failed.v1.dlq` | **PRODUCE** (saída) | 1 | 1 (dev) / 3 (prod) | Dead Letter Queue — mensagens que falharam após todos os retries |

---

## Configuração do Consumer

```yaml
# Mapeamento direto das propriedades Spring Kafka
bootstrap.servers:      ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
group.id:               ${KAFKA_GROUP_ID:notification-service}
auto.offset.reset:      earliest
enable.auto.commit:     false
key.deserializer:       org.apache.kafka.common.serialization.StringDeserializer
value.deserializer:     org.apache.kafka.common.serialization.StringDeserializer
ack-mode:               manual
missing-topics-fatal:   false
```

**Observações:**
- `enable.auto.commit: false` — offset confirmado manualmente após processamento bem-sucedido
- `ack-mode: manual` — o listener chama `ack.acknowledge()` explicitamente
- `auto.offset.reset: earliest` — nova instância começa do início do tópico

---

## Evento: VideoFailed.v1

### Descrição

Evento publicado por sistemas externos quando ocorre uma falha no processamento de um vídeo. O serviço consome este evento, valida sua estrutura e registra a ocorrência via log estruturado.

### Tópico

```
video.failed.v1
```

### Schema Completo (OpenAPI / JSON Schema)

```yaml
# ──────────────────────────────────────────────
# EventEnvelope<VideoFailedPayload>
# Envelope padrão utilizado para todos os eventos
# ──────────────────────────────────────────────
EventEnvelope:
  type: object
  required:
    - eventName
    - eventId
    - occurredAt
    - correlationId
    - payload
  properties:

    eventName:
      type: string
      description: |
        Nome e versão do evento. Deve ser exatamente "VideoFailed.v1".
        Eventos com outro valor são rejeitados e enviados à DLQ.
      const: "VideoFailed.v1"
      example: "VideoFailed.v1"

    eventId:
      type: string
      format: uuid
      description: |
        Identificador único (UUID v4) do evento.
        Utilizado para idempotência e rastreabilidade.
      example: "550e8400-e29b-41d4-a716-446655440000"

    occurredAt:
      type: string
      format: date-time
      description: |
        Timestamp ISO 8601 (UTC) de quando o evento ocorreu no sistema de origem.
        Formato: "YYYY-MM-DDTHH:mm:ss.sssZ"
      example: "2024-02-23T10:30:00.000Z"

    correlationId:
      type: string
      minLength: 1
      description: |
        Identificador de correlação para rastreabilidade ponta a ponta.
        Deve ser propagado desde a requisição original.
        Incluído em todos os logs como "correlationId=<valor>".
      example: "req-abc123456789"

    payload:
      $ref: '#/components/schemas/VideoFailedPayload'

# ──────────────────────────────────────────────
# VideoFailedPayload
# Payload específico do evento VideoFailed.v1
# ──────────────────────────────────────────────
VideoFailedPayload:
  type: object
  required:
    - videoId
    - reason
  properties:

    videoId:
      type: string
      format: uuid
      description: |
        Identificador único (UUID v4) do vídeo que falhou.
        Usado para correlacionar a notificação com o recurso original.
      example: "123e4567-e89b-12d3-a456-426614174000"

    reason:
      type: string
      minLength: 1
      description: |
        Código de motivo da falha. String não-vazia obrigatória.
        Recomenda-se usar constantes padronizadas.
      example: "ENCODING_FAILED"
      x-suggested-values:
        - ENCODING_FAILED       # Falha no processo de encoding/transcodificação
        - UPLOAD_FAILED         # Falha no upload do arquivo de vídeo
        - VALIDATION_FAILED     # Arquivo inválido (formato, tamanho, codec)
        - STORAGE_ERROR         # Erro ao salvar no armazenamento
        - TIMEOUT               # Timeout durante o processamento
        - UNKNOWN_ERROR         # Erro não categorizado

    details:
      type: string
      nullable: true
      description: |
        Mensagem descritiva adicional sobre a falha.
        Campo opcional. Pode ser null ou ausente.
      example: "Codec VP9 not supported by encoder version 3.2.1"
```

---

## Exemplos de Mensagens

### Mensagem Válida — Sucesso

```json
{
  "eventName": "VideoFailed.v1",
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "occurredAt": "2024-02-23T10:30:00.000Z",
  "correlationId": "req-abc123456789",
  "payload": {
    "videoId": "123e4567-e89b-12d3-a456-426614174000",
    "reason": "ENCODING_FAILED",
    "details": "Codec VP9 not supported by encoder version 3.2.1"
  }
}
```

**Resultado:** Log estruturado + ACK do offset

```
ERROR c.f.h.adapters.out.Slf4jNotificationLoggerAdapter :
correlationId=req-abc123456789 NOTIFY Video failed:
videoId=123e4567-e89b-12d3-a456-426614174000
reason=ENCODING_FAILED
details=Codec VP9 not supported by encoder version 3.2.1
eventId=550e8400-e29b-41d4-a716-446655440000
```

---

### Mensagem Válida — Sem Campo `details`

```json
{
  "eventName": "VideoFailed.v1",
  "eventId": "661f9511-f3ac-52e5-b827-557766551111",
  "occurredAt": "2024-02-23T14:00:00.000Z",
  "correlationId": "req-xyz987654321",
  "payload": {
    "videoId": "234f5678-f9ac-23e4-b567-537725285111",
    "reason": "TIMEOUT"
  }
}
```

**Resultado:** Log com `details=null` + ACK do offset

---

### Mensagem Inválida — `eventName` incorreto → DLQ

```json
{
  "eventName": "VideoFailed.v2",
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "occurredAt": "2024-02-23T10:30:00.000Z",
  "correlationId": "req-invalid-name",
  "payload": {
    "videoId": "123e4567-e89b-12d3-a456-426614174000",
    "reason": "ENCODING_FAILED"
  }
}
```

**Resultado:** `IllegalArgumentException: Unexpected eventName=VideoFailed.v2` → Retry → DLQ

---

### Mensagem Inválida — `videoId` não é UUID → DLQ

```json
{
  "eventName": "VideoFailed.v1",
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "occurredAt": "2024-02-23T10:30:00.000Z",
  "correlationId": "req-invalid-payload",
  "payload": {
    "videoId": "nao-e-um-uuid",
    "reason": "ENCODING_FAILED"
  }
}
```

**Resultado:** `IllegalArgumentException: Invalid event envelope: [...]` → Retry → DLQ

---

### Mensagem Inválida — `reason` vazio → DLQ

```json
{
  "eventName": "VideoFailed.v1",
  "eventId": "550e8400-e29b-41d4-a716-446655440003",
  "occurredAt": "2024-02-23T10:30:00.000Z",
  "correlationId": "req-empty-reason",
  "payload": {
    "videoId": "123e4567-e89b-12d3-a456-426614174000",
    "reason": ""
  }
}
```

**Resultado:** Violação `@NotBlank` em `reason` → Retry → DLQ

---

## Regras de Validação

### EventEnvelope

| Campo | Constraint | Tipo | Obrigatório |
|---|---|---|---|
| `eventName` | `@NotBlank` + verificação manual `== "VideoFailed.v1"` | `String` | Sim |
| `eventId` | `@NotNull` | `UUID` | Sim |
| `occurredAt` | `@NotNull` | `Instant` (ISO 8601) | Sim |
| `correlationId` | `@NotBlank` | `String` | Sim |
| `payload` | `@NotNull @Valid` | `VideoFailedPayload` | Sim |

### VideoFailedPayload

| Campo | Constraint | Tipo | Obrigatório |
|---|---|---|---|
| `videoId` | `@NotNull` (UUID válido) | `UUID` | Sim |
| `reason` | `@NotBlank` | `String` | Sim |
| `details` | nenhuma | `String` | Não (nullable) |

---

## Comportamento de Processamento

| Cenário | Ação Imediata | Comportamento de Retry | Destino Final |
|---|---|---|---|
| Mensagem válida processada | Log ERROR + ACK | N/A | Offset commitado |
| JSON inválido (parse error) | `JsonProcessingException` | Retry 2x (FixedBackOff 1s) | `video.failed.v1.dlq` |
| Violação de validação Bean | `IllegalArgumentException` | Retry 2x (FixedBackOff 1s) | `video.failed.v1.dlq` |
| `eventName` incorreto | `IllegalArgumentException` | Retry 2x (FixedBackOff 1s) | `video.failed.v1.dlq` |
| Erro no UseCase | Qualquer `Exception` | Retry 2x (FixedBackOff 1s) | `video.failed.v1.dlq` |

---

## Dead Letter Queue — `video.failed.v1.dlq`

Mensagens que não puderam ser processadas após todos os retries são enviadas para a DLQ com os headers originais do Kafka acrescidos de:

| Header Kafka | Valor | Descrição |
|---|---|---|
| `kafka_dlt-original-topic` | `video.failed.v1` | Tópico de origem |
| `kafka_dlt-original-partition` | `<número>` | Partição de origem |
| `kafka_dlt-original-offset` | `<offset>` | Offset de origem |
| `kafka_dlt-exception-fqcn` | `java.lang.IllegalArgumentException` | Classe da exceção |
| `kafka_dlt-exception-message` | `<mensagem do erro>` | Mensagem da exceção |
| `kafka_dlt-exception-cause-fqcn` | `<classe causa>` | Causa raiz (se houver) |

> **Particionamento DLQ**: A mensagem vai para a mesma partição do tópico original (routing por `record.partition()`).

---

## Endpoints de Gerenciamento (Spring Actuator)

O serviço expõe endpoints de observabilidade na porta `8080`:

| Endpoint | Método | Descrição |
|---|---|---|
| `/actuator/health` | GET | Status geral da aplicação |
| `/actuator/health/liveness` | GET | Liveness probe (app viva?) |
| `/actuator/health/readiness` | GET | Readiness probe (pronta para tráfego?) |
| `/actuator/info` | GET | Informações da aplicação |
| `/actuator/metrics` | GET | Lista de métricas disponíveis |
| `/actuator/metrics/{name}` | GET | Métrica específica |

### Exemplo — `/actuator/health`

```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "nodes": ["localhost:9092"]
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

---

## Testando a API com Kafka CLI

### Publicar mensagem válida

```bash
echo '{
  "eventName":"VideoFailed.v1",
  "eventId":"550e8400-e29b-41d4-a716-446655440000",
  "occurredAt":"2024-02-23T10:30:00.000Z",
  "correlationId":"test-123",
  "payload":{
    "videoId":"123e4567-e89b-12d3-a456-426614174000",
    "reason":"ENCODING_FAILED",
    "details":"Test failure"
  }
}' | kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic video.failed.v1
```

### Monitorar a DLQ

```bash
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic video.failed.v1.dlq \
  --from-beginning \
  --property print.headers=true
```

### Verificar lag do consumer group

```bash
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group notification-service \
  --describe
```

### Via Docker Compose (ambiente local)

```bash
# Publicar mensagem de teste (profile testing)
docker compose --profile testing up test-producer

# Verificar logs do serviço
docker compose logs -f notification-service

# Acessar Kafka UI
open http://localhost:8090
```

---

## Configuração de Retry

```yaml
# application.yaml — Valores configuráveis via variáveis de ambiente
notification:
  retry:
    max-attempts: ${NOTIF_RETRY_MAX_ATTEMPTS:3}   # Total de tentativas (1 original + 2 retries)
    backoff-ms:   ${NOTIF_RETRY_BACKOFF_MS:1000}  # Intervalo em ms entre tentativas
```

| Parâmetro | Env Var | Default | Descrição |
|---|---|---|---|
| `max-attempts` | `NOTIF_RETRY_MAX_ATTEMPTS` | `3` | Total de tentativas (inclui a primeira) |
| `backoff-ms` | `NOTIF_RETRY_BACKOFF_MS` | `1000` | Intervalo fixo entre retries em ms |

**Cálculo interno:**
```java
// KafkaErrorHandlingConfig.java
long maxRetries = Math.max(0, maxAttempts - 1);
// maxAttempts=3 → maxRetries=2 → 1 tentativa original + 2 retries
var backOff = new FixedBackOff(backoffMs, maxRetries);
```

**Linha do tempo com defaults:**
```
t=0ms    → Tentativa 1 (original)  → FALHA
t=1000ms → Tentativa 2 (retry 1)   → FALHA
t=2000ms → Tentativa 3 (retry 2)   → FALHA
t=2001ms → Mensagem enviada para DLQ
```
