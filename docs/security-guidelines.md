# Security Guidelines

## Authentication and Identity Handling

- Authenticate all requests through the `x-rh-identity` header, decoded in `RHIdAuthMechanism`. Do not add alternative authentication paths or bypass the `HeaderHelper.getRhIdFromString` parsing flow.
- Preserve the polymorphic identity type dispatch in `Identity.java` (X509, Associate/SAML, ServiceAccount). When adding a new identity type, add a `@JsonSubTypes.Type` entry in the `Identity` class and create a corresponding subclass with a `getSubject()` implementation.
- Avoid logging the raw `x-rh-identity` header value on the success path. The existing WARN-level log in `HeaderHelper` on deserialization failure intentionally logs the raw header value for debugging; do not add the header value to success-path logs.
- Keep `RHIdAuthMechanism.authenticate` non-blocking -- it returns a `Uni<SecurityIdentity>` and should not perform I/O or call backend services during authentication.

## Allow-List and Source Environment Controls

- Enforce the staging environment allow-list check in `GwResource.forward()` before any Kafka message is sent. The guard checks `gwConfig.isAllowListEnabled()`, source environment equality to `"stage"`, and org ID membership in `gwConfig.getAllowListOrgIds()`.
- When modifying allow-list logic, update `AllowListTest` with matching scenarios. Each combination of enabled/disabled, stage/prod, and known/unknown org ID has a dedicated test case.
- Prefer returning HTTP 403 (FORBIDDEN) for allow-list rejections from staging sources. The error response includes the org ID (via `ALLOW_LIST_ERROR_MESSAGE`) but does not expose the full allow-list contents.

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

- Run the production container as non-root user 185 (jboss). The Dockerfile switches to `USER jboss` after `microdnf clean all` and then to `USER 185` for the final runtime layer (after the COPY steps).
- Include `-XX:+ExitOnOutOfMemoryError` in `JAVA_OPTIONS` to terminate the JVM on OOM rather than running in a degraded state.
- Update base image packages with `microdnf upgrade --refresh --nodocs --setopt=install_weak_deps=0 -y` during container builds to pick up security patches.

## CI Security Scanning

- CodeQL analysis runs on every push and PR to `main` via `.github/workflows/codeql-analysis.yml`. Do not disable or skip this workflow.
- Platform Security scans (Grype vulnerability scan and Syft SBOM generation) run via `.github/workflows/platsec-gw.yml` on push and PR to `main`, `master`, and `security-compliance` branches. The scan uses the Dockerfile at `src/main/docker/Dockerfile-build.jvm`.

## Verification

```bash
# Run all tests including security-related identity, allow-list, and validation tests
./mvnw test --no-transfer-progress

# Verify allow-list test coverage
./mvnw test -Dtest=AllowListTest --no-transfer-progress

# Verify identity parsing test coverage
./mvnw test -Dtest=IdentityTest --no-transfer-progress

# Verify input validation test coverage
./mvnw test -Dtest=RestActionValidationTest --no-transfer-progress

# Verify email and recipient validation tests
./mvnw test -Dtest=GwResourceTest#testForbiddenRecipientsEmails --no-transfer-progress

# Check for secrets or credentials accidentally committed
grep -riE "(api_key|password|secret|token|credential)\s*[:=]\s*['\"][^'\"]{8,}" src/main/resources/
```
