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

    private static final String TTL_IN_SECONDS = "30";
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
        messageVO.setId(message.getId());
        messageVO.getRecipients().stream().forEach(recipient -> em.persist(new Recipient(recipient, message.getId())));
        setToCache(MailVO.of(messageVO));
        return message;
    }

    private void setToCache(MailVO mailVO) {
        String cacheKey = getMessageKey(mailVO.getMessageId());
        redisClient.set(Arrays.asList(cacheKey, mailVO.toJSON()));
        redisClient.expire(cacheKey, TTL_IN_SECONDS);
    }

    private String getMessageKey(Long messageId) {
        return String.format("message_%d",messageId);
    }

    public List<Message> listMail() {
        return em.createQuery("select m from Message m", Message.class).getResultList();
    }

    public MailVO findById(Long messageId) {
        MailVO mailVO = findInCache(messageId);
        if(mailVO==null){
            Message message = em.find(Message.class, messageId);
            mailVO = MailVO.fromMessage(message);
            setToCache(mailVO);
            log.info("Found in DB: Message "+mailVO.getMessageId());
            return mailVO;
        } else {
            log.info("Found in cache: Message "+mailVO.getMessageId());
            return mailVO;
        }
    }

    private MailVO findInCache(Long messageId) {
        Response response = redisClient.get(getMessageKey(messageId));
        if(response==null){
            return null;
        }
        String mailVOJSON = response.toString();
        return MailVO.fromJSON(mailVOJSON);
    }
}
