package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.ingress.Type;
import com.redhat.cloud.notifications.model.SourceEnvironment;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.common.mapper.TypeRef;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import software.amazon.awssdk.http.HttpStatusCode;

import static com.redhat.cloud.notifications.GwResource.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.GwResource.MESSAGE_ID_HEADER;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyMap;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GwResourceTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    @RestClient
    RestValidationClient restValidationClient;

    @InjectMock
    @RestClient
    RestInternalClient restInternalClient;

    InMemorySink<String> inMemorySink;

    @InjectSpy
    GwConfig gwConfig;

    @InjectSpy
    GwResource gwResource;

    @CacheName("get-baets")
    Cache cacheBaet;

    @CacheName("get-certificates")
    Cache cacheCertificates;

    private static final String SOURCE_ENVIRONMENT_HEADER = "rh-source-environment";

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
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldReturnBadRequestWhenApplicationBundleAndEventTypeAreInvalid(final boolean isBulkCachesEnabled) {
        // Simulate that we received a bad response from the notifications
        // backend.
        final WebApplicationException wae = Mockito.mock(WebApplicationException.class);
        final Response mockedResponse = Mockito.mock(Response.class);

        // Prepare the mock calls to satisfy the error handlers.
        String errorMessage = "Error message returned from the backend";

        when(mockedResponse.readEntity(String.class)).thenReturn(errorMessage);
        when(mockedResponse.getStatus()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(wae.getResponse()).thenReturn(mockedResponse);
        when(this.restValidationClient.validate(anyString(), anyString(), anyString())).thenThrow(wae);
        when(this.restValidationClient.getBaets()).thenThrow(wae);
        when(gwConfig.isBulkCachesEnabled()).thenReturn(isBulkCachesEnabled);

        // Prepare the test payload to be sent to the gateway's endpoint.
        final RestAction ra = new RestAction();
        ra.setBundle("my-invalid-bundle");
        ra.setOrgId("123");
        ra.setApplication("my-invalid-app");
        ra.setEventType("a_invalid-type");

        if (isBulkCachesEnabled) {
            errorMessage = String.format("No event type found for [bundle=%s, application=%s, eventType=%s]",
                ra.getBundle(),
                ra.getApplication(),
                ra.getEventType());
        }

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

            when(mockedResponse.readEntity(String.class)).thenReturn(errorMessage);
            when(mockedResponse.getStatus()).thenReturn(testStatusCode);
            when(wae.getResponse()).thenReturn(mockedResponse);
            when(this.restValidationClient.validate(anyString(), anyString(), anyString())).thenThrow(wae);

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

    @ParameterizedTest
    @ValueSource(strings = {"/notifications/", "/api/notifications-gw/notifications"}) // to validate direct path and redirected path (used in ephemeral)
    public void testNotificationsEndpoint(String endpointPath) {
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
        recipient.setEmails(List.of("user3@redhat.com", "user4@redhat.com"));
        ra.setRecipients(recipients);

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String responseBody = given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post(endpointPath)
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
        assertEquals(List.of("user3@redhat.com", "user4@redhat.com"), r1.get("emails"));
    }

    @Test
    public void testNotificationsEndpointWithoutRecipient() {

        String uuid = UUID.randomUUID().toString();
        String application = "my-app";

        testSimplePayload(uuid, "my-bundle", application, "a_type", HttpStatusCode.OK);

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
        assertEquals(application, am.get("application"));
        List<Map<String, Object>> eventList = (List<Map<String, Object>>) am.get("events");
        assertEquals(1, eventList.size());
        Map<String, Object> eventR = eventList.get(0);
        Map<String, Object> payloadR = (Map<String, Object>) eventR.get("payload");
        assertEquals(2, payloadR.size());
        assertEquals(uuid, payloadR.get("uuid"));
    }

    void testSimplePayload(final String uuid, final String bundle, final String application, final String eventType, final int expectedStatusCode) {
        RestAction ra = new RestAction();
        ra.setBundle(bundle);
        ra.setOrgId("123");
        ra.setApplication(application);
        ra.setEventType(eventType);

        List<RestEvent> events = new ArrayList<>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        payload.put("uuid", uuid);
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
            .statusCode(expectedStatusCode);
    }
    @Test
    public void testNotificationsWithAndWithoutRecipientsAuthorizationCriterion() {
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
        RecipientsAuthorizationCriterion criterion = new RecipientsAuthorizationCriterion.RecipientsAuthorizationCriterionBuilder()
            .withId(RandomStringUtils.secure().nextAlphanumeric(10))
            .withRelation(RandomStringUtils.secure().nextAlphanumeric(10))
            .withType(new Type().builder().withNamespace(RandomStringUtils.secure().nextAlphanumeric(10))
                .withName(RandomStringUtils.secure().nextAlphanumeric(10)).build())
            .build();
        ra.setRecipientsAuthorizationCriterion(criterion);

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

        Map<String, Object> am = Json.decodeValue(message.getPayload(), Map.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RecipientsAuthorizationCriterion racFromAm = objectMapper.convertValue(am.get("recipients_authorization_criterion"), RecipientsAuthorizationCriterion.class);
        assertTrue(racFromAm.equals(ra.getRecipientsAuthorizationCriterion()));

        ra.setRecipientsAuthorizationCriterion(null);

        inMemorySink.clear();

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
        message = inMemorySink.received().get(0);
        am = Json.decodeValue(message.getPayload(), Map.class);
        assertFalse(am.containsKey("recipients_authorization_criterion"));
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
        action.setContext(emptyMap());
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

    @Test
    void testAllowedRecipientsEmails() {

        RestEvent event = new RestEvent();
        event.setMetadata(new RestMetadata());
        event.setPayload(Collections.emptyMap());
        List<RestEvent> events = List.of(event);

        RestRecipient recipient = new RestRecipient();
        recipient.setEmails(List.of("user1@redhat.com", "user2@redhat.com"));

        RestAction action = new RestAction();
        action.setBundle("bundle");
        action.setApplication("app");
        action.setEventType("event-type");
        action.setOrgId("123");
        action.setTimestamp("2023-12-13T09:20:08.473912");
        action.setContext(emptyMap());
        action.setEvents(events);
        action.setRecipients(List.of(recipient));

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String responseBody = given()
                .body(action)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200)
                .extract().asString();

        assertEquals("success", new JsonObject(responseBody).getString("result"));
    }

    @Test
    void testForbiddenRecipientsEmails() {

        RestEvent event = new RestEvent();
        event.setMetadata(new RestMetadata());
        event.setPayload(Collections.emptyMap());
        List<RestEvent> events = List.of(event);

        RestRecipient recipient = new RestRecipient();
        recipient.setEmails(List.of("user3@gmail.com", "user4@redhat.com", "user5@hotmail.com"));

        RestAction action = new RestAction();
        action.setBundle("bundle");
        action.setApplication("app");
        action.setEventType("event-type");
        action.setOrgId("123");
        action.setTimestamp("2023-12-13T09:20:08.473912");
        action.setContext(emptyMap());
        action.setEvents(events);
        action.setRecipients(List.of(recipient));

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String responseBody = given()
                .body(action)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(400)
                .extract().asString();
        JsonObject response = new JsonObject(responseBody);

        assertEquals("error", response.getString("result"));
        assertEquals("External email addresses are forbidden in the recipients.emails field: user3@gmail.com, user5@hotmail.com", response.getString("details"));
    }

    @Test
    void testRefreshBaet() {
        when(gwConfig.isBulkCachesEnabled()).thenReturn(true);

        String uuid = UUID.randomUUID().toString();
        String bundle = "my-bundle";
        String application = "my-app";
        String eventType = "my-event-type";

        // baet list is not yet loaded
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.BAD_REQUEST);
        verify(restValidationClient, times(1)).getBaets();
        verify(gwResource, times(1)).refreshSourceEnvironment();

        // baet list has been loaded
        when(restValidationClient.getBaets()).thenReturn(Map.of(bundle, Map.of(application, List.of(eventType))));
        cacheBaet.invalidateAll().await().indefinitely();
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        verify(restValidationClient, times(2)).getBaets();

        // Should use cached data
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        verify(restValidationClient, times(2)).getBaets();

        // baet list failed to refresh
        cacheBaet.invalidateAll().await().indefinitely();
        when(restValidationClient.getBaets()).thenThrow(WebApplicationException.class);
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        // we expect 7 calls = 2 from previous exec + 1 attempts + 5 retries
        verify(restValidationClient, times(8)).getBaets();
    }

    @Test
    void testRefreshEnvironments() {
        when(gwConfig.isBulkCachesEnabled()).thenReturn(true);

        String uuid = UUID.randomUUID().toString();
        String bundle = "my-bundle";
        String application = "my-app";
        String eventType = "my-event-type";

        SourceEnvironment sourceEnvironment = new SourceEnvironment();
        sourceEnvironment.name = "STAGE";
        sourceEnvironment.bundle = bundle;
        sourceEnvironment.application = application;
        sourceEnvironment.subjectDn = "/dn=user";

        cacheBaet.invalidateAll().await().indefinitely();
        when(restValidationClient.getBaets()).thenReturn(Map.of(bundle, Map.of(application, List.of(eventType))));

        // certificates list is not yet loaded
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        verify(restValidationClient, times(0)).getCertificates();

        // Message received
        Message<String> message = inMemorySink.received().get(0);

        // It should not contain a "rh-source-environment" header.
        Optional<KafkaMessageMetadata> messageMetadata = message.getMetadata(KafkaMessageMetadata.class);
        Iterator<Header> headers = messageMetadata.get().getHeaders().headers(SOURCE_ENVIRONMENT_HEADER).iterator();
        assertFalse(headers.hasNext());

        // certificates list loaded
        cacheCertificates.invalidateAll().await().indefinitely();
        when(restValidationClient.getCertificates()).thenReturn(List.of(sourceEnvironment));
        inMemorySink.clear();
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        verify(restValidationClient, times(1)).getCertificates();

        // Message received
        message = inMemorySink.received().get(0);

        // It should contain a "rh-source-environment" header.
        messageMetadata = message.getMetadata(KafkaMessageMetadata.class);
        headers = messageMetadata.get().getHeaders().headers(SOURCE_ENVIRONMENT_HEADER).iterator();
        String headerValue = new String(headers.next().value(), UTF_8);
        assertEquals(sourceEnvironment.name, headerValue);

        // certificates list failed to refresh
        cacheCertificates.invalidateAll().await().indefinitely();
        when(restValidationClient.getCertificates()).thenThrow(WebApplicationException.class);
        inMemorySink.clear();
        testSimplePayload(uuid, bundle, application, eventType, HttpStatusCode.OK);
        verify(restValidationClient, times(7)).getCertificates();

        // Message received
        message = inMemorySink.received().get(0);

        // It should contain a "rh-source-environment" header.
        messageMetadata = message.getMetadata(KafkaMessageMetadata.class);
        headers = messageMetadata.get().getHeaders().headers(SOURCE_ENVIRONMENT_HEADER).iterator();
        headerValue = new String(headers.next().value(), UTF_8);
        assertEquals(sourceEnvironment.name, headerValue);
    }

    @Test
    void testSearchSubscribedOrgByEventType() {
        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String bundle = RandomStringUtils.secure().nextAlphabetic(10);
        String application = RandomStringUtils.secure().nextAlphabetic(10);
        List<String> eventTypes = List.of(
            RandomStringUtils.secure().nextAlphabetic(10),
            RandomStringUtils.secure().nextAlphabetic(10),
            RandomStringUtils.secure().nextAlphabetic(10)
        );

        Map<String, List<String>> resultMap = given()
            .header("x-rh-identity", identity)
            .contentType(MediaType.APPLICATION_JSON)
            .pathParam("bundle", bundle)
            .pathParam("application", application)
            .param("eventTypeNames", eventTypes)
            .when()
            .get("/notifications/subscriptions/{bundle}/{application}")
            .then()
            .statusCode(HttpStatus.SC_OK).extract().as(new TypeRef<>() {
        });

        assertTrue(resultMap.isEmpty());
        verify(restInternalClient, times(1)).getOrgSubscriptionsPerEventType(eq(bundle), eq(application), eq(eventTypes));
    }
}
