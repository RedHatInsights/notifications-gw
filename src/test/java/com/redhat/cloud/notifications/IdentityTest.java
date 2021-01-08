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

import com.redhat.cloud.notifications.auth.HeaderHelper;
import com.redhat.cloud.notifications.auth.XRhIdentity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Optional;


/**
 * @author hrupp
 */
public class IdentityTest {

    @Test
    void testSamlIdentity() {

    String header =
        "{\n" +
        "  \"identity\": {\n" +
        "    \"associate\": {\n" +
        "      \"Role\": [\n" +
        "        \"some-ldap-group\",\n" +
        "        \"another-ldap-group\"\n" +
        "      ],\n" +
        "      \"email\": \"jschmoe@redhat.com\",\n" +
        "      \"givenName\": \"Joseph\",\n" +
        "      \"rhatUUID\": \"01234567-89ab-cdef-0123-456789abcdef\",\n" +
        "      \"surname\": \"Schmoe\"\n" +
        "    },\n" +
        "    \"auth_type\": \"saml-auth\",\n" +
        "    \"type\": \"Associate\"\n" +
        "  }\n" +
        "}\n";


        String xRhEncoded = null;
        try {
            xRhEncoded = new String(Base64.getEncoder().encode(header.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            Assertions.fail();
        }

        Optional<XRhIdentity> id = HeaderHelper.getRhIdFromString(xRhEncoded);
        Assertions.assertTrue(id.isPresent());
        XRhIdentity xid = id.get();
        Assertions.assertEquals("Associate",xid.identity.type);
        Assertions.assertEquals("jschmoe@redhat.com", xid.getSubject());

    }

    @Test
    void testX509Identity() {
        String header =
                "{\n" +
                "  \"identity\": {\n" +
                "    \"x509\": {\n" +
                "      \"subject_dn\": \"/CN=some-host.example.com\",\n" +
                "      \"issuer_dn\": \"/CN=certificate-authority.example.com\"\n" +
                "    },\n" +
                "    \"auth_type\": \"X509\",\n" +
                "    \"type\": \"X509\"\n" +
                "  }\n" +
                "}\n";

        String xRhEncoded = null;
        try {
            xRhEncoded = new String(Base64.getEncoder().encode(header.getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            Assertions.fail();
        }

        Optional<XRhIdentity> id = HeaderHelper.getRhIdFromString(xRhEncoded);
        Assertions.assertTrue(id.isPresent());
        XRhIdentity xid = id.get();
        Assertions.assertEquals("X509",xid.identity.type);
        Assertions.assertEquals("/CN=some-host.example.com",xid.getSubject());

    }
}
