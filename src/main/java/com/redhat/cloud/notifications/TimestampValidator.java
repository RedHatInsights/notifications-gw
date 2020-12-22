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

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author hrupp
 */
public class TimestampValidator implements ConstraintValidator<ISO8601Timestamp,String> {
    @Override
    public void initialize(ISO8601Timestamp constraintAnnotation) {
        // TODO: Customise this generated block
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.US));
            return true;
        } catch (Exception e) {
            System.err.println("Validation failed: " + e.getMessage());
            return false;
        }
    }
}
