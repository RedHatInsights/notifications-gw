# Integration Guidelines

## Architecture Overview

notifications-gw is a Quarkus gateway that receives REST notification payloads, validates them against notifications-backend, and forwards them to Kafka for downstream processing. It has a single core flow: `POST /notifications/` -> validate -> serialize -> Kafka produce.

## Kafka Messaging

### Topic and Channel

- The sole outgoing Kafka channel is named `egress`, mapped to topic `platform.notifications.ingress`.
- Reference the channel constant `GwResource.EGRESS_CHANNEL` ("egress") rather than hardcoding the string.
- The Clowdapp declares this topic with 3 partitions and 3 replicas.

### Producer Pattern

- Use `@Channel(EGRESS_CHANNEL) Emitter<String>` for sending messages. The payload is always a `String` (JSON-encoded Action).
- Serialization uses `StringSerializer` for both key and value -- never use `JsonObjectSerializer`.
- Every message must carry a `rh-message-id` Kafka header set to a random UUID v4 encoded as UTF-8 bytes.
- Optionally attach a `rh-source-environment` header when the sender is identified as originating from a non-production source environment.
- Build messages via `OutgoingKafkaRecordMetadata` to attach headers:

```java
Headers headers = new RecordHeaders().add(MESSAGE_ID_HEADER, messageId);
OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
        .withHeaders(headers).build();
Message.of(payload).addMetadata(metadata).withAck(...).withNack(...);
```

### Ack/Nack Callback

- Every Kafka send must use a `CompletableFuture<Void>` callback wired through `withAck`/`withNack` on the `Message`.
- Block on `callback.get(callbackTimeout, TimeUnit.SECONDS)` -- the timeout defaults to 60s and is configurable via `notifications.kafka-callback-timeout-seconds`.
- On any callback failure (nack or timeout), return HTTP 503 with `{"result":"error","details":"..."}`.

## Message Serialization

- Incoming REST payloads use `RestAction` (snake_case JSON via `@JsonProperty`). Outgoing Kafka payloads use the `Action` type from the `insights-notification-schemas-java` library.
- Convert `RestAction` -> `Action` using the builder pattern (`Action.ActionBuilder`), then serialize with `Parser.encode(action)`.
- Never send `RestAction` directly to Kafka. The `Action` schema is the contract with downstream consumers.
- Timestamps must be ISO 8601 local date-time format (e.g. `2020-12-18T17:04:04.417921`), validated by the custom `@ISO8601Timestamp` annotation and `TimestampValidator`.

## REST Action Payload Validation

### Field Constraints

- `bundle`, `application`, `event_type`: required, must match `[a-z][a-z_0-9-]*` (lowercase, starts with letter).
- `org_id`: required, must be a positive number (String type to allow leading zeros).
- `account_id`: optional, positive number when present.
- `events`: required, each event must have a non-empty `payload` map.
- `recipients.emails`: validated against `^\S+@\S+\.\S+$`. When `notifications.emails.internal-only.enabled` is true, only `@redhat.com` addresses are allowed.

### Bundle/Application/EventType Validation

Two modes controlled by `notifications.bulk-caches.enabled`:
- **Disabled (default)**: Calls `RestValidationClient.validate()` per request against notifications-backend (`/internal/validation/baet`).
- **Enabled**: Loads the full bundle/application/event-type map via `RestValidationClient.getBaets()` into an in-memory cache (1-hour TTL) and validates locally.

## REST Client Integration

### Client Declaration

- REST clients use `@RegisterRestClient(configKey = "notifications-backend")` and must be `@ApplicationScoped` (required for test mocking).
- Base URL configured via `quarkus.rest-client.notifications-backend.url`, which resolves from Clowder in deployed environments.

### Fault Tolerance

- All REST client methods must use `@Retry(maxRetries = 5)` from MicroProfile Fault Tolerance.
- Validation responses from backend: HTTP 400 is forwarded as 400 to caller; all other error codes are mapped to HTTP 503.
- `ProcessingException` (backend unreachable) also maps to HTTP 503.

### Caching

- Per-request validation results are cached with Caffeine: `baet-validation` and `certificate-validation` caches (10-minute TTL).
- Bulk caches (`get-baets`, `get-certificates`) have a 1-hour TTL.
- Cache names are referenced via `@CacheResult(cacheName = "...")` on the client methods or resource methods.

## Authentication

- All requests require a Base64-encoded `x-rh-identity` header containing a JSON identity object.
- Supported identity types: `X509` (with `subject_dn` and `issuer_dn`), `ServiceAccount`, and `Associate`.
- The `RHIdAuthMechanism` decodes the header and creates an `RhIdPrincipal` with `subject` and `type`.
- Source environment lookup (stage vs. prod) only applies to `X509` and `ServiceAccount` identity types.

## Allow List (Stage Environment Gating)

- When `notifications.allow-list.enabled=true`, messages from `stage` source environments are rejected unless the `org_id` appears in `notifications.allow-list.org-ids`.
- Rejected messages return HTTP 403.

## Response Format

All responses from the gateway use a consistent JSON structure:

```json
{"result": "success|error", "details": "optional error message"}
```

Build responses using `JsonObject` from Vert.x -- do not use Jackson for gateway responses.

## Integration Testing Conventions

### Test Infrastructure

- Tests use `@QuarkusTest` with `@QuarkusTestResource(TestLifecycleManager.class)`.
- `TestLifecycleManager` starts MockServer for backend simulation and switches Kafka to in-memory via `InMemoryConnector.switchOutgoingChannelsToInMemory(EGRESS_CHANNEL)`.

### Kafka Message Verification

- Access sent messages via `InMemorySink<String>`: inject `@Any InMemoryConnector`, then `connector.sink(EGRESS_CHANNEL)`.
- Wait for messages with Awaitility: `await().atMost(Duration.ofSeconds(10L)).until(() -> sink.received().size() > 0)`.
- Clear the sink in `@BeforeEach` to isolate tests.
- Verify Kafka headers via `message.getMetadata(KafkaMessageMetadata.class)`.

### Request Path Testing

Test both `/notifications/` (direct) and `/api/notifications-gw/notifications` (3scale path used in production/stage). The `IncomingRequestInterceptor` rewrites the latter to the former in ephemeral environments where 3scale is absent.

## Metrics

- `notifications.gw.received`: counter incremented on every incoming request.
- `notifications.gw.forwarded`: counter incremented on successful Kafka send.
- `notifications.gw.failed.requests`: counter with `status_code` tag, incremented on validation or delivery failures.

## Configuration Reference

| Property | Default | Purpose |
|---|---|---|
| `notifications.kafka-callback-timeout-seconds` | 60 | Kafka ack/nack wait timeout |
| `notifications.emails.internal-only.enabled` | true | Restrict recipient emails to @redhat.com |
| `notifications.allow-list.enabled` | false | Enable org ID allowlist for stage sources |
| `notifications.allow-list.org-ids` | [] | Allowed org IDs for stage sources |
| `notifications.bulk-caches.enabled` | false | Use bulk in-memory caches instead of per-request validation |
