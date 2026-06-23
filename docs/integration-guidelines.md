# Integration Guidelines

## Kafka Message Production

- Send all notification messages through the `egress` channel defined in `GwResource.EGRESS_CHANNEL`, which maps to the `platform.notifications.ingress` Kafka topic.
- Attach a `rh-message-id` header containing a random UUID v4 to every outgoing Kafka message via `OutgoingKafkaRecordMetadata` (see `buildMessageWithId` in `GwResource`).
- When the caller is authenticated via X509 or ServiceAccount identity and a matching `SourceEnvironment` is found, attach a `rh-source-environment` header to the Kafka message.
- Use the `CompletableFuture` ack/nack callback pattern with a configurable timeout (`notifications.kafka-callback-timeout-seconds`, default 60s) to confirm Kafka delivery before responding to the HTTP caller.

## REST Client Registration

- Register REST clients with `@RegisterRestClient(configKey = "notifications-backend")` and scope them `@ApplicationScoped` so they can be mocked in tests.
- Validate event types against `notifications-backend` via `RestValidationClient.validate()` (per-request mode) or `RestValidationClient.getBaets()` (bulk cache mode, controlled by `notifications.bulk-caches.enabled`).

## Path Routing

- The gateway accepts POST requests at both `/notifications/` (direct) and `/api/notifications-gw/notifications` (ephemeral environments). The `IncomingRequestInterceptor` rewrites the latter path to `/notifications` before routing.
- Prefer testing both paths when modifying the main endpoint (see the `@ParameterizedTest` in `GwResourceTest.testNotificationsEndpoint`).

## Verification

```bash
# Run all tests
./mvnw clean test --no-transfer-progress

# Build the full package (compile + test)
./mvnw clean package --no-transfer-progress

# Run a single test class
./mvnw test -Dtest=GwResourceTest --no-transfer-progress

# Run the AllowListTest specifically
./mvnw test -Dtest=AllowListTest --no-transfer-progress
```
