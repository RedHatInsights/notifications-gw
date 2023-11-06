package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.ingress.Recipient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.vertx.core.json.JsonObject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE;

@ApplicationScoped
@Path("/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GwResource {

    public static final String EGRESS_CHANNEL = "egress";
    public static final String MESSAGE_ID_HEADER = "rh-message-id";

    @ConfigProperty(name = "notifications.kafka-callback-timeout-seconds", defaultValue = "60")
    long callbackTimeout;

    @Inject
    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitter;

    @RestClient
    RestValidationClient restValidationClient;

    private final Counter receivedActions;
    private final Counter forwardedActions;

    public GwResource(MeterRegistry registry) {
        receivedActions = registry.counter("notifications.gw.received");
        forwardedActions = registry.counter("notifications.gw.forwarded");
    }

    @POST
    @Operation(summary = "Forward one message to the notification system")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Message forwarded"),
            @APIResponse(responseCode = "403", description = "No permission"),
            @APIResponse(responseCode = "401"),
            @APIResponse(responseCode = "400", description = "Incoming message was not valid"),
            @APIResponse(responseCode = "503", description = "Message delivery to Kafka failed")
    })
    public Response forward(@NotNull @Valid RestAction ra) {
        receivedActions.increment();

        try {
            restValidationClient.validate(ra.getBundle(), ra.getApplication(), ra.getEventType());
        } catch (BadRequestException e) {
            // The following line is required to forward the HTTP 400 error message.
            return Response.status(BAD_REQUEST).entity(e.getMessage()).build();
        }

        Action.ActionBuilder builder = new Action.ActionBuilder();
        LocalDateTime parsedTime = LocalDateTime.parse(ra.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        builder.withTimestamp(parsedTime);
        List<RestEvent> events = ra.getEvents();
        List<Event> eventList = new ArrayList<>(events.size());
        for (RestEvent restEvent : events) {
            Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
            restEvent.getPayload().forEach(payloadBuilder::withAdditionalProperty);

            Event event = new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(payloadBuilder.build())
                    .build();
            eventList.add(event);
        }

        List<Recipient> recipientList = new ArrayList<>();
        List<RestRecipient> recipients = ra.getRecipients();
        if (recipients != null) {
            for (RestRecipient restRecipient : recipients) {
                Recipient recipient = new Recipient.RecipientBuilder()
                        .withIgnoreUserPreferences(restRecipient.isIgnoreUserPreferences())
                        .withOnlyAdmins(restRecipient.isOnlyAdmins())
                        .withUsers(restRecipient.getUsers() != null ? restRecipient.getUsers() : List.of())
                        .withEmails(restRecipient.getEmails() != null ? restRecipient.getEmails() : List.of())
                        .build();
                recipientList.add(recipient);
            }
        }

        Context.ContextBuilder contextBuilder = new Context.ContextBuilder();
        ra.getContext().forEach(contextBuilder::withAdditionalProperty);

        builder.withEvents(eventList);
        builder.withRecipients(recipientList);
        builder.withEventType(ra.eventType);
        builder.withApplication(ra.application);
        builder.withBundle(ra.bundle);
        builder.withAccountId(ra.accountId);
        builder.withContext(contextBuilder.build());
        builder.withOrgId(ra.orgId);
        builder.withId(ra.id);

        Action action = builder.build();
        String serializedAction = Parser.encode(action);

        CompletableFuture<Void> callback = new CompletableFuture<>();
        Message<String> message = buildMessageWithId(serializedAction, callback);

        try {
            emitter.send(message);
            /*
             * The following line waits until the callback is complete or the timeout delay is expired.
             * If the Kafka server responded with an ack, no exception will be thrown and the execution of the try block will continue.
             * If the Kafka server responded with a nack, an ExecutionException will be thrown, resulting in an HTTP 503 status returned to the caller.
             * If the timeout delay is expired, a TimeoutException will be thrown, resulting in an HTTP 503 status returned to the caller.
             */
            callback.get(callbackTimeout, TimeUnit.SECONDS);
            forwardedActions.increment();
            String responseEntity = buildResponseEntity(true, null);
            return Response.ok(responseEntity).build();
        } catch (Throwable t) {
            Log.error("Message delivery to Kafka failed", t);
            String responseEntity = buildResponseEntity(false, "Message delivery to Kafka failed, please try again later");
            return Response.status(SERVICE_UNAVAILABLE).entity(responseEntity).build();
        }
    }

    private static Message<String> buildMessageWithId(String payload, CompletableFuture<Void> callback) {
        byte[] messageId = UUID.randomUUID().toString().getBytes(UTF_8);
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload)
                .addMetadata(metadata)
                .withAck(() -> {
                    callback.complete(null);
                    return CompletableFuture.completedFuture(null);
                }).withNack(reason -> {
                    callback.completeExceptionally(reason);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static String buildResponseEntity(boolean success, String details) {
        JsonObject response = new JsonObject();
        response.put("result", success ? "success" : "error");
        if (details != null) {
            response.put("details", details);
        }
        return response.encode();
    }
}
