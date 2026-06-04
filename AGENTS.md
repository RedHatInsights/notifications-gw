# AGENTS.md

## Cross-Cutting Conventions

- **Java 21, Quarkus 3.x, Maven wrapper only.** Always use `./mvnw`, never system `mvn`. The project is a single-module Maven build with no submodules.
- **Package root:** `com.redhat.cloud.notifications`. Sub-packages are `auth` (identity/authentication) and `model` (DTOs shared with notifications-backend). New REST model POJOs go in the root package.
- **No wildcard imports.** `.editorconfig` sets `ij_java_names_count_to_use_import_on_demand = 999` to force explicit imports.
- **snake_case JSON, camelCase Java.** Use `@JsonProperty("snake_case_name")` on fields whose wire name differs from the Java name. The canonical examples are `event_type`, `org_id`, `account_id`.
- **Config prefix:** All application-specific properties use `notifications.*`. Quarkus-managed properties keep their standard prefixes. Every `@ConfigProperty` must have a `defaultValue`.
- **Logging:** Use `io.quarkus.logging.Log` static methods (`Log.infof`, `Log.errorf`, etc.) with `%s` format placeholders. Never use SLF4J or `java.util.logging` in application code.
- **Metrics prefix:** `notifications.gw.*` for all Micrometer counters. Register counters via `MeterRegistry` constructor injection. Avoid high-cardinality tags (no org IDs or UUIDs as tag values).
- **CDI scope:** `@ApplicationScoped` everywhere. This repo does not use `@RequestScoped`. REST client interfaces require both `@ApplicationScoped` and `@RegisterRestClient`.
- **Fault tolerance:** `@Retry(maxRetries = 5)` on every REST client method. No `@Timeout`, `@CircuitBreaker`, or `@Fallback` in this codebase.
- **Response envelope:** All `POST /notifications/` responses use `{"result":"success"}` on success or `{"result":"error","details":"..."}` on failure, built via the private `buildResponseEntity()` helper in `GwResource`. Do not invent alternative shapes.

## Architecture Context

This is a **gateway microservice** — it receives notification payloads via REST, validates them against `notifications-backend`, and forwards them to Kafka (`platform.notifications.ingress`). There is exactly one Kafka producer channel (`egress`) and no consumers. The gateway does not own any database or persistent state.

Authentication uses a custom `HttpAuthenticationMechanism` that decodes the `x-rh-identity` Base64 header. Three identity types exist (X509, Associate/SAML, ServiceAccount) via Jackson polymorphic deserialization on the `Identity` class. Adding a new identity type requires a `@JsonSubTypes.Type` entry in `Identity.java`.

Two validation modes exist, controlled by `notifications.bulk-caches.enabled`: per-request REST calls to `notifications-backend` (default) or locally cached bulk data with Caffeine TTLs. Both paths must be tested.

## Running the Test Suite

```bash
# Full build + all tests (matches CI)
./mvnw clean package --no-transfer-progress

# Tests only (faster iteration)
./mvnw test

# Single test class
./mvnw test -Dtest=GwResourceTest

# Single test method
./mvnw test -Dtest=GwResourceTest#testNotificationsEndpoint

# Enable MockServer logging for debugging
./mvnw test -Dmockserver.logLevel=WARN
```

Tests require no external services. `TestLifecycleManager` starts MockServer and switches Kafka to an in-memory connector automatically. Docker is not needed for tests.

## Common Pitfalls

1. **Forgetting to test both bulk-cache paths.** `GwResource.forward()` branches on `isBulkCachesEnabled()`. Tests must cover both `true` and `false` via `@ParameterizedTest @ValueSource(booleans = {false, true})` or `@InjectSpy` with `when(gwConfig.isBulkCachesEnabled()).thenReturn(...)`.

2. **Timestamp format rejects timezones.** The `@ISO8601Timestamp` validator only accepts `DateTimeFormatter.ISO_LOCAL_DATE_TIME` (e.g., `2020-12-18T17:04:04.417921`). Appending `Z` or a timezone offset causes validation failure. Test payloads must omit timezone suffixes.

3. **Cache state leaks between tests.** Inject caches with `@CacheName("get-baets")` and call `cache.invalidateAll().await().indefinitely()` in `@BeforeEach` or before assertions that depend on fresh data. Without this, a previous test's cached value silently affects the next test.

