package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.MediaType;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GwResourceTest {

    @Disabled("Class loading issues in Quarkus testing apparently")
    @Test
    public void testHelloEndpoint() {

        RestAction ra = new RestAction();
        ra.accountId="42";
        ra.bundle="bundle-test";
        ra.application="test";
        ra.eventType="hulla";
        ra.payload = new HashMap();
        ra.payload.put("key1","value1");
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        ra.timestamp=LocalDateTime.now().format(formatter);
        ra.setTimestamp("2020-12-11T12:13:03.507753");

        String identity = TestHelpers.encodeIdentityInfo("test","user");

        given()
            .body(ra)
            .header("x-rh-identity",identity)
            .contentType(MediaType.APPLICATION_JSON)
          .when().post("/notifications/")
          .then()
             .statusCode(200);
    }

}
