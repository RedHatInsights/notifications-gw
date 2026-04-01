# Performance Guidelines

## Architecture Overview

This is a Quarkus-based gateway that receives REST notifications, validates them against a backend service, and forwards them to Kafka. The critical path is: HTTP request -> validation -> Kafka produce -> synchronous ack wait.

## Caching

### Two Caching Strategies (Controlled by Feature Flag)

The `notifications.bulk-caches.enabled` flag switches between two validation modes:

1. **Per-request validation (default, flag=false):** Each request calls `RestValidationClient.validate()` which has its own `@CacheResult(cacheName = "baet-validation")` with 10-minute TTL. Certificate validation uses `@CacheResult(cacheName = "certificate-validation")` with 10-minute TTL. These caches are keyed by method parameters (bundle, application, eventType / certificateSubjectDn).

2. **Bulk cache mode (flag=true):** On startup and periodically, the full BAET map and certificate list are fetched via `refreshBaets()` and `refreshSourceEnvironment()`. These use `@CacheResult(cacheName = "get-baets")` and `@CacheResult(cacheName = "get-certificates")` with 1-hour TTL. Lookups then happen in-memory against `mapBaet` and `sourceEnvironments` fields.

### Cache Configuration Reference

All cache durations live in `application.properties`:
```properties
quarkus.cache.caffeine.baet-validation.expire-after-write=PT10M
quarkus.cache.caffeine.certificate-validation.expire-after-write=PT10M
quarkus.cache.caffeine.get-baets.expire-after-write=PT1H
quarkus.cache.caffeine.get-certificates.expire-after-write=PT1H
```

### Rules

- Never add a new cache without a corresponding `expire-after-write` entry in `application.properties`.
- Cache names in `@CacheResult` annotations must exactly match the Caffeine config keys.
- When testing cached methods, inject the `Cache` by name and call `invalidateAll().await().indefinitely()` in setup to ensure test isolation.
- The `@CacheResult` on `refreshBaets()` and `refreshSourceEnvironment()` means the method body only executes once per TTL window regardless of how many requests arrive. The boolean return value is what gets cached, but the side effect (populating `mapBaet` / `sourceEnvironments`) is the real purpose.

## Thread Safety

### Known Concerns

- `GwResource.mapBaet` and `GwResource.sourceEnvironments` are mutable instance fields on an `@ApplicationScoped` bean. They are reassigned (not mutated) from `refreshBaets()` and `refreshSourceEnvironment()`, which are guarded by `@CacheResult` TTL. The fields are not `volatile` and have no synchronization. This is acceptable in practice because Caffeine's cache provides a happens-before relationship for the thread that triggers refresh, and stale reads from other threads are tolerable (the old data remains valid).
- Do not switch these fields to concurrent mutation patterns (e.g., adding/removing entries). If you need to update them incrementally, introduce proper synchronization or use `ConcurrentHashMap`.
- The `ConcurrentModificationException` catch in the `forward()` method (around `response.readEntity()`) is a documented Quarkus bug workaround. Do not remove it without verifying the upstream fix.

## Kafka Producer

### Synchronous Callback Pattern

The gateway uses a `CompletableFuture` callback attached to each Kafka message to convert async Kafka production into a synchronous HTTP response:

```java
CompletableFuture<Void> callback = new CompletableFuture<>();
Message<String> message = buildMessageWithId(serializedAction, sourceEnvironment, callback);
emitter.send(message);
callback.get(callbackTimeout, TimeUnit.SECONDS);
```

### Rules

- The `callbackTimeout` defaults to 60 seconds (`notifications.kafka-callback-timeout-seconds`). This directly affects maximum HTTP response latency. Do not increase it without load testing.
- Every message gets a unique `rh-message-id` header (UUID v4). This is critical for tracing and deduplication downstream.
- The Kafka topic is `platform.notifications.ingress`, configured as the `egress` channel. The channel name "egress" refers to outbound from this gateway.

## REST Client Resilience

### Retry Configuration

All `RestValidationClient` and `RestInternalClient` methods use `@Retry(maxRetries = 5)` from MicroProfile Fault Tolerance. This means a failing backend call will be attempted 6 times total (1 initial + 5 retries).

### Rules

- Account for retry multiplication in capacity planning: a single inbound request can generate up to 6 backend calls if the backend is unhealthy.
- The REST client URL is resolved from Clowder config (`clowder.endpoints.notifications-backend-service.url`) with localhost fallback. Ensure Clowder config is present in deployed environments.

## Metrics

### Counter Conventions

Three Micrometer counters track request flow:

| Counter | When Incremented |
|---|---|
| `notifications.gw.received` | Every incoming POST to `/notifications` |
| `notifications.gw.forwarded` | After successful Kafka ack |
| `notifications.gw.failed.requests` | On validation or Kafka failure, tagged with `status_code` |

### Rules

- The failure counter uses dynamic tags (`status_code`). Be aware this creates a new time series per distinct HTTP status code. This is bounded in practice (400, 503) but do not add unbounded tag values.
- Counters are initialized in the constructor (`GwResource(MeterRegistry)`), not via `@PostConstruct`. Follow this pattern for counters that do not need dynamic tags.
- For counters with dynamic tags, use `meterRegistry.counter(name, Tags.of(...)).increment()` inline as done in `incrementFailuresCounter()`.

## Access Log Filtering

Health and metrics endpoints are filtered from access logs to reduce log volume:

```java
public static final String FILTER_REGEX = ".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?";
```

This filter is applied via `java.util.logging.Logger.setFilter()` on the `access_log` category. If you add new infrastructure endpoints that generate high-frequency traffic, add them to this regex.

## Request Processing Cost

A single `POST /notifications` in the default (non-bulk) mode performs:
1. Base64 decode + JSON deserialize of `x-rh-identity` header
2. Bean validation of the request body
3. REST call to backend for certificate validation (if X509/ServiceAccount identity)
4. REST call to backend for BAET validation (cached 10 min)
5. Action serialization via `Parser.encode()`
6. Kafka produce + synchronous ack wait (up to 60s)

In bulk mode, steps 3-4 are replaced by in-memory lookups against data refreshed every hour.

## Configuration Properties Summary

| Property | Default | Impact |
|---|---|---|
| `notifications.kafka-callback-timeout-seconds` | 60 | Max HTTP response time for forwarded messages |
| `notifications.bulk-caches.enabled` | false | Switches between per-request and bulk validation |
| `notifications.allow-list.enabled` | false | Enables org ID allow-listing for stage sources |
| `notifications.emails.internal-only.enabled` | true | Restricts recipient emails to `@redhat.com` |
