package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class GwConfig {

    @ConfigProperty(name = "notifications.allow-list.enabled", defaultValue = "false")
    boolean allowListEnabled;

    @ConfigProperty(name = "notifications.allow-list.org-ids", defaultValue = "[]")
    List<String> allowListOrgIds;

    @ConfigProperty(name = "notifications.bulk-caches.enabled", defaultValue = "false")
    boolean bulkCachesEnabled;

    public boolean isAllowListEnabled() {
        return allowListEnabled;
    }

    public List<String> getAllowListOrgIds() {
        return allowListOrgIds;
    }

    public boolean isBulkCachesEnabled() { return bulkCachesEnabled; }

    public void logConfiguration() {
        Log.infof("notifications.bulk-caches.enabled = %s", bulkCachesEnabled);
        Log.infof("notifications.allow-list.enabled = %s", allowListEnabled);
        Log.infof("notifications.allow-list.org-ids = %s", allowListOrgIds);
    }
}
