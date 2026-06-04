# Quarkus Guidelines

## Configuration

- Define all custom config properties with the `notifications.` prefix in `src/main/resources/application.properties` (e.g. `notifications.allow-list.enabled`, `notifications.kafka-callback-timeout-seconds`).
- Inject config values using `@ConfigProperty(name = "...", defaultValue = "...")` on fields, not constructor parameters. Follow the existing pattern in `GwConfig.java` and `GwResource.java`.
- Use the `%test.` profile prefix in `application.properties` for test-only overrides (e.g. `%test.quarkus.log.category."com.redhat.cloud.notifications".level=DEBUG`).
- Override Kafka connectors for tests in `src/test/resources/application.properties` by switching to `smallrye-in-memory` (e.g. `mp.messaging.outgoing.egress.connector=smallrye-in-memory`).
- Set the REST client base URL via the `configKey` pattern: `quarkus.rest-client.notifications-backend.url` -- avoid hardcoded URLs outside of properties files.

## CDI and Bean Scoping

- Prefer `@ApplicationScoped` for resource classes, config holders, and REST clients. This repo does not use `@RequestScoped`.
- REST client interfaces (e.g. `RestValidationClient`, `RestInternalClient`) require `@ApplicationScoped` alongside `@RegisterRestClient` to enable mocking in tests.
- Use `@Inject` field injection for all CDI beans: `MeterRegistry`, `Emitter`, config classes, and REST clients. In `GwResource`, the constructor receives `MeterRegistry` only to initialize counters at construction time -- the field-level `@Inject` on `meterRegistry` is still the CDI injection point.

## REST Endpoints

- Use Quarkus RESTEasy Reactive annotations (`quarkus-rest-jackson`), not the classic RESTEasy stack. Import from `jakarta.ws.rs.*`.
- Use `@RestQuery` from `org.jboss.resteasy.reactive` for query parameters. Path parameters use `@PathParam` from `jakarta.ws.rs` (as in `GwResource.getOrgSubscriptions`); use `@RestPath` from `org.jboss.resteasy.reactive` when writing new REST client interface methods (as in `RestInternalClient`).
- Annotate endpoints with MicroProfile OpenAPI annotations (`@Operation`, `@APIResponse`, `@APIResponses`) documenting each status code the endpoint can return.
- Return `jakarta.ws.rs.core.Response` directly from endpoint methods. Build JSON response bodies using `io.vertx.core.json.JsonObject` with `result` and `details` fields (see `buildResponseEntity` in `GwResource.java`).

## REST Clients

- Define REST clients as interfaces annotated with `@RegisterRestClient(configKey = "notifications-backend")` and `@Path("/internal/...")`.
- Apply `@Retry(maxRetries = 5)` from MicroProfile Fault Tolerance on each REST client method to handle transient backend failures.
- Pair individual-call validation methods with bulk cache methods (e.g. `validate` vs `getBaets`, `validateCertificate` vs `getCertificates`) and guard usage behind a feature flag like `notifications.bulk-caches.enabled`.

## Caching

- Use `@CacheResult(cacheName = "...")` from `io.quarkus.cache` for caching REST client responses and bulk data refreshes.
- Cache names in this repo: `baet-validation`, `certificate-validation`, `get-baets`, `get-certificates`. Configure TTLs in `application.properties` with `quarkus.cache.caffeine.<name>.expire-after-write`.
- Invalidate caches in tests using `@CacheName("...") Cache` injection and `cache.invalidateAll().await().indefinitely()`.

## Validation

- Apply Bean Validation annotations (`@NotNull`, `@NotEmpty`, `@Pattern`, `@Positive`, `@Valid`) on `RestAction` and `RestRecipient` fields. The `bundle`, `application`, and `eventType` fields enforce `[a-z][a-z_0-9-]*`.
- Use `@JsonProperty("snake_case")` on fields that differ between Java naming and JSON wire format (e.g. `@JsonProperty("event_type") public String eventType`).
- Custom constraint validators (e.g. `@ISO8601Timestamp` / `TimestampValidator`) validate timestamps against `DateTimeFormatter.ISO_LOCAL_DATE_TIME`.

## Startup and Lifecycle

- Observe `StartupEvent` in `NotificationsGwApp.init()` to perform initialization tasks: access log filtering, configuration logging, and eager cache loading when `bulkCachesEnabled` is true.
- Throw `IllegalStateException` from startup if critical caches fail to load to prevent the application from starting in a broken state.

## Observability

- Register Micrometer counters via `MeterRegistry` for received actions (`notifications.gw.received`), forwarded actions (`notifications.gw.forwarded`), and failures (`notifications.gw.failed.requests` with a `status_code` tag).
- Prefer `io.quarkus.logging.Log` static methods (`Log.infof`, `Log.errorf`, `Log.warnf`) over injected loggers.
- Non-application endpoints (health, metrics, OpenAPI) are served at the root path (`quarkus.http.non-application-root-path=/`), not under `/q/`.

## Verification

```bash
# Build and run all tests
./mvnw clean package --no-transfer-progress

# Run tests only (skip packaging)
./mvnw test

# Run a specific test class
./mvnw test -Dtest=GwResourceTest

# Verify the application compiles
./mvnw compile
```
