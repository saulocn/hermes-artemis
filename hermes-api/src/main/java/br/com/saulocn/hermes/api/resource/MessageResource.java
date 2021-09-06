package br.com.saulocn.hermes.api.resource;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.service.MessageService;
import br.com.saulocn.hermes.api.vo.MessageVO;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

@Path("/message")
public class MessageResource {

    @Inject
    MessageService messageService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello RESTEasy";
    }

    @POST
    @Transactional
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response send(MessageVO message) {
        Message createdMessage = messageService.sendMessage(message);
        return Response.created(URI.create("/message/" + createdMessage.getId())).build();
    }
}