package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.GwResource.EGRESS_CHANNEL;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestProfile(GwResourceOrgIdFilterTest.GwOrgIdFilterTestProfile.class)
public class GwResourceOrgIdFilterTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    InMemorySink<String> inMemorySink;

    @PostConstruct
    void postConstruct() {
        inMemorySink = inMemoryConnector.sink(EGRESS_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        inMemorySink.clear();
    }

    public static class GwOrgIdFilterTestProfile implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("notifications.restrict.access.by.orgid", "true",
                "notifications.allowed.orgid.list", "123456,654321",
                "notifications.allowed.event.type.list", "my-allowed-bundle:my-allowed-app:my-allowed-event-type,otherbundle:otherapp:otherevent");
        }
    }

    @Test
    public void testOrgIdFilter() {
        final String allowedOrgId = "123456";
        final String notAllowedOrgId = "555555";
        final String[] allowedBaet = new String[]{"my-allowed-bundle", "my-allowed-app", "my-allowed-event-type"};
        final String[] notAllowedBaet = new String[]{"my-bundle", "my-app", "a_type"};

        // Allowed org_id, not allowed event-type
        assertRequest(
                buildRestAction(allowedOrgId, notAllowedBaet[0], notAllowedBaet[1], notAllowedBaet[2]),
                200,
                "success"
        );

        // not allowed org_id, not allowed event-type
        assertRequest(
                buildRestAction(notAllowedOrgId, notAllowedBaet[0], notAllowedBaet[1], notAllowedBaet[2]),
                403,
                "error"
        );

        // Allowed org_id, allowed event_type
        assertRequest(
                buildRestAction(allowedOrgId, allowedBaet[0], allowedBaet[1], allowedBaet[2]),
                200,
                "success"
        );

        // not allowed org_id, allowed event_type
        assertRequest(
                buildRestAction(notAllowedOrgId, allowedBaet[0], allowedBaet[1], allowedBaet[2]),
                200,
                "success"
        );
    }

    private RestAction buildRestAction(String orgId, String bundle, String app, String eventType) {
        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setId(UUID.fromString("9151f21f-dead-beef-92f3-f4af67cdf544"));
        ra.setBundle(bundle);
        ra.setOrgId(orgId);
        ra.setApplication(app);
        ra.setEventType(eventType);

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

        return ra;
    }

    private void assertRequest(RestAction ra, int expectedStatusCode, String expectedResult) {
        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        String responseBody = given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(expectedStatusCode)
                .extract().asString();

        assertEquals(expectedResult, new JsonObject(responseBody).getString("result"));
    }

}
