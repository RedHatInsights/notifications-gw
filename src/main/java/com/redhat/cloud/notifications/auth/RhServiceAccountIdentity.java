package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.logging.Log;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RhServiceAccountIdentity extends Identity {

    @JsonProperty("org_id")
    private String orgId;

    @JsonProperty("service_account")
    private ServiceAccount serviceAccount;

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    @Override
    public String getSubject() {
        return getServiceAccount().getUsername();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceAccount {
        @JsonProperty("username")
        private String username;

        @JsonProperty("client_id")
        private String clientId;

        public String getClientId() {
            return clientId;
        }

        public String getUsername() {
            // be able to track client unique id just in case
            Log.debugf("Using subject %s, from id %s", username, clientId);
            return username;
        }
    }
}
