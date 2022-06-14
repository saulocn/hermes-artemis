package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.Arrays;

public class MessageService {

    private static final String TTL_IN_SECONDS = "30";

    @Inject
    Logger log;

    @Inject
    MailSenderService mailSenderService;

    @Inject
    RedisClient redisClient;

    @Inject
    EntityManager entityManager;

    @Incoming("mail")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Transactional
    public void mailConsumer(String jsonMessageVO) {
        RecipientVO recipientVO = RecipientVO.fromJSON(jsonMessageVO);
        MailVO mailVO = findById(recipientVO.getMessageId());
        Recipient recipient = entityManager.find(Recipient.class, recipientVO.getId());
        log.info("Consuming: " + jsonMessageVO);
        if(!recipient.isSent()) {
            log.info("Sending: " + jsonMessageVO);
            mailVO.setTo(recipient.getEmail());
            mailVO.setRecipientId(recipient.getId());
            mailSenderService.sendHtmlMail(mailVO);
            recipient.setSent(true);
            entityManager.merge(recipient);
        }

    }


    public MailVO findById(Long messageId) {
        MailVO mailVO = findInCache(messageId);
        if(mailVO==null){
            Message message = entityManager.find(Message.class, messageId);
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

    private String getMessageKey(Long messageId) {
        return String.format("message_%d",messageId);
    }


    private void setToCache(MailVO mailVO) {
        String cacheKey = getMessageKey(mailVO.getMessageId());
        redisClient.set(Arrays.asList(cacheKey, mailVO.toJSON()));
        redisClient.expire(cacheKey, TTL_IN_SECONDS);
    }


}
