# Security Guidelines

## Authentication Model

### x-rh-identity Header
This gateway uses a custom `HttpAuthenticationMechanism` (`RHIdAuthMechanism`) that extracts identity from the Base64-encoded `x-rh-identity` HTTP header. The header is set by the platform (3scale/Turnpike) before requests reach this service -- the gateway does NOT perform cryptographic verification of the header itself.

- All JAX-RS resources automatically go through Quarkus Security, which invokes the `RHIdAuthMechanism`.
- The `RhIdPrincipal` provides access to both a subject (via `getName()`) and a type (via `getType()`). Always check `principal.getType()` when making authorization decisions -- different identity types (X509, ServiceAccount, Associate) have different trust levels.
- If the `x-rh-identity` header is missing or malformed, `HeaderHelper.getRhIdFromString` returns `Optional.empty()` and the principal defaults to subject `"-unset-"` with type `"-unset-"`. The request is NOT rejected at the auth layer. Validation happens downstream.

### Supported Identity Types
The `Identity` class uses Jackson polymorphic deserialization with three subtypes:

| Type | Class | Subject extracted from |
|---|---|---|
| `X509` | `X509Identity` | `x509.subject_dn` |
| `Associate` | `SamlIdentity` | `associate.email` |
| `ServiceAccount` | `RhServiceAccountIdentity` | `service_account.username` |

When adding a new identity type, register it as a `@JsonSubTypes.Type` on the `Identity` class. Unknown types will cause deserialization to fail silently (returns empty optional).

## Authorization

### Source Environment and Allow-List
Requests from X509 or ServiceAccount identities undergo source environment resolution. If the source is `"stage"`, the org ID must appear in the allow-list or the request is rejected with HTTP 403.

- The allow-list is configured via `notifications.allow-list.enabled` and `notifications.allow-list.org-ids`.
- This mechanism prevents staging services from sending notifications to production customers. Never disable this in production.
- When `notifications.bulk-caches.enabled` is `true`, source environments are resolved from a locally cached list (refreshed hourly). When `false`, each request calls `RestValidationClient.validateCertificate()`.

### Internal-Only Email Restriction
When `notifications.emails.internal-only.enabled` is `true` (default), all email addresses in `recipients.emails` must end with `@redhat.com`. Non-matching addresses cause an HTTP 400 rejection. The check is case-insensitive after trimming.

## Input Validation

### RestAction Constraints
All incoming notification payloads are validated via Jakarta Bean Validation on `RestAction`:

- `bundle`, `application`, `eventType`: Must match `[a-z][a-z_0-9-]*` -- lowercase only, must start with a letter.
- `orgId`: Required, non-empty, must be a positive number (string type allows leading zeros).
- `accountId`: Optional but if present must be a positive number.
- `timestamp`: Validated by custom `@ISO8601Timestamp` constraint using `DateTimeFormatter.ISO_LOCAL_DATE_TIME`.
- `events`: Required, non-null list. Each `RestEvent.payload` must be non-null and non-empty.

When adding new fields to `RestAction`, always add appropriate Jakarta Validation annotations. Do not rely solely on downstream validation.

### Email Format Validation
`RestRecipient.emails` entries are validated against the regex `^\S+@\S+\.\S+$`. This is a structural check only, not a deliverability check.

### Event Type Validation
Before forwarding to Kafka, the gateway validates that the `(bundle, application, eventType)` tuple exists by calling the notifications-backend service (or checking the local cache when bulk caches are enabled). Invalid tuples return HTTP 400.

## Log Safety

### Anti-Injection Protection
`IncomingRequestInterceptor` sanitizes URIs before logging using `ANTI_INJECTION_PATTERN` which strips `\n`, `\r`, and `\t` characters. This prevents log forging/poisoning attacks.

When adding new log statements that include user-supplied data, sanitize the input or use parameterized logging (`Log.infof` with `%s` placeholders). Never concatenate raw user input into log messages.

### Access Log Filtering
Health and metrics endpoint requests (`/health`, `/metrics`) are filtered from access logs to reduce noise. The filter is in `NotificationsGwApp.initAccessLogFilter()`.

## Secrets Management

### Configuration Sources
- Secrets and service URLs are injected via Clowder (`clowder-quarkus-config-source`), not hardcoded.
- `application.properties` contains only defaults and placeholder values for local development.
- CloudWatch credentials (`access-key-id`, `access-key-secret`) have `"placeholder"` defaults -- real values come from Clowder at runtime.
- Never add real credentials to `application.properties` or test resource files.

### Kafka SSL
The property `feature-flags.expose-kafka-ssl-config-keys.enabled=true` allows Clowder to inject Kafka SSL configuration. Do not disable this in production deployments.

## Downstream Communication

### REST Client Security
- `RestValidationClient` and `RestInternalClient` communicate with `notifications-backend` over internal service mesh paths (`/internal/validation/*`, `/internal/gw/*`).
- Both clients use `@Retry(maxRetries = 5)` for resilience.
- The backend URL is resolved from Clowder: `quarkus.rest-client.notifications-backend.url=${clowder.endpoints.notifications-backend-service.url}`.
- When the backend returns non-400 errors, the gateway returns HTTP 503 to callers and does NOT leak internal error details.

### Kafka Message Headers
Every outgoing Kafka message includes:
- `rh-message-id`: A random UUID v4 for traceability.
- `rh-source-environment`: Added only when the source environment is identified (X509/ServiceAccount from a known source).

Never remove the `rh-message-id` header -- downstream consumers depend on it for deduplication and tracing.

## Caching

Security-relevant data is cached with the following TTLs:

| Cache | TTL | Purpose |
|---|---|---|
| `baet-validation` | 10 minutes | Per-request event type validation results |
| `certificate-validation` | 10 minutes | Per-request certificate validation results |
| `get-baets` | 1 hour | Bulk event type list (when bulk caches enabled) |
| `get-certificates` | 1 hour | Bulk certificate/source environment list |

Cache invalidation does not happen on-demand. If a certificate or event type is revoked, it may remain valid for up to the TTL duration. When adjusting TTLs, balance security responsiveness against backend load.

## Testing Security Features

### Identity in Tests
Use `TestHelpers.encodeIdentityInfo(tenant, username)` to create Base64-encoded `x-rh-identity` headers for tests. This creates an X509-type identity with the username as the subject DN.

Every test request to authenticated endpoints must include the `x-rh-identity` header. Tests without this header exercise the `"-unset-"` fallback path, not the real authentication flow.

### Allow-List Tests
`AllowListTest` covers the matrix of allow-list enabled/disabled, stage/prod source, and known/unknown org IDs. When modifying the allow-list logic, ensure all combinations remain covered.

### Validation Tests
`RestActionValidationTest` tests Bean Validation constraints independently of the HTTP layer using the `Validator` API. When adding new constraints to `RestAction`, add corresponding unit tests in this class.

## Ephemeral Environment Considerations
In ephemeral environments (no 3scale), the gateway receives requests on `/api/notifications-gw/notifications` instead of `/notifications`. The `IncomingRequestInterceptor` rewrites this path. Do not add security logic that depends on the request path, as it may differ between environments.
