# Fault Tolerance Guidelines

## REST Client Retry Policy

- Annotate every method on `RestValidationClient` and `RestInternalClient` with `@Retry(maxRetries = 5)` from `org.eclipse.microprofile.faulttolerance.Retry`. This is the standard retry count across all REST client calls in this gateway.
- Avoid adding `@Timeout`, `@CircuitBreaker`, or `@Fallback` annotations to REST client interfaces; this codebase relies solely on `@Retry` for MicroProfile Fault Tolerance on outbound REST calls.
- When adding a new method to either REST client interface, include `@Retry(maxRetries = 5)` on the method declaration, not at the class level.

## Kafka Delivery Callback Pattern

- Catch `Throwable` (not a specific exception) from the `callback.get(callbackTimeout, TimeUnit.SECONDS)` call; both `ExecutionException` (Kafka nack) and `TimeoutException` (expired timeout) result in an HTTP 503 response with message `"Message delivery to Kafka failed, please try again later"`.

## Backend Validation Error Handling

- When `RestValidationClient.validate()` throws `WebApplicationException`, distinguish the response status: return HTTP 400 to the caller if the backend returned 400 (passing through the backend error message), and return HTTP 503 for all other backend error codes with a generic retry message.
- When `RestValidationClient.validate()` throws `ProcessingException` (backend unreachable), return HTTP 503 with message `"Unable to validate the message, please try again later"`.
- Increment the `notifications.gw.failed.requests` counter with a `status_code` tag on every failure path. Use `incrementFailuresCounter(Status)` in `GwResource` to keep counter tagging consistent.

## Caffeine Cache Refresh Behavior

- `refreshBaets()` and `refreshSourceEnvironment()` in `GwResource` are annotated with `@CacheResult(cacheName = "get-baets")` and `@CacheResult(cacheName = "get-certificates")` respectively. On failure these methods return `false` and catch `Exception` broadly -- the existing instance fields `mapBaet` and `sourceEnvironments` are not cleared, so in-flight requests continue using the previously loaded data until the next successful refresh.
- Avoid clearing `mapBaet` or `sourceEnvironments` on refresh failure; resetting them would leave the gateway with no data to validate against.

## Startup Initialization

- When `notifications.bulk-caches.enabled` is true, `NotificationsGwApp.init()` calls `gwResource.init()` which loads both BAET and certificate caches. If either cache fails to load, the app throws `IllegalStateException` to prevent startup with empty caches.
- When `notifications.bulk-caches.enabled` is false (default), no startup cache loading occurs and validation calls go directly to `notifications-backend` per request.

## Verification

```bash
# Confirm all REST client methods have @Retry(maxRetries = 5)
grep -n "@Retry" src/main/java/com/redhat/cloud/notifications/RestValidationClient.java src/main/java/com/redhat/cloud/notifications/RestInternalClient.java

# Confirm cache refresh methods use @CacheResult and do not clear instance fields on exception
grep -n "CacheResult\|mapBaet\|sourceEnvironments\|catch" src/main/java/com/redhat/cloud/notifications/GwResource.java

# Confirm failure counter is incremented on all error paths
grep -n "incrementFailuresCounter" src/main/java/com/redhat/cloud/notifications/GwResource.java

# Confirm startup guard throws IllegalStateException when bulk caches fail to load
grep -n "IllegalStateException\|gwResource.init" src/main/java/com/redhat/cloud/notifications/NotificationsGwApp.java

# Run the full test suite to validate fault-tolerance behavior
./mvnw test
```
