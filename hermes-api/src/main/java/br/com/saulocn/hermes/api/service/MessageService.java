package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.entity.Recipient;
import br.com.saulocn.hermes.api.resource.request.MessageVO;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class MessageService {

    @Inject
    EntityManager em;

    @Transactional
    public Message sendMail(MessageVO messageVO){
        Message message = Message.of(messageVO);
        em.persist(message);
        messageVO.getRecipients().stream().forEach(recipient -> em.persist(new Recipient(recipient, message.getId())));
        return message;
    }

}
