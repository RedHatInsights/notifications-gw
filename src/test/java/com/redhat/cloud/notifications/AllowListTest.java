package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.model.SourceEnvironment;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.redhat.cloud.notifications.GwResource.ALLOW_LIST_ERROR_MESSAGE;
import static com.redhat.cloud.notifications.TestHelpers.encodeIdentityInfo;
import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.ok;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
public class AllowListTest {

    @InjectMock
    GwConfig gwConfig;

    @InjectMock
    @RestClient
    RestValidationClient restValidationClient;

    @BeforeEach
    @CacheInvalidateAll(cacheName = "certificate-validation")
    void beforeEach() {
        when(restValidationClient.validate(anyString(), anyString(), anyString())).thenReturn(ok().build());
    }

    @Test
    void testAllowListDisabledStage() {
        mock(false, "stage", emptyList());
        sendMessage("123", 200, "success", null);
    }

    @Test
    void testAllowListDisabledProd() {
        mock(false, "prod", emptyList());
        sendMessage("123", 200, "success", null);
    }

    @Test
    void testAllowListEnabledStageEmptyList() {
        mock(true, "stage", emptyList());
        String orgId = "123";
        String expectedDetails = String.format(ALLOW_LIST_ERROR_MESSAGE, orgId);
        sendMessage(orgId, 403, "error", expectedDetails);
    }

    @Test
    void testAllowListEnabledProdEmptyList() {
        mock(true, "prod", emptyList());
        sendMessage("123", 200, "success", null);
    }

    @Test
    void testAllowListEnabledStageUnknownOrgId() {
        mock(true, "stage", List.of("456", "789"));
        String orgId = "123";
        String expectedDetails = String.format(ALLOW_LIST_ERROR_MESSAGE, orgId);
        sendMessage(orgId, 403, "error", expectedDetails);
    }

    @Test
    void testAllowListEnabledProdUnknownOrgId() {
        mock(true, "prod", List.of("456", "789"));
        sendMessage("123", 200, "success", null);
    }

    private void mock(boolean allowListEnabled, String sourceEnv, List<String> allowListOrgIds) {
        when(gwConfig.isAllowListEnabled()).thenReturn(allowListEnabled);
        when(gwConfig.getAllowListOrgIds()).thenReturn(allowListOrgIds);

        SourceEnvironment sourceEnvironment = new SourceEnvironment();
        sourceEnvironment.name = sourceEnv;
        when(restValidationClient.validateCertificate(anyString(), anyString(), anyString())).thenReturn(sourceEnvironment);
    }

    private void sendMessage(String orgId, int expectedStatusCode, String expectedResult, String expectedDetails) {

        RestEvent event = new RestEvent();
        event.setPayload(emptyMap());
        event.setMetadata(new RestMetadata());

        RestAction action = new RestAction();
        action.setOrgId(orgId);
        action.setBundle("my-bundle");
        action.setApplication("my-app");
        action.setEventType("my-event-type");
        action.setTimestamp("2024-04-08T17:32:14.456702");
        action.setContext(emptyMap());
        action.setEvents(List.of(event));

        String responseBody = given()
                .body(action)
                .header("x-rh-identity", encodeIdentityInfo("test", "user"))
                .contentType(APPLICATION_JSON)
                .when().post("/notifications/")
                .then().statusCode(expectedStatusCode)
                .extract().asString();
        JsonObject response = new JsonObject(responseBody);

        assertEquals(expectedResult, response.getString("result"));
        if (expectedDetails != null) {
            assertEquals(expectedDetails, response.getString("details"));
        }
    }
}
