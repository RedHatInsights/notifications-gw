package com.redhat.cloud.notifications;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/internal/validation")
@RegisterRestClient(configKey = "notifications-backend")
public interface RestValidationClient {

    @GET
    @Path("/baet")
    @Produces(MediaType.APPLICATION_JSON)
    Response isBundleApplicationEventTypeTripleValid(@QueryParam("bundle") String bundle, @QueryParam("application") String application, @QueryParam("eventType") String eventType);

}
