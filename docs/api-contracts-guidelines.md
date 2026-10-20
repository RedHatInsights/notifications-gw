# API Contracts Guidelines

## RestAction Payload Contract

- Use snake_case JSON field names for the `POST /notifications/` payload: `event_type`, `account_id`, `org_id`, `recipients_authorization_criterion`. The `@JsonProperty` annotations in `RestAction.java` define the canonical wire names.
- Format `bundle`, `application`, and `event_type` values with the regex `[a-z][a-z_0-9-]*` (lowercase start, then lowercase letters, digits, underscores, hyphens). Uppercase or spaces cause a 400 response.
- Format `timestamp` as ISO 8601 local date-time without timezone (e.g., `2020-12-18T17:04:04.417921`). The `TimestampValidator` parses with `DateTimeFormatter.ISO_LOCAL_DATE_TIME`; including a timezone offset causes validation failure.
- Provide `org_id` as a non-empty string of digits (validated with `@Positive`). Despite being a `String` type, non-numeric values are rejected. The `account_id` field follows the same `@Positive` constraint but is optional.
- Include at least an empty `events` list (`@NotNull`). Each `RestEvent.payload` must be a non-empty map (`@NotNull @NotEmpty`). The `metadata` field on `RestEvent` is optional and currently maps to an empty `RestMetadata` class.
- Keep `context` as a `Map<String, Object>`. It is not validated but is required for successful Kafka serialization -- the builder calls `contextBuilder.withAdditionalProperty` on each entry.
- Validate `recipients[].emails` entries against `^\S+@\S+\.\S+$`. When `notifications.emails.internal-only.enabled` is true (the default), only `@redhat.com` email addresses are accepted; external addresses return a 400 with the offending emails listed.

## Response Envelope

- Expect all `POST /notifications/` responses to use the JSON envelope `{"result": "success"|"error", "details": "..."}`. The `details` field is omitted on success.
- Map HTTP status codes as: 200 = forwarded, 400 = validation or event-type lookup failure, 403 = allow-list rejection for staging source environments, 503 = Kafka delivery failure or notifications-backend unreachable.

## Authentication and Identity

- Send the `x-rh-identity` header as a Base64-encoded JSON object with an `identity` wrapper. The `identity.type` field dispatches to `X509Identity` (type `X509`), `SamlIdentity` (type `Associate`), or `RhServiceAccountIdentity` (type `ServiceAccount`).

## Internal REST Client Contracts

- The `RestValidationClient` calls `notifications-backend` at `/internal/validation/baet` to validate bundle/application/event_type triples. When bulk caches are enabled (`notifications.bulk-caches.enabled=true`), it instead calls `/internal/validation/baet_list` via `getBaets()`, with results cached for 1 hour in `refreshBaets()`.
- The `RestInternalClient` exposes `GET /internal/gw/subscriptions/{bundle}/{application}` with `eventTypeNames` as a repeated query parameter. The `GwResource` re-exposes this at `GET /notifications/subscriptions/{bundleName}/{applicationName}`.

## Ephemeral Environment Path

- The `IncomingRequestInterceptor` rewrites requests from `/api/notifications-gw/notifications` to `/notifications` for ephemeral environments where 3scale is not available. Prefer using `/notifications/` as the canonical path in tests.

## Adding New Fields to RestAction

- Add new optional fields without `@NotNull` to avoid breaking existing callers. Include a `@JsonProperty("snake_case_name")` annotation when the Java field name differs from the wire name.
- When a new field must propagate to Kafka, update both the `RestAction` mapping in `GwResource.forward()` and the corresponding Avro builder call (e.g., `builder.withNewField(ra.newField)`).
- Prefer adding `@Valid` on collection fields whose elements carry their own validation constraints (as done for `recipients`).

## Verification

```bash
# Run all tests including API contract validation
./mvnw test
# Run only the RestAction validation tests
./mvnw test -Dtest=RestActionValidationTest
# Run the gateway endpoint integration tests
./mvnw test -Dtest=GwResourceTest
# Run sample endpoint tests
./mvnw test -Dtest=SamplesTest
```
