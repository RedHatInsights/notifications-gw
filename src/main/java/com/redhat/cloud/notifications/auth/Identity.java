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
package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * x-rh-identity header can have several identity 'payloads' depending on
 * who generates it. Turnpike currently has x509 and saml.
 * 3scale also has type User. We differentiate according to
 * the type property.
 * <p/>
 * See https://stackoverflow.com/a/62299710/100957,
 * https://fasterxml.github.io/jackson-annotations/javadoc/2.4/com/fasterxml/jackson/annotation/JsonTypeInfo.html and
 * https://medium.com/@david.truong510/jackson-polymorphic-deserialization-91426e39b96a
 * for the @JsonTypeInfo and @JsonSubTypes
 */

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
    property = "type",
    visible = true
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = X509Identity.class, name = "X509"),
    @JsonSubTypes.Type(value = SamlIdentity.class, name = "Associate"),
    @JsonSubTypes.Type(value = RhServiceAccountIdentity.class, name = "ServiceAccount")
})
public abstract class Identity {
    @JsonProperty(required = true)
    public String type;
    @JsonProperty
    public String auth_info;

    abstract public String getSubject();

    public String getType() {
        return type;
    }
}
