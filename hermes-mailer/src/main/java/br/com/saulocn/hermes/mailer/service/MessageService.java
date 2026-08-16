package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.redis.client.RedisClient;
import io.vertx.redis.client.Response;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Arrays;

@ApplicationScoped
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

    /**
     * Claims the recipient and sends the mail, as one transaction.
     *
     * <p>Everything this touches rolls back together when the send throws, which is what puts the
     * message back on the queue. Nothing that must <em>survive</em> that rollback belongs in
     * here — see {@link MailConsumer} for where the failure counter goes and why.
     *
     * <h2>Why the SMTP call is inside the transaction</h2>
     *
     * <p>It looks wrong — a network call to a third party holding a database connection — and the
     * obvious rewrite is to commit the claim first and send afterwards. That rewrite trades this
     * system's stated priority away. Committing first means a crash between the commit and the
     * send leaves the row reading {@code sent = true} with no mail ever sent: the fallback job
     * only republishes rows that are still unsent, so nothing would ever notice. Silent loss.
     *
     * <p>Keeping the send inside means a crash rolls the claim back with the connection, the
     * broker redelivers, and the worst case is a second mail to someone who may already have
     * received one. This system has already chosen that direction everywhere else — the fallback
     * job deliberately republishes, and the claim exists to absorb the duplicates it creates.
     *
     * <p>What remains is a genuine dual write: if SMTP accepts the mail and the commit then fails,
     * the mail is out and the claim is not, so the redelivery sends a second copy. That window
     * cannot be closed without a transactional mail server or an outbox with a send-state column.
     * It can only be made rare, which is a matter of configuration: the transaction timeout has to
     * comfortably exceed the slowest send, or the timeout <em>itself</em> becomes the thing that
     * manufactures duplicates. See the timeout and pool settings in application.properties, which
     * are sized against each other for exactly this reason.
     */
    @Transactional
    public void deliver(RecipientVO recipientVO) {
        log.info("Consuming: " + recipientVO.getId());

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

        log.info("Sending: " + recipientVO.getId());
        MailVO mailVO = findById(recipientVO.getMessageId());
        mailVO.setTo(recipientVO.getEmail());
        mailVO.setRecipientId(recipientVO.getId());

        // Throwing from here rolls the claim back with the transaction, so the broker redelivers.
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
