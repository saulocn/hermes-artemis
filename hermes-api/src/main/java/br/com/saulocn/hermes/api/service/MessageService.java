package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;


import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@ApplicationScoped
public class MessageService {

    @Inject
    EntityManager em;

    @Inject
    @Channel("message-requests")
    Emitter<String> emitter;

    public void sendMail(MessageVO messageVO){
        Message message = Message.of(messageVO);
        em.persist(message);
        messageVO.setId(message.getId());
        System.out.println(messageVO);
        emitter.send(messageVO.toJSON());
    }

}
