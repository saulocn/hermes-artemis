package br.com.saulocn.hermes.api.service;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.entity.Recipient;
import br.com.saulocn.hermes.api.resource.request.MessageVO;
import br.com.saulocn.hermes.api.service.vo.MailVO;
import io.quarkus.hibernate.reactive.panache.common.runtime.ReactiveTransactional;
import io.quarkus.redis.client.reactive.ReactiveRedisClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.mutiny.redis.client.Response;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.stream.Stream;

@ApplicationScoped
public class MessageService {

    private static final String TTL_IN_SECONDS = "30";

    @Inject
    ReactiveRedisClient reactiveRedisClient;


    @Inject
    Logger log;


    @ReactiveTransactional
    public Uni<Message> sendMail(MessageVO messageVO){
        Message message = Message.of(messageVO);
        Uni<Message> persist = message.persist();
        persist.chain()
        persist.subscribeAsCompletionStage(item->{
            log.info("Pelo menos executou?");
            Stream<Recipient> recipientStream = messageVO.getRecipients().stream()
                    .map(recipientRequested -> new Recipient(recipientRequested, item.getId()));
            return Multi.createFrom().items(recipientStream).onItem().invoke(recipient->recipient.persist()).toUni();
        });
        return persist;
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
