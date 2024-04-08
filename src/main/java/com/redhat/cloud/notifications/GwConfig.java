package com.redhat.cloud.notifications;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class GwConfig {

    @ConfigProperty(name = "notifications.allow-list.enabled", defaultValue = "false")
    boolean allowListEnabled;

    @ConfigProperty(name = "notifications.allow-list.org-ids", defaultValue = "[]")
    List<String> allowListOrgIds;

    public boolean isAllowListEnabled() {
        return allowListEnabled;
    }

    public List<String> getAllowListOrgIds() {
        return allowListOrgIds;
    }
}
