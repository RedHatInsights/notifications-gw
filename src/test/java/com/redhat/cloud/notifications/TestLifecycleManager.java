/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.mockserver.model.Parameter;

import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.GwResource.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        Map<String, String> properties = new HashMap<>();

        setupMockServer(properties);

        /*
         * We'll use an in-memory Reactive Messaging connector to send payloads.
         * See https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/2/testing/testing.html
         */
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(EGRESS_CHANNEL));

        System.out.println("+ -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();

        MockServerLifecycleManager.stop();

        // Helper to debug mock server issues
//           System.err.println(mockServerClient.retrieveLogMessages(request()));
//           System.err.println(mockServerClient.retrieveRecordedRequests(request()));
    }

    private void setupMockServer(Map<String, String> properties) {

        // set up mock engine
        MockServerLifecycleManager.start();

        properties.put("quarkus.rest-client.notifications-backend.url", getMockServerUrl());
        properties.put("quarkus.rest-client.rbac.url", getMockServerUrl());

        String xRhIdentity = TestHelpers.encodeIdentityInfo("test", "user");
        String access = TestHelpers.getFileAsString("rbac_example_full_access.json");

        getClient()
                .when(request()
                        .withPath("/internal/validation/baet")
                        .withQueryStringParameter(new Parameter("bundle", "my-bundle"))
                        .withQueryStringParameter(new Parameter("application", "my-app"))
                        .withQueryStringParameter(new Parameter("eventtype", "a_type"))
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                );

        getClient()
                .when(request()
                        .withPath("/internal/validation/baet")
                        .withQueryStringParameter(new Parameter("bundle", "my-invalid-bundle"))
                        .withQueryStringParameter(new Parameter("application", "my-invalid-app"))
                        .withQueryStringParameter(new Parameter("eventtype", "a_invalid-type"))
                )
                .respond(response()
                        .withStatusCode(404)
                        .withHeader("Content-Type", "application/json")
                );

        getClient()
                .when(request()
                        .withPath("/api/rbac/v1/access/")
                        .withHeader("x-rh-identity", xRhIdentity)
                )
                .respond(response()
                        .withStatusCode(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(access)
                );
    }

}
