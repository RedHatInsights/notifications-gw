package com.redhat.cloud.notifications;

import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumWriter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
@Path("/sample")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SamplesResource {

    @GET
    @Path("/")
    public Response getSample() {
        RestAction a = new RestAction();
        a.setAccountId("123");
        a.setBundle("my-bundle");
        a.setApplication("my-app");
        a.setEventType("a_type");
        Map<String, Object> payload = new HashMap<>();
        payload.put("key1","value1");
        payload.put("key2","value2");
        a.setPayload(payload);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withLocale(Locale.US);
        a.setTimestamp(LocalDateTime.now().format(formatter));

        return Response.ok().entity(a).build();
    }


    @POST
    @Path("/verify")
    public Response verify(@Valid RestAction ra) {

        return Response.ok().build();
    }

}