4. **Adding a REST client method without `@Retry`.** Every method on `RestValidationClient` and `RestInternalClient` must have `@Retry(maxRetries = 5)`. The annotation goes on the method, not the class.

5. **Path rewriting in ephemeral environments.** `IncomingRequestInterceptor` rewrites `/api/notifications-gw/notifications` to `/notifications`. When modifying the main endpoint, test both paths (see `@ValueSource(strings = {...})` in `GwResourceTest`).

6. **Non-application root path is `/`, not `/q/`.** Health, metrics, and OpenAPI are served at `/health/*`, `/metrics`, `/openapi.json` — not under `/q/`. This is set by `quarkus.http.non-application-root-path=/` in `application.properties`.

7. **Port mismatch between dev and prod.** Dev uses port 8086 (`application.properties`), production uses 8000 (`clowdapp.yaml` via `QUARKUS_HTTP_PORT`). The Dockerfile exposes 8080 but the ClowdApp overrides it. Do not change one without updating the others.

8. **Log injection from user input.** Sanitize user-controlled strings before logging using the `ANTI_INJECTION_PATTERN` (strips `\n`, `\r`, `\t`) from `IncomingRequestInterceptor`. Apply the same pattern in new log statements that include request-derived data.

## PR Expectations

- Run `./mvnw clean package --no-transfer-progress` and confirm all tests pass before submitting.
- New REST client methods need `@Retry(maxRetries = 5)`.
- New `@ConfigProperty` fields need a `defaultValue` and a log line in `GwConfig.logConfiguration()`.
- New `@CacheResult` caches need a matching `quarkus.cache.caffeine.<name>.expire-after-write` entry in `application.properties`.
- New optional fields on `RestAction` must not add `@NotNull` to avoid breaking existing callers.

## Detailed Guidelines Index

Read the relevant guideline before working in that domain:

- [docs/api-contracts-guidelines.md](docs/api-contracts-guidelines.md) — RestAction payload format, response envelope, authentication headers, and adding new fields
- [docs/async-and-messaging-guidelines.md](docs/async-and-messaging-guidelines.md) — Kafka producer pattern, ack/nack callbacks, message headers, and in-memory testing
- [docs/code-organization-guidelines.md](docs/code-organization-guidelines.md) — Package structure, REST resource conventions, bean validation, caching, and test patterns
- [docs/configuration-guidelines.md](docs/configuration-guidelines.md) — Application properties, GwConfig bean, environment variable mapping, and cache TTL config
- [docs/data-validation-guidelines.md](docs/data-validation-guidelines.md) — Bean Validation constraints, custom timestamp validation, email checks, and BAET validation
- [docs/dependency-management-guidelines.md](docs/dependency-management-guidelines.md) — Version properties, Quarkus BOM alignment, dependency grouping, and automated updates
- [docs/deployment-guidelines.md](docs/deployment-guidelines.md) — Dockerfile, Tekton/GitHub Actions CI, ClowdApp template, and health probes
- [docs/error-handling-guidelines.md](docs/error-handling-guidelines.md) — HTTP status code selection, failures counter, logging levels, and cache refresh errors
- [docs/fault-tolerance-guidelines.md](docs/fault-tolerance-guidelines.md) — REST client retry policy, Kafka callback pattern, and startup cache guards
- [docs/integration-guidelines.md](docs/integration-guidelines.md) — Kafka message production, REST client registration, and path routing
- [docs/logging-and-observability-guidelines.md](docs/logging-and-observability-guidelines.md) — Logging framework, log levels, CloudWatch/Sentry sinks, Micrometer metrics, and health checks
- [docs/performance-guidelines.md](docs/performance-guidelines.md) — Kafka callback timeout, retry overhead, bulk caches vs per-request validation, and resource limits
- [docs/quarkus-guidelines.md](docs/quarkus-guidelines.md) — Quarkus config patterns, CDI scoping, RESTEasy Reactive, caching, and startup lifecycle
- [docs/rh-identity-guidelines.md](docs/rh-identity-guidelines.md) — Identity type hierarchy, header decoding, RHIdAuthMechanism, and test identity construction
- [docs/security-guidelines.md](docs/security-guidelines.md) — Authentication enforcement, allow-list controls, log injection prevention, and CI security scanning
- [docs/testing-guidelines.md](docs/testing-guidelines.md) — Test annotations, REST Assured patterns, MockServer setup, cache handling, and parameterized tests
