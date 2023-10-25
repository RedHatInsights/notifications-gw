package com.redhat.cloud.notifications;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/internal/validation")
@RegisterRestClient(configKey = "notifications-backend")
@ApplicationScoped // This scope is required to mock the client during tests.
public interface RestValidationClient {

    @GET
    @Path("/baet")
    @Retry(maxRetries = 5)
    @Produces(TEXT_PLAIN)
    @CacheResult(cacheName = "baet-validation")
    Response validate(@RestQuery String bundle, @RestQuery String application, @RestQuery String eventType);

}
