# Configuration Guidelines

## Application Properties

- Define all runtime-configurable settings in `src/main/resources/application.properties` using the Quarkus/MicroProfile Config conventions. Group related properties with comments (Kafka, OpenAPI, logging, caching).
- Use the `notifications.` prefix for application-specific config properties. Quarkus-managed properties use their standard prefixes (`quarkus.`, `mp.messaging.`, `kafka.`).
- Provide a `defaultValue` on every `@ConfigProperty` annotation in Java code so the app starts locally without external config. See `GwConfig.java` for the pattern:
  ```java
  @ConfigProperty(name = "notifications.allow-list.enabled", defaultValue = "false")
  boolean allowListEnabled;
  ```
- Use the `%test.` profile prefix in `application.properties` for test-only overrides (e.g., `%test.quarkus.log.category."com.redhat.cloud.notifications".level=DEBUG`). Do not create separate `application-test.properties` files.

## Centralized Config Bean

- Consolidate feature-flag and business-logic config properties into `GwConfig.java` (`@ApplicationScoped`). Inject `GwConfig` rather than scattering `@ConfigProperty` fields across resource classes.
- Add a log line for each new property inside `GwConfig.logConfiguration()` so its runtime value is visible at startup.
- Keep infrastructure config (Kafka timeout, HTTP port) as `@ConfigProperty` fields directly in the class that uses them (e.g., `callbackTimeout` in `GwResource.java`).

## Environment Variable Mapping

- Map every custom config property to an environment variable in `.rhcicd/clowdapp.yaml` under `spec.deployments[0].podSpec.env`. Use uppercase with underscores replacing dots and hyphens (e.g., `notifications.allow-list.enabled` becomes `NOTIFICATIONS_ALLOW_LIST_ENABLED`).
- Add a corresponding `parameters` entry in the same `clowdapp.yaml` with a `description` and sensible default `value`. This is the contract for deployment operators.
- For Clowder-resolved properties, use the `${clowder.endpoints.<service>.url:<local-fallback>}` syntax. The only current example is the notifications-backend URL:
  ```
  quarkus.rest-client.notifications-backend.url=${clowder.endpoints.notifications-backend-service.url:http://localhost:8085}
  ```

## Caching Configuration

- This project uses four named Caffeine caches. Their TTLs are configured in `application.properties`:
  - `baet-validation` and `certificate-validation` (10-minute TTL) -- per-request validation caches on `RestValidationClient`
  - `get-baets` and `get-certificates` (1-hour TTL) -- bulk data caches on `GwResource`
- When adding a new `@CacheResult(cacheName = "...")`, add a matching `quarkus.cache.caffeine.<name>.expire-after-write` entry in `application.properties`.
- In tests, inject the cache with `@CacheName("<name>")` and call `cache.invalidateAll().await().indefinitely()` in `@BeforeEach` to prevent state leaking between tests.

## Kafka Messaging Configuration

- The outgoing Kafka channel is named `egress` and writes to the `platform.notifications.ingress` topic. Configure it via `mp.messaging.outgoing.egress.*` properties.
- Tests override the Kafka connector to use `smallrye-in-memory` in `src/test/resources/application.properties`. Do not add additional test Kafka overrides in the main properties file.
- The Kafka topic is declared in `.rhcicd/clowdapp.yaml` under `kafkaTopics` with its partition and replica counts. Keep these in sync if changing topic configuration.

## Test Configuration

- Override the messaging connector in `src/test/resources/application.properties` with `mp.messaging.outgoing.egress.connector=smallrye-in-memory`. This file is intentionally minimal.
- Set up test infrastructure (MockServer URL, in-memory channels) by returning config overrides from `TestLifecycleManager.start()` as a `Map<String, String>`. Do not use `@TestProfile` for these overrides.
- Mock REST clients with `@InjectMock @RestClient` and config beans with `@InjectSpy` in test classes (see `GwResourceTest.java`).

## Docker and Deployment

- The Dockerfile at `src/main/docker/Dockerfile-build.jvm` sets `JAVA_OPTIONS` including `-Dquarkus.http.host=0.0.0.0`. The Clowdapp overrides the HTTP port to 8000 via `QUARKUS_HTTP_PORT`.
- The default dev port is 8086 (set in `application.properties`). The production port is 8000 (set in `clowdapp.yaml`). Do not change either without updating both locations.

## Verification

```bash
# Confirm application.properties is valid and app compiles
./mvnw clean compile --no-transfer-progress

# Run all tests (validates test config overrides work)
./mvnw test --no-transfer-progress

# Check that all env vars in clowdapp.yaml have matching parameters
grep -oP 'value: \$\{(\w+)\}' .rhcicd/clowdapp.yaml | sort -u

# Verify no config properties are missing defaultValue in Java code
grep -rn '@ConfigProperty' src/main/java --include="*.java"
```
