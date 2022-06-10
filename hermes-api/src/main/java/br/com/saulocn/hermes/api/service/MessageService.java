package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.entity.Recipient;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.vo.MailVO;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class MessageService {

    @Inject
    EntityManager em;

    @Inject
    RedisClient redisClient;


    @Inject
    Logger log;


    @Transactional
    public Message sendMail(MessageVO messageVO){
        Message message = Message.of(messageVO);
        em.persist(message);
        messageVO.getRecipients().stream().forEach(recipient -> em.persist(new Recipient(recipient, message.getId())));
        redisClient.set(Arrays.asList(getMessageKey(message.getId()), MailVO.of(messageVO).toJSON()));
        return message;
    }

    private String getMessageKey(Long messageId) {
        return String.format("message_%d",messageId);
    }

    public List<Message> listMail() {
        return em.createQuery("select m from Message m", Message.class).getResultList();
    }

    public MailVO findById(Long messageId) {
        String mailVOJSON = redisClient.get(getMessageKey(messageId)).toString();
        log.info("Found: "+mailVOJSON);
        return MailVO.fromJSON(mailVOJSON);
    }
}
