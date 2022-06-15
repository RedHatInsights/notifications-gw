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

import io.quarkus.logging.Log;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

import javax.enterprise.context.ApplicationScoped;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * JAKARTA-375 auth mechanism. This basically just decodes he x-rh-identity header
 * and assumes that all calls have this header set.
 * @author hrupp
 */
@ApplicationScoped
public class RHIdAuthMechanism implements HttpAuthenticationMechanism {

    public static final String IDENTITY_HEADER = "x-rh-identity";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String xRhIdentityHeaderValue = context.request().getHeader(IDENTITY_HEADER);

        String subject = "-unset-";

        Optional<XRhIdentity> oxid = HeaderHelper.getRhIdFromString(xRhIdentityHeaderValue);
        if (oxid.isPresent()) {
            XRhIdentity xid = oxid.get();
            subject = xid.getSubject();
        }

        Log.debugf("Using subject %s", subject);

        return Uni.createFrom().item(
                QuarkusSecurityIdentity.builder()
                    .setPrincipal(new RhIdPrincipal(subject))
                    .build()
        );
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(RhIdentityAuthenticationRequest.class);
    }

    @Override
    public HttpCredentialTransport getCredentialTransport() {
        return null;
    }


}
