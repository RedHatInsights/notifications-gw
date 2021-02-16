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

import io.vertx.core.json.Json;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import javax.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Test receiver that works together with the
 * {@link GwResourceTest#testHelloEndpoint()}
 * to receive the message on Kafka and provide it
 * back to the test.
 *
 */
@ApplicationScoped
public class TestReceiver {

    public static Map<String,Object> theAction = null;

    @Incoming("ingress")
    public void receive(String action) {

        theAction = Json.decodeValue(action, Map.class);
    }
}
