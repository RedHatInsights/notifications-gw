# Testing Guidelines

## Test Framework and Annotations

- Annotate integration tests with `@QuarkusTest`. Tests requiring MockServer or the in-memory Kafka connector also need `@QuarkusTestResource(TestLifecycleManager.class)`.
- Use `@InjectMock` with `@RestClient` to mock REST clients (`RestValidationClient`, `RestInternalClient`). Use `@InjectSpy` to partially mock CDI beans like `GwConfig` and `GwResource` (as in `GwResourceTest`); use `@InjectMock` to fully replace a bean like `GwConfig` in `AllowListTest`.
- Prefer `io.quarkus.test.junit.mockito.InjectSpy` over manually constructing spies; Quarkus manages the CDI proxy lifecycle.
- Pure validation tests (like `RestActionValidationTest`) do not need `@QuarkusTest`; use Jakarta `Validator` directly for faster execution.

## Identity and Authentication

- Generate the `x-rh-identity` header using `TestHelpers.encodeIdentityInfo(tenant, username)`. This produces a Base64-encoded X509-type identity JSON.
- Note: `RHIdAuthMechanism` does not reject requests missing the `x-rh-identity` header; it sets subject and type to `"-unset-"` and proceeds. Endpoint authorization failures result from downstream logic, not the auth mechanism itself.

## REST Endpoint Testing

- Use REST Assured (`io.restassured.RestAssured.given`) for HTTP endpoint tests. Import `static io.restassured.RestAssured.given` and `static io.restassured.http.ContentType.JSON`.
- Build `RestAction` payloads with required fields: `bundle`, `orgId`, `application`, `eventType`, `events`, and `timestamp` (ISO 8601 format like `"2020-12-18T17:04:04.417921"`).
- Set `contentType(MediaType.APPLICATION_JSON)` on requests sending a JSON body.
- Parse response bodies as `JsonObject` (Vert.x) to assert on `result` and `details` fields, matching the gateway's standard JSON response format: `{"result":"success"|"error", "details":"..."}`.

## MockServer Setup

- `MockServerLifecycleManager` manages a MockServer instance used to stub the notifications-backend validation endpoints. It is started/stopped by `TestLifecycleManager`.
- Configure mock expectations in `TestLifecycleManager.setupMockServer()` for the `/internal/validation/baet` path with query parameters for bundle, application, and eventtype.
- Prefer `@InjectMock @RestClient RestValidationClient` over MockServer expectations when you need per-test control over REST client behavior (as done in `GwResourceTest` and `AllowListTest`).

## Cache Handling in Tests

- Inject caches by name (`@CacheName("get-baets")`, `@CacheName("get-certificates")`) and invalidate before test scenarios that depend on fresh data: `cacheBaet.invalidateAll().await().indefinitely()`.
- Use `@CacheInvalidateAll(cacheName = "certificate-validation")` on `@BeforeEach` methods when the entire test class needs cache isolation (see `AllowListTest`).

## Parameterized Tests

- Use `@ParameterizedTest` with `@ValueSource` for testing variants of the same behavior, such as toggling `isBulkCachesEnabled` (booleans) or testing multiple endpoint paths (strings like `"/notifications/"` and `"/api/notifications-gw/notifications"`).

## Test Organization

- Test classes reside in the package `com.redhat.cloud.notifications` under `src/test/java`.
- Prefer helper methods within the test class (e.g., `testSimplePayload`, `sendMessage`, `mock`) to reduce duplication when multiple tests share payload construction logic.
- Call `Mockito.reset(...)` explicitly when reusing mocks across iterations within a single test method (see `shouldReturnServiceUnavailableForNonBadRequest`).

## Verification

```bash
# Run all tests
./mvnw clean test --no-transfer-progress

# Run a single test class
./mvnw test -Dtest=GwResourceTest --no-transfer-progress

# Run a single test method
./mvnw test -Dtest=GwResourceTest#testNotificationsEndpoint --no-transfer-progress

# Full build including tests (matches CI)
./mvnw clean package --no-transfer-progress
```
