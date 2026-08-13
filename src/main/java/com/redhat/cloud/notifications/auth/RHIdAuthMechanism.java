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
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.Set;

/**
 * JAKARTA-375 auth mechanism. This basically just decodes he x-rh-identity header
 * and assumes that all calls have this header set.
 * @author hrupp
 */
@ApplicationScoped
public class RHIdAuthMechanism implements HttpAuthenticationMechanism {

    public static final String X_RH_IDENTITY_HEADER = "x-rh-identity";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String xRhIdentityHeaderValue = context.request().getHeader(X_RH_IDENTITY_HEADER);

        XRhIdentity xid = HeaderHelper.getRhIdFromString(xRhIdentityHeaderValue)
                .orElseThrow(() -> {
                    // The raw header value is not logged here: HeaderHelper already logs it at WARN level
                    // when decoding fails, and it must never be logged on a missing-header rejection since
                    // there is nothing sensitive to add beyond the fact that it was absent.
                    Log.warnf("Rejecting request: %s header is %s", X_RH_IDENTITY_HEADER, xRhIdentityHeaderValue == null ? "missing" : "invalid");
                    return new AuthenticationFailedException(String.format("Missing or invalid %s header", X_RH_IDENTITY_HEADER));
                });

        String subject = xid.getSubject();
        String type = xid.getType();

        Log.debugf("Using subject %s, from type %s", subject, type);

        SecurityIdentity identity = QuarkusSecurityIdentity.builder()
                .setPrincipal(new RhIdPrincipal(subject, type))
                .build();

        return Uni.createFrom().item(identity);
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        // Invoked by Quarkus REST's AuthenticationFailedExceptionMapper when authenticate() fails,
        // to build the HTTP response sent back to the caller.
        return Uni.createFrom().item(new ChallengeData(Response.Status.UNAUTHORIZED.getStatusCode(), null, null));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Collections.singleton(RhIdentityAuthenticationRequest.class);
    }
}
