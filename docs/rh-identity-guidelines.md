# RH Identity Guidelines

## Identity Type Hierarchy and Polymorphic Deserialization

- Register new identity types in `Identity.java` via `@JsonSubTypes` with the exact `type` string from the `x-rh-identity` JSON payload. The three current discriminator values are `"X509"`, `"Associate"`, and `"ServiceAccount"`.
- Extend `Identity` (abstract class) for each new identity type and implement `getSubject()` to return the caller-identifying string (e.g., `subject_dn` for X509, `email` for Associate, `username` for ServiceAccount).
- Annotate ServiceAccount-style identity classes with both `@JsonNaming(SnakeCaseStrategy.class)` and `@JsonIgnoreProperties(ignoreUnknown = true)` to match the upstream JSON contract. X509 and Associate identity classes use default field naming with public fields instead.

## Header Decoding Pipeline

- Decode the `x-rh-identity` header exclusively through `HeaderHelper.getRhIdFromString()`. This method Base64-decodes the header value and deserializes into `XRhIdentity` using a dedicated `ObjectMapper` configured with `FAIL_ON_UNKNOWN_PROPERTIES = false`.
- Return `Optional.empty()` (not exceptions) from `HeaderHelper.getRhIdFromString()` when the header is null or malformed. The auth mechanism handles absent identity by setting subject and type to `"-unset-"`.

## Authentication Mechanism

- `RHIdAuthMechanism` implements Quarkus `HttpAuthenticationMechanism` and is the sole auth entry point. It reads the raw `x-rh-identity` header, delegates to `HeaderHelper`, and builds a `QuarkusSecurityIdentity` with an `RhIdPrincipal`.
- `RhIdPrincipal` carries both `name` (the subject string) and `type` (the identity type discriminator). Cast `SecurityContext.getUserPrincipal()` to `RhIdPrincipal` in resource methods to access `getType()`.

## Identity Type Checks in GwResource

- Check `principal.getType()` against the string constants `"X509"` and `"ServiceAccount"` (defined as private constants in `GwResource`) to determine whether source environment validation applies. Associate identity types skip source environment checks.
- Source environment validation calls `restValidationClient.validateCertificate()` using `principal.getName()` as the authentication data (subject DN for X509, username for ServiceAccount).

## Allow-List and Staging Guard

- The staging allow-list (`notifications.allow-list.enabled`, `notifications.allow-list.org-ids` in `GwConfig`) only blocks requests when all three conditions hold: allow-list is enabled, source environment is `"stage"`, and the org ID is not in the allow-list. Production source environments bypass the allow-list entirely.
- Prefer testing allow-list logic through `AllowListTest` patterns: use `@InjectMock GwConfig` and `@InjectMock @RestClient RestValidationClient`, and assert both HTTP status codes and JSON response body fields (`result`, `details`).

## Test Identity Construction

- Use `TestHelpers.encodeIdentityInfo(tenant, username)` to produce Base64-encoded X509 identity headers for integration tests. This helper builds an X509-type identity with `subject_dn` set to `/dn=<username>`.
- For non-X509 identity types in tests, manually construct the JSON, Base64-encode it, and pass it as the `x-rh-identity` header. Follow the patterns in `IdentityTest.java` (`testSamlIdentity`, `testX509Identity`, `testServiceAccountIdentity`).

## Verification

```bash
# Run identity deserialization tests
./mvnw test -pl . -Dtest=IdentityTest
# Run allow-list authorization tests
./mvnw test -pl . -Dtest=AllowListTest
# Run full gateway resource tests (includes identity header usage)
./mvnw test -pl . -Dtest=GwResourceTest
# Verify all identity subtypes are registered
grep -c "@JsonSubTypes.Type" src/main/java/com/redhat/cloud/notifications/auth/Identity.java
```
