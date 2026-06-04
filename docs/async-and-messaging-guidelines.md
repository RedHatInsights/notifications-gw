# Async and Messaging Guidelines

## Kafka Producer Pattern

- Produce to Kafka exclusively through the `Emitter<String>` injected via `@Channel("egress")` in `GwResource`. Do not create additional Kafka producers or channels; this gateway has a single outgoing channel named `egress` writing to `platform.notifications.ingress`.
- Serialize the `Action` object to a `String` using `Parser.encode(action)` from the `insights-notification-schemas-java` library before emitting. Do not use custom serializers or send raw JSON; the downstream `notifications-backend` consumer expects this format.
- Attach a `rh-message-id` header (random UUID v4, UTF-8 encoded) to every outgoing Kafka message via `OutgoingKafkaRecordMetadata`. Conditionally attach a `rh-source-environment` header when the source environment is resolved for X509/ServiceAccount identity types.

## Synchronous Ack/Nack Callback

- Use `CompletableFuture<Void>` as the ack/nack callback when sending via `emitter.send(message)`. Block on `callback.get(callbackTimeout, TimeUnit.SECONDS)` to convert the async Kafka send into a synchronous HTTP response. The `callbackTimeout` defaults to 60 seconds (configurable via `notifications.kafka-callback-timeout-seconds`).
- Wire ack/nack handlers using `Message.of(payload).withAck(...).withNack(...)` so the `CompletableFuture` completes normally on ack and completes exceptionally on nack. Return HTTP 503 with a `{"result":"error","details":"..."}` JSON body on any Kafka delivery failure.

## Message Construction

- Build outgoing `Message<String>` instances through the static `buildMessageWithId` method in `GwResource`. Attach Kafka headers via `OutgoingKafkaRecordMetadata<String>` using `RecordHeaders`, not by modifying the message payload.
- Construct the `Action` object using its builder pattern (`Action.ActionBuilder`), mapping each field from the validated `RestAction` input. Convert `RestEvent` to `Event` using `Event.EventBuilder` (with a fresh `Metadata.MetadataBuilder` and `Payload.PayloadBuilder`), `RestRecipient` to `Recipient` using `Recipient.RecipientBuilder`, and context map entries via `Context.ContextBuilder.withAdditionalProperty`.

## Testing Kafka Messages

- Swap the `egress` channel to an in-memory connector for tests. `TestLifecycleManager` calls `InMemoryConnector.switchOutgoingChannelsToInMemory(EGRESS_CHANNEL)` and the test `application.properties` overrides the connector to `smallrye-in-memory`.
- Inject `InMemoryConnector` with `@Any` and obtain the sink via `inMemoryConnector.sink(EGRESS_CHANNEL)` in a `@PostConstruct` method. Clear the sink in `@BeforeEach` to isolate tests.
- Use Awaitility to poll for messages: `await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0)`. Do not use `Thread.sleep` or assume immediate delivery.
- Verify Kafka headers via `message.getMetadata(KafkaMessageMetadata.class)` and assert header presence, UUID format, and expected values.

## Caching and Bulk Validation

- When `notifications.bulk-caches.enabled` is `true`, the gateway validates event types and source environments against locally cached data instead of calling `RestValidationClient` per request. The caches `get-baets` and `get-certificates` use Caffeine with 1-hour TTL (`PT1H`). The per-request caches `baet-validation` and `certificate-validation` use 10-minute TTL (`PT10M`).
- Invalidate caches in tests using `@CacheName`-injected `Cache` instances and `cache.invalidateAll().await().indefinitely()` before asserting refresh behavior.

## Verification

```bash
# Run all tests (includes Kafka in-memory connector tests)
./mvnw test

# Run only the Kafka messaging tests
./mvnw test -Dtest=GwResourceTest

# Verify the project compiles and schemas are resolved
./mvnw compile
```
