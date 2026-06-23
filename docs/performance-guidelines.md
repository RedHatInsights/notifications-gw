# Performance Guidelines

## Kafka Callback Timeout

- Prefer keeping `notifications.kafka-callback-timeout-seconds` at or below 60 seconds (the current default in `GwResource.java`); raising it holds the request thread longer during `callback.get()`, reducing throughput under Kafka backpressure.
- When changing this value in `clowdapp.yaml` via `NOTIFICATIONS_KAFKA_CALLBACK_TIMEOUT_SECONDS`, verify the downstream readiness probe (`initialDelaySeconds: 40`) still fires before any realistic timeout scenario.

## REST Client Retry Overhead

- All methods in `RestValidationClient` and `RestInternalClient` use `@Retry(maxRetries = 5)`, meaning a single failed backend call can trigger up to 6 total attempts. When combining `@Retry` with a long callback timeout, calculate worst-case latency: a 60-second callback timeout with 6 retry attempts could block threads for minutes.

## Bulk Caches vs Per-Request Validation

- When `notifications.bulk-caches.enabled` is `true`, `GwResource` validates event types against the in-memory `mapBaet` HashMap and matches source environments via a linear `stream().filter()` scan of the `sourceEnvironments` list. This avoids per-request REST calls to `notifications-backend` but trades off freshness.
- Avoid adding new per-request REST calls to `RestValidationClient` without wrapping them in `@CacheResult` or gating them behind the `isBulkCachesEnabled()` flag; unprotected calls add latency to every `POST /notifications` request.

## Source Environment Lookup

- The `getSourceEnvironment()` method in `GwResource.java` performs a linear scan over the `sourceEnvironments` list. If this list grows beyond a few hundred entries, prefer replacing it with a lookup `Map` keyed by a composite of `bundle + application + subjectDn`.

## Metrics Cardinality

- Avoid high-cardinality tags (e.g., org IDs, UUIDs) on Micrometer counters -- they create unbounded metric series and increase Prometheus scrape memory.

## Resource Limits

- The ClowdApp specifies `cpu: 500m` for both request and limit, `memory: 250Mi` request / `500Mi` limit (parameterized via `CPU_REQUEST`, `CPU_LIMIT`, `MEMORY_REQUEST`, `MEMORY_LIMIT`). When adding dependencies or caches that increase heap usage, update `MEMORY_LIMIT` in `clowdapp.yaml` and validate with a load test before merging.

## Verification

```bash
# Run all tests (includes Kafka in-memory connector and cache validation tests)
./mvnw clean package --no-transfer-progress

# Verify no high-cardinality metric tags (orgId, UUID) in counter creation
grep -rn "Tags.of\|counter(" src/main/java/ --include="*.java"

# Check Kafka callback timeout default
grep "kafka-callback-timeout" src/main/resources/application.properties src/main/java/com/redhat/cloud/notifications/GwResource.java

# Verify retry counts on REST clients
grep -n "@Retry" src/main/java/com/redhat/cloud/notifications/RestValidationClient.java src/main/java/com/redhat/cloud/notifications/RestInternalClient.java

# Check Kafka partition count
grep -A2 "platform.notifications.ingress" .rhcicd/clowdapp.yaml
```
