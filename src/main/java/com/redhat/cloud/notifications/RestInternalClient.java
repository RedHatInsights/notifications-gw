package com.redhat.cloud.notifications;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/internal/gw")
@RegisterRestClient(configKey = "notifications-backend")
@ApplicationScoped // This scope is required to mock the client during tests.
public interface RestInternalClient {

    @GET
    @Path("/subscriptions/{bundle}/{application}")
    @Retry(maxRetries = 5)
    @Produces(APPLICATION_JSON)
    Map<String, List<String>> getOrgSubscriptionsPerEventType(@RestPath String bundle, @RestPath String application, @RestQuery List<String> eventTypes);
}
