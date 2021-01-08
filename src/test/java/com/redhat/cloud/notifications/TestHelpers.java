package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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

    public static Header createIdentityHeader(String tenant, String username) {
        return new Header("x-rh-identity", encodeIdentityInfo(tenant, username));
    }

    public static Header createIdentityHeader(String encodedIdentityHeader) {
        return new Header("x-rh-identity", encodedIdentityHeader);
    }

    public static String getFileAsString(String filename) {
        try {
            InputStream is = TestHelpers.class.getClassLoader().getResourceAsStream(filename);
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Failed to read rhid example file: " + e.getMessage());
            return "";
        }
    }

    public static String serializeAction(Action action) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(Action.getClassSchema(), baos);
        DatumWriter<Action> writer = new SpecificDatumWriter<>(Action.class);
        writer.write(action, jsonEncoder);
        jsonEncoder.flush();

        return baos.toString(StandardCharsets.UTF_8);
    }
}
