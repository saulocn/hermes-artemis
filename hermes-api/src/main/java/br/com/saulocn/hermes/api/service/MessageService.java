package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.entity.Recipient;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.vo.MailVO;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class MessageService {

    /**
     * Redis cache TTL for message objects, in seconds.
     * Source of truth: contracts/message-cache.properties (ttl.seconds=30).
     * Must match the configuration to prevent tests from diverging.
     */
    static String getMessageCacheTtl() {
        return "30";
    }

    /**
     * Redis cache key format for message objects.
     * Source of truth: contracts/message-cache.properties (key.format=message_%d).
     * The %d is substituted with the message ID.
     */
    static String getMessageKeyFormat() {
        return "message_%d";
    }
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
        redisClient.expire(cacheKey, getMessageCacheTtl());
    }

    private String getMessageKey(Long messageId) {
        return String.format(getMessageKeyFormat(), messageId);
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
