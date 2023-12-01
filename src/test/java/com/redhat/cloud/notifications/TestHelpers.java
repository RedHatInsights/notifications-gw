package com.redhat.cloud.notifications;

import io.vertx.core.json.JsonObject;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import static org.junit.Assert.fail;

public class TestHelpers {
    public static String encodeIdentityInfo(String tenant, String username) {
        JsonObject identity = new JsonObject();
        JsonObject x509 = new JsonObject();
        x509.put("subject_dn", "/dn="+username);
        x509.put("issuer_dn","/dn=Acme/o=org");
        identity.put("x509", x509);
        identity.put("type", "X509");
        JsonObject header = new JsonObject();
        header.put("identity", identity);

        String xRhEncoded = null;
        try {
            xRhEncoded = new String(Base64.getEncoder().encode(header.encode().getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            fail();
        }
        return xRhEncoded;
    }
}
