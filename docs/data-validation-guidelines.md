# Data Validation Guidelines

## Bean Validation on REST Models

- Annotate `RestAction` fields with Jakarta Bean Validation constraints (`@NotNull`, `@NotEmpty`, `@Pattern`, `@Positive`) rather than writing manual checks in `GwResource`.
- Apply the `@Valid` annotation on the `RestAction` parameter in endpoint methods (`GwResource.forward`) and on nested collection fields (`RestAction.recipients`) to trigger cascading validation via `quarkus-hibernate-validator`.
- Use the regex `[a-z][a-z_0-9-]*` for `bundle`, `application`, and `eventType` fields in `RestAction`. These three fields share the same `@Pattern` constraint; keep them consistent when adding new string identifier fields.
- Validate `orgId` with both `@Positive` and `@NotNull`/`@NotEmpty`. The field is typed as `String` (to allow leading zeros) but constrained to contain only a positive number.
- Keep `accountId` validated with `@Positive` only (no `@NotNull`/`@NotEmpty`) -- it is optional, unlike `orgId`.

## Custom Timestamp Validation

- Validate timestamp strings via the custom `@ISO8601Timestamp` annotation, which delegates to `TimestampValidator`.
- `TimestampValidator` parses with `DateTimeFormatter.ISO_LOCAL_DATE_TIME` (locale `US`). Accepted format example: `2020-12-18T17:04:04.417921`. Timezone-aware formats (e.g., `2020-12-18T17:04:04.417921Z`) are rejected.
- When adding new date/time fields to REST models, prefer reusing `@ISO8601Timestamp` over introducing a new custom constraint.

## Email Validation

- Validate email entries in `RestRecipient.emails` with `@Pattern(regexp = "^\\S+@\\S+\\.\\S+$")` applied at the type-use level on list elements (`List<@Pattern(...) String>`).
- Enforce the `@redhat.com` domain restriction for recipient emails in `GwResource.forward` as a runtime check (controlled by the `notifications.emails.internal-only.enabled` config property), not via Bean Validation annotations.

## Backend BAET Validation

- Validate that the `bundle`/`application`/`eventType` triple is registered in the notifications backend. This uses one of two paths depending on `notifications.bulk-caches.enabled`:
  - When disabled (default): call `RestValidationClient.validate()` synchronously per request. The client retries up to 5 times (`@Retry(maxRetries = 5)`) and caches results with `@CacheResult(cacheName = "baet-validation")` (TTL 10 min).
  - When enabled: use the in-memory `mapBaet` map populated from `RestValidationClient.getBaets()`, loaded via `GwResource.refreshBaets()` which is cached under the `get-baets` cache (TTL 1 hour).
- Distinguish backend error responses: return HTTP 400 to the caller when the backend returns 400 (invalid BAET); return HTTP 503 for all other backend errors or when the backend is unreachable (`ProcessingException`).

## Allow-List Validation for Staging Sources

- When `notifications.allow-list.enabled` is `true` and the source environment is `"stage"`, reject the request with HTTP 403 unless `orgId` is in `notifications.allow-list.org-ids`. This check happens in `GwResource.forward` after identity resolution but before BAET validation.

## Certificate / Source Environment Validation

- Resolve the source environment for `X509` and `ServiceAccount` identity types by matching `bundle`, `application`, and the principal's subject DN against `RestValidationClient.validateCertificate()` (per-request mode, cached via `certificate-validation`) or the in-memory `sourceEnvironments` list loaded by `GwResource.refreshSourceEnvironment()` (cached under `get-certificates`, TTL 1 hour) when bulk caches are enabled.

## Log Injection Protection

- Sanitize user-controlled strings before logging using `ANTI_INJECTION_PATTERN` (strips `\n`, `\r`, `\t`) as done in `IncomingRequestInterceptor`. Apply the same pattern when logging request-derived data in new code paths.

## Identity Header Validation

- The `x-rh-identity` header is Base64-decoded and deserialized into `XRhIdentity` in `HeaderHelper.getRhIdFromString`. Jackson is configured with `FAIL_ON_UNKNOWN_PROPERTIES = false`. If decoding or deserialization fails, the result is `Optional.empty()` and the principal defaults to `"-unset-"`.

## Writing Validation Tests

- Write unit-level constraint tests in `RestActionValidationTest` using `jakarta.validation.Validator` directly (no Quarkus context needed). Assert the expected violation count for both valid and invalid inputs.
- Write integration-level validation tests in `GwResourceTest` using REST Assured against live Quarkus endpoints (`/notifications/`). Mock `RestValidationClient` with `@InjectMock` and `@RestClient`. `SamplesTest` covers the `/sample/verify` endpoint via MockServer (configured by `TestLifecycleManager`) rather than `@InjectMock`.
- Cover both the `isBulkCachesEnabled = true` and `false` paths in BAET validation tests (see `shouldReturnBadRequestWhenApplicationBundleAndEventTypeAreInvalid`).
- Test email validation at the REST endpoint level: submit `RestRecipient` with invalid emails and assert HTTP 400 with a response body referencing `recipients[0].emails[0]`.

## Verification

```bash
# Run all tests including validation tests
./mvnw clean package --no-transfer-progress

# Run only the RestAction bean validation unit tests
./mvnw test -pl . -Dtest=RestActionValidationTest --no-transfer-progress

# Run only the gateway resource integration tests (includes email, BAET, allow-list validation)
./mvnw test -pl . -Dtest=GwResourceTest --no-transfer-progress

# Run the samples endpoint validation tests
./mvnw test -pl . -Dtest=SamplesTest --no-transfer-progress

# Run the allow-list validation tests
./mvnw test -pl . -Dtest=AllowListTest --no-transfer-progress
```
