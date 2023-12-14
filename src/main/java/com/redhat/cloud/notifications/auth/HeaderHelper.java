/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
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
package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

import java.util.Base64;
import java.util.Optional;

import static com.redhat.cloud.notifications.auth.RHIdAuthMechanism.X_RH_IDENTITY_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author hrupp
 */
public abstract class HeaderHelper {

    static ObjectMapper om = new ObjectMapper();
    static {
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static Optional<XRhIdentity> getRhIdFromString(String xRhIdHeader) {
        XRhIdentity rhIdentity;
        if (xRhIdHeader == null) {
            return Optional.empty();
        }
        try {
            String jsonString = new String(Base64.getDecoder().decode(xRhIdHeader.getBytes(UTF_8)), UTF_8);
            rhIdentity = om.readValue(jsonString, XRhIdentity.class);
        } catch (Exception e) {
            Log.warnf(e, "%s header deserialization failed: %s", X_RH_IDENTITY_HEADER, xRhIdHeader);
            return Optional.empty();
        }
        return Optional.ofNullable(rhIdentity);
    }
}
