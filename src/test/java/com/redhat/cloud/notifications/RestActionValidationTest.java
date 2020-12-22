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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author hrupp
 */
public class RestActionValidationTest {

    final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();

    @Test
    void testGood() {
        RestAction ra =new RestAction();
        ra.accountId="123";
        ra.application="policies";
        ra.eventType="policy_triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(0,violations.size(), violations.toString());
    }

    @Test
    void testBad1() {
        RestAction ra =new RestAction();
        ra.accountId="abc";
        ra.application="Hulla";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(4,violations.size());
    }

    @Test
    void testBad2() {
        RestAction ra =new RestAction();
        ra.accountId="";
        ra.application="policies";
        ra.eventType="triggered";
        ra.timestamp="2020-12-18T17:04:04.417921";

        Set<ConstraintViolation<RestAction>> violations = validator.validate(ra);
        assertEquals(2,violations.size());
    }

}
