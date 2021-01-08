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

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;

/**
 * This one puts the Principal into the context.
 * @author hrupp
 */
@ApplicationScoped
public class RhIdProvider implements IdentityProvider<RhIdentityAuthenticationRequest> {

    @Override
    public Class<RhIdentityAuthenticationRequest> getRequestType() {
        return RhIdentityAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(RhIdentityAuthenticationRequest req, AuthenticationRequestContext ctx) {

        // This is a no-op - we just use it to put the Principal into the call context,
        // which happens automatically.

        return null;
    }
}
