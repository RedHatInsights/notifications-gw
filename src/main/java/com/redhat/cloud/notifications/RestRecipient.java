package com.redhat.cloud.notifications;

public class RestRecipient {

    private boolean ignoreUserPreferences;
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
