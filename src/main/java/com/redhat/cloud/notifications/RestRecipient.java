package com.redhat.cloud.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RestRecipient {

    @JsonProperty("ignore_user_preferences")
    private boolean ignoreUserPreferences;

    @JsonProperty("only_admins")
    private boolean onlyAdmins;

    public boolean isIgnoreUserPreferences() {
        return ignoreUserPreferences;
    }

    public void setIgnoreUserPreferences(boolean ignoreUserPreferences) {
        this.ignoreUserPreferences = ignoreUserPreferences;
    }

    public boolean isOnlyAdmins() {
        return onlyAdmins;
    }

    public void setOnlyAdmins(boolean onlyAdmins) {
        this.onlyAdmins = onlyAdmins;
    }
}
