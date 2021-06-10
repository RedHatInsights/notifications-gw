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

import io.quarkus.runtime.StartupEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.event.Observes;

/**
 * @author hrupp
 */
public class NotificationsGwApp {

    private static final String BUILD_COMMIT_ENV_NAME = "OPENSHIFT_BUILD_COMMIT";
    private static final String BUILD_REFERENCE_ENV_NAME = "OPENSHIFT_BUILD_REFERENCE";
    private static final String BUILD_NAME_ENV_NAME = "OPENSHIFT_BUILD_NAME";

    public static final String FILTER_REGEX = ".*(/health(/\\w+)?|/metrics) HTTP/[0-9].[0-9]\" 200.*\\n?";
    private static final Pattern pattern = Pattern.compile(FILTER_REGEX);

    @ConfigProperty(name = "quarkus.http.access-log.category")
    private String loggerName;

    private static final Logger LOG = Logger.getLogger(NotificationsGwApp.class);

    void init(@Observes StartupEvent ev) {
        initAccessLogFilter();
        showVersionInfo();
    }

    private void initAccessLogFilter() {
        java.util.logging.Logger accessLog = java.util.logging.Logger.getLogger(loggerName);
        accessLog.setFilter(record -> {
            final String logMessage = record.getMessage();
            Matcher matcher = pattern.matcher(logMessage);
            return !matcher.matches();
        });
    }

    private void showVersionInfo() {
        LOG.info("Starting notifications backend");
        String buildCommit = System.getenv(BUILD_COMMIT_ENV_NAME);
        if (buildCommit != null) {
            String osBuildRef = System.getenv(BUILD_REFERENCE_ENV_NAME);
            String osBuildName = System.getenv(BUILD_NAME_ENV_NAME);

            LOG.infof("\ts2i-build [%s]\n\tfrom branch [%s]\n\twith git sha [%s]", osBuildName, osBuildRef, buildCommit);
        }
    }

}
