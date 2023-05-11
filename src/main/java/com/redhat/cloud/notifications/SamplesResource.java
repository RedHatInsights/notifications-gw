package com.redhat.cloud.notifications;


import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@Path("/sample")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SamplesResource {

    @GET
    @Path("/")
    public Response getSample() {
        RestAction a = new RestAction();
        a.setId(UUID.fromString("1234fedb-1234-5678-9abc-f4af67cdf544"));
        a.setBundle("my-bundle");
        a.setAccountId("123");
        a.setOrgId("234567");
        a.setApplication("my-app");
        a.setEventType("a_type");
        List<RestEvent> events = new ArrayList<RestEvent>();
        RestEvent event = new RestEvent();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("key", "value");
        // event.setMetadata(new RestMetadata());
        event.setPayload(payload);
        events.add(event);
        a.setEvents(events);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.US);
        a.setTimestamp(LocalDateTime.now().format(formatter));
        a.setContext(new HashMap<String, Object>());
        List<RestRecipient> recipients = new ArrayList<RestRecipient>();
        RestRecipient recipient = new RestRecipient();
        recipient.setUsers(List.of("user1", "user2"));
        recipients.add(recipient);
        return Response.ok().entity(a).build();
    }

    @POST
    @Path("/verify")
    public Response verify(@Valid RestAction ra) {

        return Response.ok().build();
    }

}
