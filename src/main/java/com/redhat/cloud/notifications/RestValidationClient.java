package com.redhat.cloud.notifications;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/internal/validation")
@RegisterRestClient(configKey = "notifications-backend")
@ApplicationScoped // This scope is required to mock the client during tests.
public interface RestValidationClient {

    @GET
    @Path("/baet")
    @Retry(maxRetries = 5)
    @Produces(TEXT_PLAIN)
    Response validate(@QueryParam("bundle") String bundle, @QueryParam("application") String application, @QueryParam("eventType") String eventType);

}
