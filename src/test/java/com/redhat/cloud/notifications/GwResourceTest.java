package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GwResourceTest {

    @Test
    public void testHelloEndpoint() throws InterruptedException {

        UUID random = UUID.randomUUID();

        RestAction ra = new RestAction();
        ra.setBundle("my-bundle");
        ra.setAccountId("123");
        ra.setApplication("my-app");
        ra.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event = new RestEvent();
        event.setPayload("{\"key1\" : \"value1\", \"uuid\" : \"" + random.toString() + "\"}");
        event.setMetadata("{}");
        events.add(event);
        ra.setEvents(events);
        ra.setTimestamp("2020-12-18T17:04:04.417921");
        ra.setContext("{}");

        String identity = TestHelpers.encodeIdentityInfo("test", "user");

        given()
                .body(ra)
                .header("x-rh-identity", identity)
                .contentType(MediaType.APPLICATION_JSON)
                .when().post("/notifications/")
                .then()
                .statusCode(200);
        
        // Now check if we got a message
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000L);
            if (TestReceiver.theAction != null) {
                Map<String,Object> am = TestReceiver.theAction;
                assertEquals(ra.application, am.get("application"));
                assertEquals(ra.accountId, am.get("account_id"));
                List<Map> eventList = (List<Map>) am.get("events");
                assertEquals(1, eventList.size());
                Map<String, Object> eventR = eventList.get(0);
                Map<String,String> payload = Json.decodeValue((String)eventR.get("payload"), Map.class);
                assertEquals(2, payload.size());
                assertEquals(random.toString(), payload.get("uuid"));

                return;
            }
        }
        fail("Should not have reached this");
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
        ra.application="this.is_a Application";

        given()
            .body(ra)
            .header("x-rh-identity", identity)
            .contentType(MediaType.APPLICATION_JSON)
            .when().post("/notifications/")
            .then()
            .statusCode(400);
    }
}
