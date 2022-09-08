/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author hrupp
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SamplesTest {

    @Test
    void testGetSample() {
        when()
                .get("/sample")
            .then()
                .statusCode(200);
    }

    @Test
    void testGood1() {
        RestAction ra =new RestAction();
        ra.setBundle("my-bundle");
        ra.setOrgId("123");
        ra.setApplication("my-app");
        ra.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", "value");
        event.setMetadata(new RestMetadata());
        event.setPayload(payload);
        events.add(event);
        ra.setEvents(events);
        ra.timestamp="2020-12-18T17:04:04.417921";

        given()
                .body(ra)
                .contentType(ContentType.JSON)
            .when()
                .post("/sample/verify")
            .then()
                .statusCode(200);
    }

    @Test
    void testGood2() {
        RestAction ra =new RestAction();
        ra.setId(UUID.fromString("9151f21f-dead-beef-92f3-f4af67cdf544"));
        ra.setBundle("my-bundle");
        ra.setOrgId("123");
        ra.setApplication("my-app");
        ra.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event1 = new RestEvent();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", "value");
        // event1.setMetadata(new RestMetadata());
        event1.setPayload(payload);
        events.add(event1);

        RestEvent event2 = new RestEvent();
        // event2.setMetadata(new RestMetadata());
        event2.setPayload(payload);
        events.add(event1);

        ra.setEvents(events);
        ra.timestamp="2020-12-18T17:04:04.417921";
        ra.setContext(new HashMap());

        given()
                .body(ra)
                .contentType(ContentType.JSON)
            .when()
                .post("/sample/verify")
            .then()
                .statusCode(200);
    }

    @Test
    void testBad() {
        RestAction ra =new RestAction();
        ra.accountId="123";
        ra.application="policies";
        ra.bundle="insights";
        ra.eventType="policy triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        given()
                .body(ra)
                .contentType(ContentType.JSON)
            .when()
                .post("/sample/verify")
            .then()
                .statusCode(400);
    }

    @Test
    void testBad2() {
        RestAction ra =new RestAction();
        ra.accountId="123";
        ra.application="policies";
        ra.eventType="policy triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        given()
                .body(ra)
                .contentType(ContentType.JSON)
                .when()
                .post("/sample/verify")
                .then()
                .statusCode(400);
    }

    @Test
    void testBadOrgId() {
        RestAction ra =new RestAction();
        ra.setOrgId("li la lu");
        ra.accountId="123";
        ra.application="policies";
        ra.eventType="policy triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        given()
                .body(ra)
                .contentType(ContentType.JSON)
                .when()
                .post("/sample/verify")
                .then()
                .statusCode(400);
    }
}
