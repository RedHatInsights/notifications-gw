# Error Handling Guidelines

## HTTP Error Response Format

Use `GwResource.buildResponseEntity(boolean success, String details)` for all error responses in the gateway endpoint. This produces `{"result":"error","details":"..."}` JSON. Do not invent alternative response shapes.

```java
return Response
    .status(BAD_REQUEST)
    .header(CONTENT_TYPE, APPLICATION_JSON)
    .entity(buildResponseEntity(false, errorMessage))
    .build();
```

## Status Code Selection in GwResource.forward()

Map upstream `WebApplicationException` responses from `RestValidationClient` to the correct outbound status:
- Return `BAD_REQUEST` (400) only when `notifications-backend` itself returned 400 -- forward its error message verbatim.
- Return `SERVICE_UNAVAILABLE` (503) for all other backend status codes and for `ProcessingException` (backend unreachable). Use the generic message `"Unable to validate the message, please try again later"` to avoid leaking internal details.
- Return `FORBIDDEN` (403) for allow-list rejections from staging source environments.
- Kafka delivery failures (ack timeout, nack, any `Throwable`) produce `SERVICE_UNAVAILABLE` (503) with `"Message delivery to Kafka failed, please try again later"`.

## Failures Counter

Increment `notifications.gw.failed.requests` via `incrementFailuresCounter(Status)` only for backend validation errors (`WebApplicationException` and `ProcessingException`) and Kafka delivery failures. Do not increment the counter for: successful forwards, Bean Validation constraint violations (Quarkus handles those), allow-list rejections (FORBIDDEN), internal email check failures, or bulk-cache event-type-not-found rejections.

## Logging Levels for Errors

- Use `Log.debugf` when the backend returns 400 (client error, expected in normal operation).
- Use `Log.errorf` when the backend returns non-400 errors or is unreachable (`ProcessingException`).
- Use `Log.error` for Kafka delivery failures and cache refresh failures.
- Use `Log.warnf` for allow-list rejections and identity header deserialization failures.
- Prefer `Log.infof` (not error/warn) when authentication validation finds no matching certificate -- this is an expected path, not an error.

## Cache Refresh Error Handling

In `refreshBaets()` and `refreshSourceEnvironment()`, catch `Exception` broadly, log with `Log.error`, and return `false`. The caller in `NotificationsGwApp.init()` checks the boolean and throws `IllegalStateException` if bulk caches fail at startup. At runtime, stale cached data is preserved on refresh failure -- do not clear the existing `mapBaet` or `sourceEnvironments` list on error.

## Custom Timestamp Validation

`TimestampValidator` catches `Exception` broadly from `LocalDateTime.parse()` and returns `false` (invalid). Do not throw from custom `ConstraintValidator.isValid()` implementations -- return `false` and let the Bean Validation framework produce the error response.

## Identity Header Errors

`HeaderHelper.getRhIdFromString()` returns `Optional.empty()` on any deserialization failure. The auth mechanism in `RHIdAuthMechanism` falls back to subject `"-unset-"` and type `"-unset-"` when the header is missing or malformed. Do not throw exceptions from the auth pipeline for bad identity headers.

## Error Handling in getSourceEnvironment()

When not using bulk caches, catch `Exception` broadly from `restValidationClient.validateCertificate()` and return `Optional.empty()`. Log at `info` level -- certificate validation failure is not an error condition, it means the source environment is unknown.

## Avoid in Error Handling

- Avoid using `System.err.println` for error output -- use `io.quarkus.logging.Log` instead. (`TimestampValidator` is the sole existing violation of this.)
- Avoid catching specific exception subclasses in cache refresh methods -- the broad `Exception` catch ensures no unexpected exception type crashes the refresh cycle.
- Avoid returning raw exception messages from backend services to the caller when the status is not 400 -- use the generic "try again later" message.

## Verification

```bash
# Run all tests including error-handling scenarios
./mvnw test --no-transfer-progress

# Check for System.err/System.out usage (prefer Log)
grep -rn "System\.err\|System\.out" src/main/java/

# Verify all error responses use buildResponseEntity
grep -rn "buildResponseEntity" src/main/java/com/redhat/cloud/notifications/GwResource.java

# Verify failure counter is incremented on error paths
grep -rn "incrementFailuresCounter" src/main/java/com/redhat/cloud/notifications/GwResource.java
```
