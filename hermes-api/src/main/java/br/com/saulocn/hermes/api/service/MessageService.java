package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.vo.MessageVO;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;

@ApplicationScoped
public class MessageService {



    @Inject
    @Channel("init-request")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10)
    Emitter<String> emitter;


    @Inject
    EntityManager entityManager;


    public Message sendMessage(MessageVO messageVO) {
        Message message = Message.of(messageVO);
        entityManager.persist(message);
        messageVO.setId(message.getId());
        System.out.println("Mensagem salva");
        emitter.send(messageVO.toJSON());
        System.out.println("Mensagem enviada");
        return message;
    }

    public void processMessage(Long id) {
        System.out.println("pegando a mensagem:" + id);
        Message message = entityManager.find(Message.class, id);
        System.out.println("processando a mensagem:" + message);
        if (message != null) {
            System.out.println("processando a mensagem:" + message);
            message.setProcessed(true);
            entityManager.merge(message);
        }
    }
}
