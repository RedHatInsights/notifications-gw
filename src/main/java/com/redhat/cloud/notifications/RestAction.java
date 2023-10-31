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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Based on the Avro 'Action'
 * @author hrupp
 */
public class RestAction {

    // Allowed, but not enforced yet.
    public UUID id;

    @NotNull
    @NotEmpty
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String bundle;

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

    // This field can only contain a positive number. We use the String type to allow leading zeros in the value.
    @Positive
    @JsonProperty("account_id")
    public String accountId;

    // This field can only contain a positive number. We use the String type to allow leading zeros in the value.
    @Positive
    @NotNull
    @NotEmpty
    @JsonProperty("org_id")
    public String orgId;

    @NotNull
    public List<RestEvent> events;

    public Map<String, Object> context;

    @Valid
    public List<RestRecipient> recipients;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

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

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public List<RestEvent> getEvents() {
        return events;
    }

    public void setEvents(List<RestEvent> events) {
        this.events = events;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public List<RestRecipient> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<RestRecipient> recipients) {
        this.recipients = recipients;
    }
}
