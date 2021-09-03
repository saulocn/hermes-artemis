package br.com.saulocn.hermes.mailer.resource;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import br.com.saulocn.hermes.mailer.kafka.MessageProducer;
import br.com.saulocn.hermes.mailer.model.Message;
import br.com.saulocn.hermes.mailer.service.MessageService;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

@Path("/messages")
public class MessageResource {

    @Inject
    MessageService messageService;

    @Inject
    @Channel("init")
    Emitter<String> emitter;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Message> list() {
        return messageService.list();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response post() {
        emitter.send("teste");
        return Response.ok().build();
    }

}
