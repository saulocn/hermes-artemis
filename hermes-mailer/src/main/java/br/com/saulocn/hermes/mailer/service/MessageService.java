package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.redis.client.RedisClient;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.redis.client.Response;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
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

    // JDBC and the SMTP client both block, so this must not run on the event loop. The named
    // pool is what smallrye.messaging.worker.mail-sender-pool.max-concurrency sizes — that
    // property was configured but had nothing referencing it until now.
    @Incoming("mail")
    @Blocking("mail-sender-pool")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    @Transactional
    public void mailConsumer(String jsonMessageVO) {
        RecipientVO recipientVO = RecipientVO.fromJSON(jsonMessageVO);
        log.info("Consuming: " + jsonMessageVO);

        // Claim the recipient before sending, with the check and the write in one statement.
        // Reading `sent` and then updating it leaves a window: MailFallbackJob republishes
        // whatever is still unsent, so the same recipient does arrive twice, and with the
        // worker pool running 30 in parallel both copies could pass a separate read before
        // either commits — two mails to the same person. `where sent = false` makes the
        // database the arbiter: exactly one copy updates a row, the rest update none.
        //
        // Claiming first also means a failed send rolls the claim back with the transaction,
        // so the message is redelivered rather than silently dropped.
        int claimed = entityManager.createQuery(
                        "update Recipient r set r.sent = true where r.id = :id and r.sent = false")
                .setParameter("id", recipientVO.getId())
                .executeUpdate();

        if (claimed == 0) {
            // Already delivered by another copy. Returning normally acks the message, which is
            // what drops the duplicate off the queue instead of letting it redeliver forever.
            log.info("Duplicate, already sent: " + recipientVO.getId());
            return;
        }

        log.info("Sending: " + jsonMessageVO);
        MailVO mailVO = findById(recipientVO.getMessageId());
        mailVO.setTo(recipientVO.getEmail());
        mailVO.setRecipientId(recipientVO.getId());
        mailSenderService.sendHtmlMail(mailVO);
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
