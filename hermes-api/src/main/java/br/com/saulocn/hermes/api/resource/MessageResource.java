package br.com.saulocn.hermes.api.resource;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.MessageService;
import br.com.saulocn.hermes.api.service.vo.MailVO;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

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
    public Response post(MessageVO messageVO) {
        Message message = messageService.sendMail(messageVO);
        log.info("Cadastrando um e-mail para ser enviado:" + messageVO.getTitle() +" ID: " + message.getId());
        return Response.ok().entity(message).build();
    }

    @GET
    public Response getAll() {
        List<Message> messages = messageService.listMail();
        return Response.ok().entity(messages).build();
    }

    @GET
    @Path("/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response find(@PathParam("messageId") Long messageId) {
        MailVO mailVO = messageService.findById(messageId);
        return Response.ok().entity(mailVO).build();
    }
}