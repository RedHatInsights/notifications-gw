package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import io.vertx.core.json.Json;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.GwResource.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.GwResource.MESSAGE_ID_HEADER;
import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GwResourceTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Test
    void shouldReturn404WhenApplicationBundleAndEventTypeAreInvalid() {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setBundle("my-invalid-bundle");
        ra.setAccountId("123");
        ra.setApplication("my-invalid-app");
        ra.setEventType("a_invalid-type");

        List<RestEvent> events = new ArrayList<>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("uuid", random.toString());
        event.setMetadata(new RestMetadata());
        event.setPayload(payload);
        events.add(event);
        ra.setEvents(events);
        ra.setTimestamp("2020-12-18T17:04:04.417921");
        ra.setContext(new HashMap<>());

        List<RestRecipient> recipients = new ArrayList<>();
        RestRecipient recipient = new RestRecipient();
        recipient.setOnlyAdmins(true);
        recipient.setIgnoreUserPreferences(false);
        recipients.add(recipient);
        recipient = new RestRecipient();
        recipient.setOnlyAdmins(false);
        recipient.setIgnoreUserPreferences(true);
        recipient.setUsers(List.of("user3", "user4"));
        recipients.add(recipient);
        ra.setRecipients(recipients);

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(404);
    }

    @Test
    public void testNotificationsEndpoint() {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setId(UUID.fromString("9151f21f-dead-beef-92f3-f4af67cdf544"));
        ra.setBundle("my-bundle");
        ra.setAccountId("123");
        ra.setOrgId("2345678");
        ra.setApplication("my-app");
        ra.setEventType("a_type");

        List<RestEvent> events = new ArrayList<>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("uuid", random.toString());
        event.setMetadata(new RestMetadata());
        event.setPayload(payload);
        events.add(event);
        ra.setEvents(events);
        ra.setTimestamp("2020-12-18T17:04:04.417921");
        ra.setContext(new HashMap<>());

        List<RestRecipient> recipients = new ArrayList<>();
        RestRecipient recipient = new RestRecipient();
        recipient.setOnlyAdmins(true);
        recipient.setIgnoreUserPreferences(false);
        recipients.add(recipient);
        recipient = new RestRecipient();
        recipient.setOnlyAdmins(false);
        recipient.setIgnoreUserPreferences(true);
        recipients.add(recipient);
        recipient.setUsers(List.of("user3", "user4"));
        ra.setRecipients(recipients);

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200);

        // Now check if we got a message
        InMemorySink<String> inMemorySink = inMemoryConnector.sink(EGRESS_CHANNEL);
        await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0);

        // Message received!
        Message<String> message = inMemorySink.received().get(0);
        inMemorySink.clear();

        // It should contain a "rh-message-id" header and its value should be a valid UUID version 4.
        Optional<KafkaMessageMetadata> messageMetadata = message.getMetadata(KafkaMessageMetadata.class);
        Iterator<Header> headers = messageMetadata.get().getHeaders().headers(MESSAGE_ID_HEADER).iterator();
        String headerValue = new String(headers.next().value(), UTF_8);
        // Is the header value a valid UUID? The following line will throw an exception otherwise.
        UUID.fromString(headerValue);
        // If the UUID version is 4, then its 15th character has to be "4".
        assertEquals("4", headerValue.substring(14, 15));

        Map<String, Object> am = Json.decodeValue(message.getPayload(), Map.class);
        assertEquals(ra.application, am.get("application"));
        assertEquals(ra.accountId, am.get("account_id"));
        assertEquals(ra.orgId, am.get("org_id"));
        assertEquals(ra.id.toString(), am.get("id"));
        List<Map<String, Object>> eventList = (List<Map<String, Object>>) am.get("events");
        assertEquals(1, eventList.size());

        Map<String, Object> eventR = eventList.get(0);
        Map<String, Object> payloadR = (Map<String, Object>) eventR.get("payload");
        assertEquals(2, payloadR.size());
        assertEquals(random.toString(), payloadR.get("uuid"));

        List<Map<String, Object>> recipientList = (List<Map<String, Object>>) am.get("recipients");
        assertEquals(2, recipientList.size());
        Map<String, Object> r0 = recipientList.get(0);
        assertEquals(Boolean.TRUE, r0.get("only_admins"));
        assertEquals(Boolean.FALSE, r0.get("ignore_user_preferences"));
        assertEquals(List.of(), r0.get("users"));

        Map<String, Object> r1 = recipientList.get(1);
        assertEquals(Boolean.FALSE, r1.get("only_admins"));
        assertEquals(Boolean.TRUE, r1.get("ignore_user_preferences"));
        assertEquals(List.of("user3", "user4"), r1.get("users"));
    }

    @Test
    public void testNotificationsEndpointWithoutRecipient() {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setBundle("my-bundle");
        ra.setAccountId("123");
        ra.setApplication("my-app");
        ra.setEventType("a_type");

        List<RestEvent> events = new ArrayList<>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("uuid", random.toString());
        event.setMetadata(new RestMetadata());
        event.setPayload(payload);
        events.add(event);
        ra.setEvents(events);
        ra.setTimestamp("2020-12-18T17:04:04.417921");
        ra.setContext(new HashMap<>());

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200);

        // Now check if we got a message
        InMemorySink<String> inMemorySink = inMemoryConnector.sink(EGRESS_CHANNEL);
        await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0);

        // Message received!
        Message<String> message = inMemorySink.received().get(0);
        inMemorySink.clear();

        // It should contain a "rh-message-id" header and its value should be a valid UUID version 4.
        Optional<KafkaMessageMetadata> messageMetadata = message.getMetadata(KafkaMessageMetadata.class);
        Iterator<Header> headers = messageMetadata.get().getHeaders().headers(MESSAGE_ID_HEADER).iterator();
        String headerValue = new String(headers.next().value(), UTF_8);
        // Is the header value a valid UUID? The following line will throw an exception otherwise.
        UUID.fromString(headerValue);
        // If the UUID version is 4, then its 15th character has to be "4".
        assertEquals("4", headerValue.substring(14, 15));

        Map<String, Object> am = Json.decodeValue(message.getPayload(), Map.class);
        assertEquals(ra.application, am.get("application"));
        assertEquals(ra.accountId, am.get("account_id"));
        List<Map<String, Object>> eventList = (List<Map<String, Object>>) am.get("events");
        assertEquals(1, eventList.size());
        Map<String, Object> eventR = eventList.get(0);
        Map<String, Object> payloadR = (Map<String, Object>) eventR.get("payload");
        assertEquals(2, payloadR.size());
        assertEquals(random.toString(), payloadR.get("uuid"));
    }

    @Test
    void noEmptyContent() {

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        given()
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(400);
    }

    @Test
    void testValidData() {

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        RestAction ra = new RestAction();
        ra.application = "this.is_a Application";

        given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(400);
    }
}
