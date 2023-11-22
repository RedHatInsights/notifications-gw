/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.quarkus.runtime.LaunchMode.TEST;

/**
 * @author hrupp
 */
@ApplicationScoped
public class NotificationsGwApp {

    private static final String NOTIFICATIONS_URL_KEY = "quarkus.rest-client.notifications-backend.url";
    public static final String ALLOWED_ORG_ID_LIST = "notifications.allowed.orgid.list";
    public static final String RESTRICT_ACCESS_BY_ORG_ID = "notifications.restrict.access.by.orgid";

    public static final String FILTER_REGEX = ".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?";
    private static final Pattern pattern = Pattern.compile(FILTER_REGEX);

    @ConfigProperty(name = "quarkus.http.access-log.category")
    private String loggerName;

    @ConfigProperty(name = RESTRICT_ACCESS_BY_ORG_ID, defaultValue = "true")
    boolean restrictAccessByOrgId;

    @ConfigProperty(name = ALLOWED_ORG_ID_LIST, defaultValue = "none")
    List<String> allowedOrgIdList;

    void init(@Observes StartupEvent ev) {
        initAccessLogFilter();

        Log.info(readGitProperties());

        Log.infof(NOTIFICATIONS_URL_KEY + "=%s", ConfigProvider.getConfig().getValue(NOTIFICATIONS_URL_KEY, String.class));
        Log.infof(ALLOWED_ORG_ID_LIST + "=%s", allowedOrgIdList);
        Log.infof(RESTRICT_ACCESS_BY_ORG_ID + "=%s", restrictAccessByOrgId);
    }

    private void initAccessLogFilter() {
        java.util.logging.Logger accessLog = java.util.logging.Logger.getLogger(loggerName);
        accessLog.setFilter(record -> {
            final String logMessage = record.getMessage();
            Matcher matcher = pattern.matcher(logMessage);
            return !matcher.matches();
        });
    }

    private String readGitProperties() {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream("git.properties");
        try {
            return readFromInputStream(inputStream);
        } catch (IOException e) {
            Log.error("Could not read git.properties.", e);
            return "Version information could not be retrieved";
        }
    }

    private String readFromInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "git.properties file not available";
        }
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("#Generated")) {
                    resultStringBuilder.append(line);
                }
            }
        }
        return resultStringBuilder.toString();
    }
}
