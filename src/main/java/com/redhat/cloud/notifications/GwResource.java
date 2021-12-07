package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Encoder;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;

import com.redhat.cloud.notifications.ingress.Recipient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.reactive.messaging.Message;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
@Path("/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GwResource {

    public static final String EGRESS_CHANNEL = "egress";
    public static final String MESSAGE_ID_HEADER = "rh-message-id";

    @Inject
    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitter;

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
        @APIResponse(responseCode = "400", description = "Incoming message was not valid")
    })
    public Response forward(@NotNull @Valid RestAction ra) {
        receivedActions.increment();

        Action.Builder builder = Action.newBuilder();
        LocalDateTime parsedTime = LocalDateTime.parse(ra.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        builder.setTimestamp(parsedTime);
        List<RestEvent> events = ra.getEvents();
        List<Event> eventList = new ArrayList<>(events.size());
        for (RestEvent restEvent : events) {
            Metadata.Builder metadataBuilder = Metadata.newBuilder();
            Event event = new Event(metadataBuilder.build(), restEvent.getPayload());
            eventList.add(event);
        }

        List<Recipient> recipientList = new ArrayList<>();
        List<RestRecipient> recipients = ra.getRecipients();
        if (recipients != null) {
            for (RestRecipient restRecipient : recipients) {
                Recipient.Builder recipientBuilder = Recipient.newBuilder();
                Recipient recipient = recipientBuilder
                        .setIgnoreUserPreferences(restRecipient.isIgnoreUserPreferences())
                        .setOnlyAdmins(restRecipient.isOnlyAdmins())
                        .build();
                recipientList.add(recipient);
            }
        }

        builder.setEvents(eventList);
        builder.setRecipients(recipientList);
        builder.setEventType(ra.eventType);
        builder.setApplication(ra.application);
        builder.setBundle(ra.bundle);
        builder.setAccountId(ra.accountId);
        builder.setContext(ra.getContext());

        Action message = builder.build();

        String serializedAction = new Encoder().encode(message);
        emitter.send(buildMessageWithId(serializedAction));
        forwardedActions.increment();

        return Response.ok().build();

    }


    private static Message<String> buildMessageWithId(String payload) {
        byte[] messageId = UUID.randomUUID().toString().getBytes(UTF_8);
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }
}
