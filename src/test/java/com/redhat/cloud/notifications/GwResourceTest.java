package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
        ra.accountId = "42";
        ra.bundle = "bundle-test";
        ra.application = "test";
        ra.eventType = "hulla";
        ra.payload = new HashMap();
        ra.payload.put("key1", "value1");
        ra.payload.put("uuid",random);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        ra.timestamp = LocalDateTime.now().format(formatter);
        ra.setTimestamp("2020-12-11T12:13:03.507753");

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
                Map<String,String> payload = Json.decodeValue((String)am.get("payload"), Map.class);
                assertEquals(2, payload.size());
                assertEquals(random.toString(), payload.get("uuid"));

                return;
            }
        }
        fail("Should not have reached this");
    }
}
