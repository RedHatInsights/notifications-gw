# Error Handling Guidelines

## 1. Response Format

All error responses MUST use the standard JSON envelope built by `GwResource.buildResponseEntity()`:

```json
{"result": "error", "details": "<human-readable message>"}
```

Success responses use the same shape with `"result": "success"` and no `details` field. Always set the `Content-Type: application/json` header explicitly on error responses.

## 2. HTTP Status Code Selection

Use exactly three error status codes, chosen by origin of the failure:

| Status | When to use |
|--------|-------------|
| **400 Bad Request** | Validation failures originating from the caller's payload, OR when the notifications-backend itself returned 400. Propagate the backend's error message directly. |
| **403 Forbidden** | The allow-list rejected the request (staging source + unlisted org ID). |
| **503 Service Unavailable** | Any non-400 error from the notifications-backend, any `ProcessingException` (backend unreachable), any Kafka delivery failure (nack or timeout). Always use a generic message -- never expose internal details. |

Do NOT use 404, 500, or other status codes from `GwResource`. For non-400 backend errors, always map to 503.

## 3. Validation

### 3.1 Jakarta Bean Validation (declarative)

Input validation is declarative on the `RestAction` and `RestRecipient` model classes. The framework returns 400 automatically for constraint violations before the resource method runs.

- Field-name patterns: `@Pattern(regexp = "[a-z][a-z_0-9-]*")` on `bundle`, `application`, `eventType`.
- Nullability: `@NotNull @NotEmpty` on required string fields.
- Numeric strings: `@Positive` on `accountId` and `orgId` (stored as `String` to allow leading zeros).
- Email format: `@Pattern` on individual list elements in `RestRecipient.emails`.
- Timestamp: Custom `@ISO8601Timestamp` annotation backed by `TimestampValidator`, which parses with `DateTimeFormatter.ISO_LOCAL_DATE_TIME`.
- Use `@Valid` on nested objects (e.g., `RestAction.recipients`) to cascade validation.

### 3.2 Business validation (imperative, in GwResource.forward)

After bean validation passes, `forward()` performs additional checks in this order:

1. **Allow-list check** -- staged source environments must target a whitelisted org ID.
2. **Internal-email-only check** -- when `internalEmailsOnly` is enabled, all emails must end with `@redhat.com`.
3. **Event type validation** -- either via the bulk-cache map lookup or a REST call to notifications-backend.

Each check returns a `Response` directly on failure; it does NOT throw an exception.

## 4. REST Client Error Handling

### 4.1 Fault tolerance on REST clients

All methods on `RestValidationClient` and `RestInternalClient` are annotated with `@Retry(maxRetries = 5)` via MicroProfile Fault Tolerance. Do not add retry logic in the calling code; the client interface handles it.

### 4.2 Catching REST client exceptions in GwResource

When calling `restValidationClient.validate()` without bulk caches, catch two exception types in this order:

1. **`WebApplicationException`** -- the backend responded with an HTTP error. Extract the response status and entity. If status is 400, relay the backend's message with 400. Otherwise, return 503 with a generic message.
2. **`ProcessingException`** -- the backend was unreachable. Return 503 with a generic message.

When reading the response entity from a `WebApplicationException`, wrap the call in a secondary try/catch for `ConcurrentModificationException` (Quarkus bug workaround).

### 4.3 Swallowed exceptions in cache-refresh and certificate-validation paths

`refreshBaets()`, `refreshSourceEnvironment()`, and `getSourceEnvironment()` catch `Exception` broadly, log the error, and return a fallback value (`false`, `Optional.empty()`, or stale cached data). This is intentional -- these failures must not block the main request flow.

## 5. Kafka Delivery Errors

The `emitter.send(message)` call uses a `CompletableFuture` callback pattern:

- `callback.get(callbackTimeout, TimeUnit.SECONDS)` blocks until Kafka acks or nacks.
- Any `Throwable` (including `ExecutionException` from nack and `TimeoutException`) is caught, logged at ERROR, and mapped to 503.
- The timeout is configurable via `notifications.kafka-callback-timeout-seconds` (default: 60).

Always catch `Throwable` (not `Exception`) for the Kafka path, matching the existing convention.

## 6. Logging Conventions

Use `io.quarkus.logging.Log` (static import) exclusively. Do NOT use `java.util.logging`, SLF4J, or `System.err` in production code.

### Log levels by error category

| Level | Usage |
|-------|-------|
| `Log.errorf` | Backend returned non-400 error, Kafka delivery failed, cache refresh failed. Always include the exception object. |
| `Log.warnf` | Allow-list rejection, identity header deserialization failure. |
| `Log.debugf` | Backend returned 400 (caller's fault), event type not found in cache. |
| `Log.infof` | Successful operations -- cache refreshes, authentication validation results. |

### Log message conventions

- Use `f`-suffix methods (`Log.errorf`, `Log.warnf`, `Log.debugf`) with `%s`/`%d` format specifiers -- not string concatenation.
- Include structured context in brackets: `[bundle=%s, application=%s, eventType=%s]`.
- For validation logging, include the full `RestAction` via its `toString()` (which outputs JSON).
- When logging with an exception using `errorf`: pass it as the first argument: `Log.errorf(exception, "message %s", arg)`.
- When logging with an exception using `error`: pass it as the second argument: `Log.error("message", exception)`.

## 7. Metrics for Errors

Increment the `notifications.gw.failed.requests` counter (via `incrementFailuresCounter()`) for error responses from REST client failures and Kafka delivery failures. This includes both 400 errors from backend validation and 503 errors. Tag with `status_code`. The counter is NOT incremented for 400 errors from local validation (bulk cache mode, internal email check) or 403 errors from allow-list rejection.

## 8. Security: Log Injection Prevention

The `IncomingRequestInterceptor` sanitizes URI strings in log output using `ANTI_INJECTION_PATTERN` which strips `\n`, `\r`, and `\t`. Apply the same pattern when logging any externally-sourced string at TRACE level.

## 9. Startup Failure

When `bulkCachesEnabled` is true and cache initialization fails, `NotificationsGwApp.init()` throws `IllegalStateException` to prevent the application from starting in a degraded state. This is a fail-fast pattern -- do not catch this exception.

## 10. Test Conventions for Error Cases

- Mock `RestValidationClient` with `@InjectMock @RestClient` and throw `WebApplicationException` to simulate backend errors.
- Test all non-400 backend status codes (500, 501, 502, 503, 504, 507) and assert they all map to 503 from the gateway.
- Assert both the HTTP status code and the full JSON response body (`result` + `details` fields).
- Use `InMemoryConnector` / `InMemorySink` for Kafka, avoiding real broker dependencies.
