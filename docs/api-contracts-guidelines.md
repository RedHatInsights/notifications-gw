# API Contracts Guidelines

## Framework and Stack

- **Runtime**: Quarkus (RESTEasy Reactive) with Jackson serialization (`quarkus-rest-jackson`, `quarkus-rest-client-jackson`).
- **Validation**: `quarkus-hibernate-validator` (Jakarta Bean Validation).
- **OpenAPI**: SmallRye OpenAPI (`quarkus-smallrye-openapi`), served at `/openapi.json`.
- **OpenAPI operation IDs**: Generated via `CLASS_METHOD` strategy (`mp.openapi.extensions.smallrye.operationIdStrategy=CLASS_METHOD`).

## Path Conventions

- Resource classes use `@Path` at the class level. The primary resource is mounted at `/notifications`.
- In production, 3scale exposes the path `/api/notifications-gw/notifications`. The `IncomingRequestInterceptor` (a `@PreMatching` `ContainerRequestFilter`) rewrites that path to `/notifications` for ephemeral environments where 3scale is absent.
- Non-application endpoints (health, metrics, OpenAPI) are served from the root (`/`) via `quarkus.http.non-application-root-path=/`. Do not change this -- tests in `NonApplicationRootPathTest` verify `/health`, `/metrics`, and `/openapi.json` are accessible without a `/q/` prefix.

## Content Negotiation

- All resource classes declare both `@Consumes(APPLICATION_JSON)` and `@Produces(APPLICATION_JSON)` at the class level.
- REST clients (`RestValidationClient`, `RestInternalClient`) also annotate each method with `@Produces` and optionally `@Consumes` to be explicit about the expected media types. The `/internal/validation/baet` endpoint is an exception that produces `TEXT_PLAIN`.

## Request/Response Model Conventions

### JSON Field Naming

- Models use **snake_case** for JSON field names, achieved via `@JsonProperty` annotations on individual fields (e.g., `@JsonProperty("event_type")`, `@JsonProperty("org_id")`, `@JsonProperty("account_id")`).
- The `SourceEnvironment` model uses `@JsonNaming(SnakeCaseStrategy.class)` as a class-level alternative. Both approaches coexist; prefer `@JsonProperty` on gateway-facing models for explicitness.
- Always apply `@JsonIgnoreProperties(ignoreUnknown = true)` on models deserialized from external services to prevent breakage when upstream adds fields.

### Model Structure

- Request/response models are plain POJOs with public fields and getter/setter pairs (not records). Fields that are optional omit `@NotNull`.
- The `RestAction` model is the primary inbound payload. It references `RestEvent` (list), `RestRecipient` (optional list), and a free-form `Map<String, Object> context`.
- The `toString()` on `RestAction` uses `JsonObject.encode()` for structured logging; follow this pattern when adding loggable models.

### Standard Response Envelope

All responses from the `forward` endpoint use a JSON envelope built by `buildResponseEntity`:
```json
{"result": "success"}
{"result": "error", "details": "description of the problem"}
```
Do not return raw strings or alternative shapes from gateway endpoints.

## Bean Validation Rules

### Naming Identifiers

`bundle`, `application`, and `eventType` fields must match `[a-z][a-z_0-9-]*` -- lowercase, starting with a letter, allowing underscores and hyphens. This is enforced with `@Pattern` and is also checked by `@NotNull @NotEmpty`.

### Custom Validators

- The `@ISO8601Timestamp` annotation is a custom constraint backed by `TimestampValidator`. It validates that the `timestamp` field parses as `ISO_LOCAL_DATE_TIME` (e.g., `2020-12-18T17:04:04.417921`). No timezone offset is accepted.
- Email addresses in `RestRecipient.emails` are validated with `@Pattern(regexp = "^\\S+@\\S+\\.\\S+$")` applied at the list element level: `List<@Pattern(...) String>`.

### Cascading Validation

- The `forward` method parameter uses `@NotNull @Valid RestAction ra` to trigger validation on the body.
- Inside `RestAction`, the `recipients` field is annotated `@Valid` to cascade validation into each `RestRecipient`.

### Positive-String Pattern

`accountId` and `orgId` are typed as `String` (to preserve leading zeros) but annotated `@Positive` to enforce they contain only positive numeric values.

## Authentication

- Authentication is handled by `RHIdAuthMechanism`, which reads the `x-rh-identity` header (Base64-encoded JSON).
- The identity JSON is deserialized into `XRhIdentity` -> `Identity` using Jackson polymorphic deserialization keyed on the `type` field (`X509`, `Associate`, `ServiceAccount`).
- The principal extracted (`RhIdPrincipal`) carries `subject` and `type`. The `type` determines source-environment validation behavior in `GwResource`.

## Request Interceptor

- `IncomingRequestInterceptor` is a `@Provider @PreMatching` filter. It performs path rewriting only, no authentication or header manipulation.
- It includes an anti-injection pattern (`[\n|\r|\t]`) to sanitize URIs before trace-level logging, preventing log forging.

## REST Client Conventions

- REST clients are MicroProfile REST Client interfaces annotated with `@RegisterRestClient(configKey = "...")`.
- They must be `@ApplicationScoped` to support mocking in tests via `@InjectMock @RestClient`.
- Fault tolerance: all REST client methods use `@Retry(maxRetries = 5)`.
- Caching: use Quarkus `@CacheResult(cacheName = "...")` on client methods or on wrapper methods in the resource. Cache TTLs are configured in `application.properties` under `quarkus.cache.caffeine.<name>.expire-after-write`.
- Query parameters use `@RestQuery` (RESTEasy Reactive), not `@QueryParam`. Path parameters use `@RestPath`, not `@PathParam` (in client interfaces).

## OpenAPI Annotations

- Use `@Operation(summary = "...")` on endpoint methods.
- Document all response codes with `@APIResponses` / `@APIResponse`, including `401` (even without a description, for completeness).
- Do not add OpenAPI annotations to REST client interfaces.

## Error Handling Strategy

- Validation errors from the backend returning HTTP 400 are forwarded to the caller as 400 with the backend's error message.
- All other backend errors (5xx, network failures) are returned as 503 with a generic "please try again later" message. Never expose internal error details for non-400 errors.
- Backend errors (non-400) and Kafka delivery failures increment a Micrometer counter (`notifications.gw.failed.requests`) tagged with the returned `status_code` (503). Client errors (400, 403) do not increment this counter.

## Internal Email Restriction

When `notifications.emails.internal-only.enabled` is `true` (the default), all email addresses in `recipients.emails` must end with `@redhat.com`. Non-matching addresses cause a 400 response listing the offending addresses. Any new endpoint accepting email recipients must enforce this same rule.
