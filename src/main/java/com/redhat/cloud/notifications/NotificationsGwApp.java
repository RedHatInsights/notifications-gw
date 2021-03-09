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
import org.jboss.logging.Logger;

import javax.enterprise.event.Observes;

/**
 * @author hrupp
 */
public class NotificationsGwApp {
    private static final String BUILD_COMMIT_ENV_NAME = "OPENSHIFT_BUILD_COMMIT";
    private static final String BUILD_REFERENCE_ENV_NAME = "OPENSHIFT_BUILD_REFERENCE";
    private static final String BUILD_NAME_ENV_NAME = "OPENSHIFT_BUILD_NAME";

    private static final Logger LOG = Logger.getLogger(NotificationsGwApp.class);

    void init(@Observes StartupEvent ev) {
        showVersionInfo();
    }

    private void showVersionInfo() {
        LOG.info("Starting notifications gw");
        String buildCommit = System.getenv(BUILD_COMMIT_ENV_NAME);
        if (buildCommit != null) {
            String osBuildRef = System.getenv(BUILD_REFERENCE_ENV_NAME);
            String osBuildName = System.getenv(BUILD_NAME_ENV_NAME);

            LOG.infof("\ts2i-build [%s]\n\tfrom branch [%s]\n\twith git sha [%s]", osBuildName, osBuildRef, buildCommit);
        }
    }

}
