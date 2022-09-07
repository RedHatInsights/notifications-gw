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

import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RestActionValidationTest {

    final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();

    @Test
    void testGood1() {
        RestAction a = new RestAction();
        a.setId(UUID.fromString("9151f21f-dead-beef-baad-f4af67cdf544"));
        a.setBundle("my-bundle");
        a.setOrgId("123");
        a.setApplication("my-app");
        a.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event1 = new RestEvent();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", "value");
        event1.setMetadata(new RestMetadata());
        event1.setPayload(payload);
        a.setEvents(events);
        a.setTimestamp("2020-12-18T17:04:04.417921");
        a.setContext(new HashMap());

        Set<ConstraintViolation<RestAction>> violations = validator.validate(a);
        assertEquals(0,violations.size(), violations.toString());
    }

    @Test
    void testGood2() {
        RestAction a = new RestAction();
        a.setBundle("my-bundle");
        a.setAccountId("123");
        a.setOrgId("456");
        a.setApplication("my-app");
        a.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event1 = new RestEvent();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", "value");
        event1.setMetadata(new RestMetadata());
        event1.setPayload(payload);
        events.add(event1);

        RestEvent event2 = new RestEvent();
        event2.setMetadata(new RestMetadata());
        event2.setPayload(payload);
        events.add(event1);

        a.setEvents(events);
        a.setTimestamp("2020-12-18T17:04:04.417921");

        Set<ConstraintViolation<RestAction>> violations = validator.validate(a);
        assertEquals(0,violations.size(), violations.toString());
    }

    @Test
    void testBad1() {
        RestAction ra =new RestAction();
        ra.orgId="abc";
        ra.bundle="Coal";
        ra.application="Hulla";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(6,violations.size(), violations.toString());
    }

    @Test
    void testBad2() {
        RestAction ra =new RestAction();
        ra.orgId="";
        ra.bundle="insights";
        ra.application="policies";
        ra.eventType="triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(3,violations.size(), violations.toString());
    }

    @Test
    void testBad3() {
        RestAction ra =new RestAction();
        ra.orgId="123";
        ra.application="policies";
        ra.eventType="policy_triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(3,violations.size(), violations.toString());
    }

}
