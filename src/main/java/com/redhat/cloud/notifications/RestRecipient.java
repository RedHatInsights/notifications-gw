package com.redhat.cloud.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RestRecipient {

    @JsonProperty("users")
    private List<String> users;

    @JsonProperty("ignore_user_preferences")
    private boolean ignoreUserPreferences;

    @JsonProperty("only_admins")
    private boolean onlyAdmins;

    public List<String> getUsers() {
        return users;
    }

    public void setUsers(List<String> users) {
        this.users = users;
    }

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
