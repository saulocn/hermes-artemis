package br.com.saulocn.hermes.api.resource;

import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.MessageService;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/message")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MessageResource {

    @Inject
    MessageService messageService;

    @Inject
    Logger log;


    @POST
    @Transactional
    public Uni<Response> post(MessageVO messageVO) {
        log.info("Cadastrando");
        return messageService.sendMail(messageVO)
                .onItem().transform(message->{
                    log.info("Cadastrando um e-mail para ser enviado:" + messageVO.getTitle() +" ID: " + message.getId());
                    return Response.ok().entity(message).build();
                });
    }

    @GET
    public Uni<Response> getAll() {
        return messageService.listMail().map(messages -> Response.ok().entity(messages).build());
    }

    @GET
    @Path("/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Response> find(@PathParam("messageId") Long messageId) {
        return messageService.findById(messageId).map(mailVO -> Response.ok().entity(mailVO).build());
    }
}