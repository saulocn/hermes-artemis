package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.entity.Recipient;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.vo.MailVO;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.redis.client.Response;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class MessageService {

    private static final String TTL_IN_SECONDS = "30";

    @Inject
    ReactiveRedisClient reactiveRedisClient;


    @Inject
    Logger log;


    @Transactional
    public Uni<Message> sendMail(MessageVO messageVO){
        Message message = Message.of(messageVO);
        Uni<Message> persistedMessage = Panache.withTransaction(message::persist);
        persistRecipients(messageVO.getRecipients(), message.getId());
        return persistedMessage;
    }

    private void persistRecipients(List<String> recipients, Long messageId){
        recipients.forEach(recipient -> Panache.withTransaction(new Recipient(recipient, messageId)::persist));
    }


    private Uni<Response> setToCache(MailVO mailVO) {
        String cacheKey = getMessageKey(mailVO.getMessageId());
        log.info("Adicionando ao cache");
        Uni<Response> cache = reactiveRedisClient.setex(cacheKey, TTL_IN_SECONDS, mailVO.toJSON());
        cache.subscribe().with(response ->  log.info("Adicionado ao cache"));
        return cache;
    }

    private String getMessageKey(Long messageId) {
        return String.format("message_%d",messageId);
    }

    public Uni<List<Message>> listMail() {
        return Message.find("select m from Message m").list();
    }

    public Uni<MailVO> findById(Long messageId) {
        return findInCache(messageId)
                .onItem().ifNull().switchTo( () -> findOnDB(messageId));
    }

    private Uni<MailVO> findOnDB(Long messageId) {
        return Message.findById(messageId).map(message->{
            MailVO mailVO = MailVO.fromMessage((Message) message);
            setToCache(mailVO);
            log.info("Found in DB: Message "+mailVO.getMessageId());
            return mailVO;
        });
    }

    private Uni<MailVO> findInCache(Long messageId) {
       return reactiveRedisClient.get(getMessageKey(messageId))
               .onItem().ifNotNull().transform(response->{
                   String mailVOJSON = response.toString();
                   MailVO mailVO = MailVO.fromJSON(mailVOJSON);
                   log.info("Found in cache: Message "+mailVO.getMessageId());
                   return mailVO;
               });
    }
}
