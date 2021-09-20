package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.messaging.MessageProducer;
import br.com.saulocn.hermes.api.vo.MessageVO;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@ApplicationScoped
public class MessageService {


    @Inject
    MessageProducer messageProducer;

    @Inject
    EntityManager entityManager;

    public Message sendMessage(MessageVO messageVO) {
        Message message = Message.of(messageVO);
        entityManager.persist(message);
        messageVO.setId(message.getId());
        messageProducer.send(messageVO);
        return message;
    }

    @Transactional
    public void process(MessageVO messageVO) {
        Message message = entityManager.find(Message.class, messageVO.getId());

        System.out.println("Mensagem encontrada: "+message);
        if(message!=null) {
            System.out.println("Processando");
            message.setProcessed(true);
            entityManager.merge(message);
        }

    }
}
