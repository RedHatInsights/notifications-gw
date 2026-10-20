package com.redhat.cloud.notifications;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public class RestRecipient {

    private static final String EMAIL_VALIDATION_REGEX = "^\\S+@\\S+\\.\\S+$";

    @JsonProperty("users")
    private List<String> users;

    @JsonProperty("ignore_user_preferences")
    private boolean ignoreUserPreferences;

    @JsonProperty("only_admins")
    private boolean onlyAdmins;

    @JsonProperty("emails")
    private List<@Pattern(regexp = EMAIL_VALIDATION_REGEX) String> emails;

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

    public List<String> getEmails() {
        return emails;
    }

    public void setEmails(List<String> emails) {
        this.emails = emails;
    }
}
