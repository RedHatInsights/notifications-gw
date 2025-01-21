package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.model.SourceEnvironment;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
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

    @GET
    @Path("/certificate")
    @Retry(maxRetries = 5)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @CacheResult(cacheName = "certificate-validation")
    SourceEnvironment validateCertificate(@RestQuery String bundle, @RestQuery String application, @RestQuery String certificateSubjectDn);

    @GET
    @Path("/certificates")
    @Retry(maxRetries = 5)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    List<SourceEnvironment> getCertificates();

    @GET
    @Path("/baet_list")
    @Retry(maxRetries = 5)
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Map<String, Map<String, List<String>>> getBaets();
}
