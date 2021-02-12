package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;


@ApplicationScoped
@Path("/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GwResource {


    @Inject
    @Channel("egress")
    Emitter<String> emitter;

    @Inject
    @Metric
    Counter receivedActions;

    @Inject
    @Metric
    Counter fowardedActions;

    @POST
    @Operation(summary = "Forward one message to the notification system")
    @APIResponses({
        @APIResponse(responseCode = "200", description = "Message forwarded"),
        @APIResponse(responseCode = "403", description = "No permission"),
        @APIResponse(responseCode = "401")
    })
    public Response forward(RestAction ra) {
        receivedActions.inc();

        Action.Builder builder = Action.newBuilder();
        LocalDateTime parsedTime = LocalDateTime.parse(ra.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        builder.setTimestamp(parsedTime);
        builder.setPayload(ra.payload);
        builder.setEventType(ra.eventType);
        builder.setApplication(ra.application);
        builder.setBundle(ra.bundle);
        builder.setAccountId(ra.accountId);

        Action message = builder.build();

        try {
            String serializedAction = serializeAction(message);
            CompletionStage<Void> res = emitter.send(serializedAction);
            fowardedActions.inc();
        } catch (IOException e) {
            e.printStackTrace();  // TODO: Customise this generated block
            return Response.serverError().entity(e.getMessage()).build();
        }

        return Response.ok().build();

    }

    @GET
    @Path("/sample")
    public Response getSample() {
        RestAction a = new RestAction();
        a.setAccountId("123");
        a.setBundle("my-bundle");
        a.setApplication("my-app");
        a.setEventType("a type");
        Map<String, Object> payload = new HashMap<>();
        payload.put("key1","value1");
        payload.put("key2","value2");
        a.setPayload(payload);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        a.setTimestamp(LocalDateTime.now().format(formatter));

        return Response.ok().entity(a).build();
    }

    public static String serializeAction(Action action) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonEncoder jsonEncoder = EncoderFactory.get().jsonEncoder(Action.getClassSchema(), baos);
        DatumWriter<Action> writer = new SpecificDatumWriter<>(Action.class);
        writer.write(action, jsonEncoder);
        jsonEncoder.flush();

        return baos.toString(StandardCharsets.UTF_8);
    }

}
