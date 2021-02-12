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
    void testGood() {
        RestAction ra =new RestAction();
        ra.accountId="123";
        ra.bundle="insights";
        ra.application="policies";
        ra.eventType="policy_triggered";
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
    void testBad() {
        RestAction ra =new RestAction();
        ra.accountId="123";
        ra.application="policies";
        ra.application="insights";
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
}
