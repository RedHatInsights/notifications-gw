package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.regex.Pattern;

@Provider
@PreMatching
public class IncomingRequestInterceptor implements ContainerRequestFilter {


    // Prevents the injection of characters that would break the log file pattern and lead to log forging or log poisoning.
    private static final Pattern ANTI_INJECTION_PATTERN = Pattern.compile("[\n|\r|\t]");

    private static final String PATH_TO_REPLACE = "/api/notifications-gw/notifications";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        routeRedirector(requestContext);
    }

    /**
     * In ephemeral environment, the path exposed for notifications GW endpoint is not the same as Stage or prod
     * (because Threescale is not available in ephemeral), then we have to rewrite it on the fly.
     */
    private void routeRedirector(ContainerRequestContext requestContext) {

        String uri = requestContext.getUriInfo().getPath();
        if (Log.isTraceEnabled()) {
            String sanitizedUri = ANTI_INJECTION_PATTERN.matcher(uri).replaceAll("");
            Log.tracef("Incoming uri: %s", sanitizedUri);
        }
        if (PATH_TO_REPLACE.equals(uri)) {
            String newTarget = "/notifications";
            Log.tracef("Rerouting to: %s", newTarget);

            requestContext.setRequestUri(UriBuilder.fromUri(requestContext.getUriInfo().getRequestUri()).replacePath(newTarget).build());
        }
    }
}
