# Testing Guidelines

## Test Stack

| Component | Library/Tool |
|-----------|-------------|
| Framework | JUnit 5 (via `quarkus-junit5-mockito`) |
| HTTP testing | REST-assured |
| Mocking | Mockito (`@InjectMock`, `@InjectSpy`) |
| External services | MockServer (`mockserver-netty`) |
| Kafka | SmallRye In-Memory Connector (`smallrye-reactive-messaging-in-memory`) |
| Async assertions | Awaitility |
| Bean validation | Jakarta Validation (`Validation.buildDefaultValidatorFactory()`) |

## Test Categories

### 1. Integration tests (`@QuarkusTest`)
Tests that require CDI, REST endpoints, Kafka messaging, or REST client mocking **must** use `@QuarkusTest`. If the test also needs Kafka or MockServer, add the lifecycle resource:

```java
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class MyFeatureTest {
```

Only add `@QuarkusTestResource` when the test actually interacts with Kafka egress or MockServer. Tests like `NonApplicationRootPathTest` and `AllowListTest` use `@QuarkusTest` alone (with `@InjectMock` for REST clients).

### 2. Plain unit tests (no annotation)
Tests that validate POJOs, parsing logic, regex patterns, or bean validation constraints run without Quarkus. Examples: `RestActionValidationTest`, `IdentityTest`, `NotificationsGwAppTest`. Do not add `@QuarkusTest` to these -- it slows execution for no benefit.

## Test Lifecycle

### `TestLifecycleManager`
This is the single `QuarkusTestResourceLifecycleManager` for the project. It:
1. Starts MockServer via `MockServerLifecycleManager.start()`
2. Configures mock expectations for the `/internal/validation/baet` endpoint
3. Switches the `egress` Kafka outgoing channel to the in-memory connector
4. Returns override properties that Quarkus applies at startup

When adding new external service routes that MockServer should handle, add them in `TestLifecycleManager.setupMockServer()`.

### `MockServerLifecycleManager`
A static utility wrapping `ClientAndServer`. Logging is disabled by default. Enable it with `-Dmockserver.logLevel=WARN` (or `INFO`/`DEBUG`/`TRACE`).

## Mocking REST Clients

REST clients (`RestValidationClient`, `RestInternalClient`) are `@ApplicationScoped` interfaces using `@RegisterRestClient`. Mock them in tests with:

```java
@InjectMock
@RestClient
RestValidationClient restValidationClient;
```

Use `@InjectSpy` when you need to verify calls while keeping real behavior for some methods. Always reset mocks between test iterations when looping over multiple scenarios in a single test method.

## Kafka / Messaging Testing

Kafka is replaced by the SmallRye in-memory connector in tests. Access the sink via:

```java
@Inject @Any
InMemoryConnector inMemoryConnector;
```

Rules:
- Always clear the sink in `@BeforeEach` to avoid cross-test contamination.
- Use Awaitility to wait for async message delivery -- never assert on sink size synchronously:
  ```java
  await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0);
  ```
- After receiving a message, verify Kafka headers (e.g., `rh-message-id`) via `KafkaMessageMetadata`.
- Deserialize the payload with `Json.decodeValue(message.getPayload(), Map.class)`.

## Quarkus Cache in Tests

When testing bulk-cache behavior, invalidate caches explicitly between scenarios:

```java
@CacheName("get-baets")
Cache cacheBaet;

cacheBaet.invalidateAll().await().indefinitely();
```

Use `@CacheInvalidateAll(cacheName = "...")` on `@BeforeEach` methods for automatic per-test cache clearing.

## Identity / Authentication

All requests to secured endpoints require an `x-rh-identity` header. Use the helper:

```java
String identity = TestHelpers.encodeIdentityInfo("test", "user");
given().header("x-rh-identity", identity)...
```

This produces a Base64-encoded X509 identity JSON. If you need SAML or ServiceAccount identity types, construct the JSON manually (see `IdentityTest` for the format).

## REST-assured Conventions

- Use static imports: `given()`, `when()`, `JSON` content type.
- POST payloads as domain objects (`RestAction`) -- Jackson serialization is automatic.
- Assert status codes and extract response as `String`, then parse with `JsonObject`:
  ```java
  String responseBody = given()
      .body(action)
      .header("x-rh-identity", identity)
      .contentType(JSON)
      .when().post("/notifications/")
      .then().statusCode(200)
      .extract().asString();
  assertEquals("success", new JsonObject(responseBody).getString("result"));
  ```

## Bean Validation Tests

For testing `RestAction` validation constraints without starting Quarkus:

```java
ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
Validator validator = factory.getValidator();
Set<ConstraintViolation<RestAction>> violations = validator.validate(action);
assertEquals(0, violations.size(), violations.toString());
```

## Naming Conventions

- Test classes: `<Subject>Test.java` (e.g., `GwResourceTest`, `AllowListTest`)
- Test methods: descriptive camelCase starting with `test` or `should`, or a concise name like `noEmptyContent`
- Helper methods within test classes are `private` or package-private and reused across test methods in the same class

## File Organization

All test classes live in a single flat package: `com.redhat.cloud.notifications`. There are no sub-packages in the test tree.

| File | Purpose |
|------|---------|
| `TestLifecycleManager` | Quarkus test resource: starts MockServer, configures in-memory Kafka |
| `MockServerLifecycleManager` | Static MockServer wrapper (start/stop/getClient) |
| `TestHelpers` | Identity header encoding utility |

## Configuration

Test-specific properties are set via the `%test.` profile prefix in `application.properties`. Additional overrides come from `TestLifecycleManager.start()` return value (MockServer URL, in-memory connector config). Do not create a separate `application-test.properties` file.

## Running Tests

```bash
./mvnw test
```

No Docker or Testcontainers containers are launched at runtime -- MockServer is started in-process via `ClientAndServer.startClientAndServer()` and Kafka is replaced by the in-memory connector.
