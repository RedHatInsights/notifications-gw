package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.quarkus.logging.Log;

@JsonNaming(SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RhServiceAccountIdentity extends Identity {

    private String orgId;

    private ServiceAccount serviceAccount;

    public ServiceAccount getServiceAccount() {
        return serviceAccount;
    }

    @Override
    public String getSubject() {
        return getServiceAccount().getUsername();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(SnakeCaseStrategy.class)
    public static class ServiceAccount {
        private String username;

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
