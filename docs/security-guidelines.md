# Security Guidelines

## Authentication Mechanism and Identity Dispatch

- Authenticate all requests through `RHIdAuthMechanism`, the sole Quarkus `HttpAuthenticationMechanism`. It reads the raw `x-rh-identity` header, decodes it via `HeaderHelper.getRhIdFromString()`, and builds a `QuarkusSecurityIdentity` with an `RhIdPrincipal`. Do not add alternative authentication paths or bypass this flow.
- Decode the `x-rh-identity` header exclusively through `HeaderHelper.getRhIdFromString()`. Do not call the underlying Base64 or Jackson APIs directly.
- Keep `RHIdAuthMechanism.authenticate` non-blocking — it returns a `Uni<SecurityIdentity>` and must not perform I/O or call backend services during authentication.
- Avoid logging the raw `x-rh-identity` header value on the success path. The existing WARN-level log in `HeaderHelper` on deserialization failure intentionally logs the raw header for debugging; do not add it to success-path logs.
- Register new identity types in `Identity.java` via `@JsonSubTypes` with the exact `type` string from the x-rh-identity JSON payload. The three current discriminator values are `"X509"`, `"Associate"`, and `"ServiceAccount"`. Extend `Identity` (abstract class) and implement `getSubject()` to return the caller-identifying string (e.g., `subject_dn` for X509, `email` for Associate, `username` for ServiceAccount).
- Annotate ServiceAccount-style identity classes with both `@JsonNaming(SnakeCaseStrategy.class)` and `@JsonIgnoreProperties(ignoreUnknown = true)`. X509 and Associate identity classes use default field naming with public fields.
- `RhIdPrincipal` carries both `name` (subject string) and `type` (discriminator). Cast `SecurityContext.getUserPrincipal()` to `RhIdPrincipal` in resource methods to access `getType()`.
- Check `principal.getType()` against the string constants `"X509"` and `"ServiceAccount"` (private constants in `GwResource`) to determine whether source environment validation applies. Associate identity types skip source environment checks.

## Allow-List and Source Environment Controls

- Enforce the staging allow-list check in `GwResource.forward()` before any Kafka message is sent. The guard fires only when all three conditions hold: `gwConfig.isAllowListEnabled()` is true, source environment equals `"stage"`, and the org ID is not in `gwConfig.getAllowListOrgIds()`. Production source environments bypass the allow-list entirely.
- Return HTTP 403 for allow-list rejections. The error response includes the org ID via `ALLOW_LIST_ERROR_MESSAGE` but does not expose the full allow-list contents.
- When modifying allow-list logic, update `AllowListTest` with matching scenarios. Use `@InjectMock GwConfig` and `@InjectMock @RestClient RestValidationClient`. Each combination of enabled/disabled, stage/prod, and known/unknown org ID has a dedicated test case.

## Log Injection Prevention

- Sanitize URI values before logging using `ANTI_INJECTION_PATTERN` in `IncomingRequestInterceptor`, which strips `\n`, `\r`, and `\t` characters. Apply the same pattern when adding new log statements that include user-controlled path or header data.
- Prefer `Log.debugf`/`Log.infof` format strings over string concatenation in log statements to avoid accidental injection of control characters from user input.

## Backend Communication Security

- Route all validation calls through `RestValidationClient`, which targets the `/internal/validation` path on `notifications-backend`. This client uses `@Retry(maxRetries = 5)` and Caffeine caching (10-minute TTL for individual validations, 1-hour TTL for bulk caches).
- Route internal subscription queries through `RestInternalClient` on the `/internal/gw` path. Both REST clients resolve their base URL from `quarkus.rest-client.notifications-backend.url`, defaulting to `http://localhost:8085` in dev.
- When validation calls to notifications-backend fail with non-400 status codes, return HTTP 503 to callers with a generic message. Avoid forwarding internal error details from the backend to external callers.

## Kafka Message Security

- Attach a `rh-message-id` header with a random UUID v4 to every Kafka message in `GwResource.buildMessageWithId()`. Do not reuse or omit this header.
- Attach a `rh-source-environment` header to Kafka messages only when the caller identity type is X509 or ServiceAccount and the source environment is successfully resolved. Do not attach this header for unvalidated sources.

## Container and Runtime Security

- Run the production container as non-root user 185 (jboss). The Dockerfile switches to `USER jboss` after `microdnf clean all` and then to `USER 185` for the final runtime layer.
- Include `-XX:+ExitOnOutOfMemoryError` in `JAVA_OPTIONS` to terminate the JVM on OOM rather than running in a degraded state.
- Update base image packages with `microdnf upgrade --refresh --nodocs --setopt=install_weak_deps=0 -y` during container builds to pick up security patches.

## CI Security Scanning

- CodeQL analysis runs on every push and PR to `main` via `.github/workflows/codeql-analysis.yml`. Do not disable or skip this workflow.
- Platform Security scans (Grype vulnerability scan and Syft SBOM generation) run via `.github/workflows/platsec-gw.yml` on push and PR to `main`, `master`, and `security-compliance` branches. The scan uses the Dockerfile at `src/main/docker/Dockerfile-build.jvm`.

## Test Identity Construction

- For non-X509 identity types in tests, manually construct the JSON, Base64-encode it, and pass it as the `x-rh-identity` header. Follow the patterns in `IdentityTest.java` (`testSamlIdentity`, `testX509Identity`, `testServiceAccountIdentity`).

## Verification

```bash
# Run identity deserialization and allow-list tests
./mvnw test -Dtest=IdentityTest,AllowListTest --no-transfer-progress

# Run full test suite including all security-related tests
./mvnw test --no-transfer-progress

# Verify input validation and email recipient tests
./mvnw test -Dtest=RestActionValidationTest,GwResourceTest#testForbiddenRecipientsEmails --no-transfer-progress

# Verify all identity subtypes are registered
grep -c "@JsonSubTypes.Type" src/main/java/com/redhat/cloud/notifications/auth/Identity.java

# Check for secrets or credentials accidentally committed
grep -riE "(api_key|password|secret|token|credential)\s*[:=]\s*['\"][^'\"]{8,}" src/main/resources/
```
