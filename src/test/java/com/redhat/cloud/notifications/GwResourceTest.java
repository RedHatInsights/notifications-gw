package com.redhat.cloud.notifications;

import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.GwResource.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.GwResource.MESSAGE_ID_HEADER;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GwResourceTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    @RestClient
    RestValidationClient restValidationClient;

    InMemorySink<String> inMemorySink;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(EGRESS_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
    }

    /**
     * Tests that the gateway returns a bad request with the error message that
     * the notifications backend gave us.
     */
    @Test
    void shouldReturnBadRequestWhenApplicationBundleAndEventTypeAreInvalid() {
        // Simulate that we received a bad response from the notifications
        // backend.
        final WebApplicationException wae = Mockito.mock(WebApplicationException.class);
        final Response mockedResponse = Mockito.mock(Response.class);

        // Prepare the mock calls to satisfy the error handlers.
        final String errorMessage = "Error message returned from the backend";

        Mockito.when(mockedResponse.readEntity(String.class)).thenReturn(errorMessage);
        Mockito.when(mockedResponse.getStatus()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        Mockito.when(wae.getResponse()).thenReturn(mockedResponse);
        Mockito.when(this.restValidationClient.validate(anyString(), anyString(), anyString())).thenThrow(wae);

        // Prepare the test payload to be sent to the gateway's endpoint.
        final RestAction ra = new RestAction();
        ra.setBundle("my-invalid-bundle");
        ra.setOrgId("123");
        ra.setApplication("my-invalid-app");
        ra.setEventType("a_invalid-type");

        final List<RestEvent> events = new ArrayList<>();
        ra.setEvents(events);
        ra.setTimestamp("2020-12-18T17:04:04.417921");

        final String identity = TestHelpers.encodeIdentityInfo("test", "user");

        // Call the endpoint under test.
        final String responseBody = given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(400)
                .contentType(JSON)
                .extract().asString();

        final String expectedResponse = String.format("{\"result\":\"error\",\"details\":\"%s\"}", errorMessage);
        assertEquals(expectedResponse, responseBody);
    }

    /**
     * Tests that the gateway returns a service unavailable error when the
     * notifications backend returns errors that are not of the bad request
     * family of errors.
     */
    @Test
    public void shouldReturnServiceUnavailableForNonBadRequest() {
        final List<Integer> testStatusCodes = List.of(
            HttpStatus.SC_INTERNAL_SERVER_ERROR,
            HttpStatus.SC_NOT_IMPLEMENTED,
            HttpStatus.SC_BAD_GATEWAY,
            HttpStatus.SC_SERVICE_UNAVAILABLE,
            HttpStatus.SC_GATEWAY_TIMEOUT,
            HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
            HttpStatus.SC_INSUFFICIENT_STORAGE
        );

        for (final int testStatusCode : testStatusCodes) {
            // Simulate that we received a bad response from the notifications
            // backend.
            final WebApplicationException wae = Mockito.mock(WebApplicationException.class);
            final Response mockedResponse = Mockito.mock(Response.class);

            // Prepare the mock calls to satisfy the error handlers.
            final String errorMessage = "Unable to validate the message, please try again later";

            Mockito.when(mockedResponse.readEntity(String.class)).thenReturn(errorMessage);
            Mockito.when(mockedResponse.getStatus()).thenReturn(testStatusCode);
            Mockito.when(wae.getResponse()).thenReturn(mockedResponse);
            Mockito.when(this.restValidationClient.validate(anyString(), anyString(), anyString())).thenThrow(wae);

            // Prepare the test payload to be sent to the gateway's endpoint.
            final RestAction ra = new RestAction();
            ra.setBundle("my-invalid-bundle");
            ra.setOrgId("123");
            ra.setApplication("my-invalid-app");
            ra.setEventType("a_invalid-type");

            final List<RestEvent> events = new ArrayList<>();
            ra.setEvents(events);
            ra.setTimestamp("2020-12-18T17:04:04.417921");

            final String identity = TestHelpers.encodeIdentityInfo("test", "user");

            // Call the endpoint under test.
            final String responseBody = given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE)
                .extract().asString();

            final String expectedResponse = String.format("{\"result\":\"error\",\"details\":\"%s\"}", errorMessage);
            assertEquals(expectedResponse, responseBody);

            Mockito.reset(wae, mockedResponse, this.restValidationClient);
        }
    }

    @Test
    public void testNotificationsEndpoint() {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setId(UUID.fromString("9151f21f-dead-beef-92f3-f4af67cdf544"));
        ra.setBundle("my-bundle");
        ra.setOrgId("123");
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
        recipient.setEmails(List.of("user3@domain.com", "user4@domain.com"));
        ra.setRecipients(recipients);

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String responseBody = given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200)
                .extract().asString();

        assertEquals("success", new JsonObject(responseBody).getString("result"));

        // Now check if we got a message
        await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0);

        // Message received!
        Message<String> message = inMemorySink.received().get(0);

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
        assertEquals(List.of(), r0.get("emails"));

        Map<String, Object> r1 = recipientList.get(1);
        assertEquals(Boolean.FALSE, r1.get("only_admins"));
        assertEquals(Boolean.TRUE, r1.get("ignore_user_preferences"));
        assertEquals(List.of("user3", "user4"), r1.get("users"));
        assertEquals(List.of("user3@domain.com", "user4@domain.com"), r1.get("emails"));
    }

    @Test
    public void testNotificationsEndpointWithoutRecipient() {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setBundle("my-bundle");
        ra.setOrgId("123");
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
        await().atMost(Duration.ofSeconds(10L)).until(() -> inMemorySink.received().size() > 0);

        // Message received!
        Message<String> message = inMemorySink.received().get(0);

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

    @Test
    void testInvalidEmail() {

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        RestEvent event = new RestEvent();
        event.setMetadata(new RestMetadata());
        event.setPayload(Map.of("key", "value"));

        RestAction action = new RestAction();
        action.setBundle("my-bundle");
        action.setApplication("my-app");
        action.setEventType("my-event-type");
        action.setOrgId("123");
        action.setTimestamp("2023-10-31T08:52:14.987723");
        action.setContext(Collections.emptyMap());
        action.setEvents(List.of(event));

        // The payload is valid.
        given()
                .body(action)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200);

        RestRecipient recipient = new RestRecipient();
        recipient.setEmails(List.of("invalid-email"));
        action.setRecipients(List.of(recipient));

        // Then after adding an invalid email recipient, the payload should no longer be valid.
        String responseBody = given()
                .body(action)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(400)
                .extract().asString();

        assertTrue(responseBody.contains("recipients[0].emails[0]"));
    }
}
