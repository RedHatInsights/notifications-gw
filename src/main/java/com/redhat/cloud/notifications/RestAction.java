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

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Based on the Avro 'Action'
 * @author hrupp
 */
public class RestAction {

    @NotNull
    @NotEmpty
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String application;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @JsonProperty("event_type")
    public String eventType;

    @ISO8601Timestamp
    public String timestamp;

    @javax.validation.constraints.Positive
    @NotNull
    @NotEmpty
    @JsonProperty("account_id")
    public String accountId;

    public java.util.Map<String,Object> payload;

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Map<String,Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String,Object> payload) {
        this.payload = payload;
    }
}
