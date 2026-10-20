# Code Organization Guidelines

## Package Structure

- Place all production classes under `com.redhat.cloud.notifications` in `src/main/java`. Use only these sub-packages:
  - `auth` -- authentication mechanism classes (`Identity` subtypes, `RHIdAuthMechanism`, `HeaderHelper`, `RhIdPrincipal`)
  - `model` -- data transfer objects shared with the notifications-backend (e.g. `SourceEnvironment`)
- Place new REST model POJOs (request/response bodies) directly in the root `com.redhat.cloud.notifications` package alongside `RestAction`, `RestEvent`, `RestRecipient`, and `RestMetadata`.

## REST Resource Conventions

- Annotate JAX-RS resource classes with `@ApplicationScoped`, `@Path`, `@Consumes(APPLICATION_JSON)`, and `@Produces(APPLICATION_JSON)` at the class level, following the pattern in `GwResource` and `SamplesResource`.
- Use Quarkus REST (RESTEasy Reactive) annotations: prefer `@RestQuery` from `org.jboss.resteasy.reactive` over `@QueryParam`, and `@RestPath` over `@PathParam` in REST client interfaces.
- Define REST client interfaces (not classes) annotated with `@RegisterRestClient(configKey = "...")` and `@ApplicationScoped`. The `@ApplicationScoped` scope is required to enable mocking during tests. See `RestValidationClient` and `RestInternalClient`.

## REST Client Configuration

- Configure REST client base URLs in `src/main/resources/application.properties` using the pattern `quarkus.rest-client.<configKey>.url` where `configKey` matches the `@RegisterRestClient(configKey)` value.
- Apply `@Retry(maxRetries = 5)` from MicroProfile Fault Tolerance on REST client methods that call the notifications-backend.

## Bean Validation on REST Models

- Apply Jakarta Bean Validation annotations (`@NotNull`, `@NotEmpty`, `@Pattern`, `@Positive`, `@Valid`) directly on public fields of `Rest*` model classes. The `bundle`, `application`, and `eventType` fields use the regex pattern `[a-z][a-z_0-9-]*`.
- For custom validation, create a constraint annotation (like `@ISO8601Timestamp`) paired with a `ConstraintValidator` implementation, both in the root package.
- Use `@JsonProperty` from Jackson for snake_case JSON field names (e.g., `event_type`, `org_id`, `account_id`) while keeping camelCase Java field names.

## Caching

- Use `@CacheResult(cacheName = "...")` from Quarkus Cache on methods whose results should be cached. Configure TTL in `application.properties` with `quarkus.cache.caffeine.<cacheName>.expire-after-write`.
- Four caches exist: `baet-validation` (10min), `certificate-validation` (10min), `get-baets` (1h), `get-certificates` (1h). Prefer adding entries under these caches or following their naming pattern when creating new ones.

## Kafka Messaging

- Use the `@Channel("egress")` injected `Emitter<String>` to send messages to the `platform.notifications.ingress` topic. Do not create additional Kafka producers directly.
- Attach Kafka headers via `OutgoingKafkaRecordMetadata` built with `RecordHeaders`. Include the `rh-message-id` header (UUID v4 string, UTF-8 encoded) on every outgoing message.
- Wire ack/nack callbacks through `CompletableFuture` to synchronously wait for Kafka delivery confirmation with a configurable timeout (`notifications.kafka-callback-timeout-seconds`).

## Authentication

- The `auth` sub-package implements a custom `HttpAuthenticationMechanism` (`RHIdAuthMechanism`) that decodes the Base64-encoded `x-rh-identity` header. Do not add alternative auth mechanisms.
- Identity types are distinguished via Jackson polymorphic deserialization on the `Identity` abstract class. To support a new identity type, add a new `@JsonSubTypes.Type` entry in `Identity` and a corresponding subclass.

## Configuration

- Centralize feature flags and allow-list configuration in `GwConfig` as `@ConfigProperty` fields. Use `notifications.*` as the config key prefix for application-specific properties.
- Log configuration values at startup from `GwConfig.logConfiguration()`.
- The `IncomingRequestInterceptor` rewrites `/api/notifications-gw/notifications` to `/notifications` for ephemeral environments. Path rewriting logic belongs in this single `@Provider @PreMatching` filter.

## Metrics

- Register Micrometer counters via constructor injection of `MeterRegistry` (see `GwResource` constructor). Use `notifications.gw.*` as the metric name prefix.
- Increment the `notifications.gw.failed.requests` counter with a `status_code` tag when returning error responses.

## Test Conventions

- Annotate integration tests with `@QuarkusTest` and `@QuarkusTestResource(TestLifecycleManager.class)`. The lifecycle manager starts MockServer and switches the Kafka egress channel to `InMemoryConnector`.
- Override the Kafka connector in `src/test/resources/application.properties` with `mp.messaging.outgoing.egress.connector=smallrye-in-memory`.
- Mock REST clients with `@InjectMock @RestClient` and spy on beans with `@InjectSpy`.
- Use `TestHelpers.encodeIdentityInfo(tenant, username)` to generate valid Base64-encoded `x-rh-identity` headers for tests.
- Use `InMemorySink<String>` to verify Kafka messages. Wait for messages with `await().atMost(Duration.ofSeconds(10L))` from Awaitility.
- Write pure validation tests (like `RestActionValidationTest`) as plain JUnit 5 without `@QuarkusTest` when only testing Bean Validation constraints.

## Verification

```bash
# Compile and run all tests
./mvnw verify

# Compile only (fast check for syntax/structure errors)
./mvnw compile

# Run tests with MockServer logging enabled
./mvnw verify -Dmockserver.logLevel=WARN
```
