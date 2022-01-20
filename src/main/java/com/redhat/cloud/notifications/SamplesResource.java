package com.redhat.cloud.notifications;


import javax.enterprise.context.ApplicationScoped;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@ApplicationScoped
@Path("/sample")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SamplesResource {

    @GET
    @Path("/")
    public Response getSample() {
        RestAction a = new RestAction();
        a.setBundle("my-bundle");
        a.setAccountId("123");
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
