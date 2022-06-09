package br.com.saulocn.hermes.enqueuer.batch.fallback;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.util.List;


@Dependent
@Named
public class MailFallbackWriter extends AbstractItemWriter {

    @Inject
    Logger log;

    @Inject
    @Channel("mail-fallback-request")
    Emitter<String> emitter;


    @Inject
    EntityManager entityManager;

    @Override
    public void writeItems(List<Object> list) throws Exception {
        List<Recipient> recipients = (List<Recipient>)(List<?>) list;
        recipients.stream()
                .map(recipient -> new RecipientVO(recipient.getId(), recipient.getEmail(), recipient.getMessageId()))
                .forEach(recipientVO -> {
                    emitter.send(recipientVO.toJSON());
                    Recipient recipient = entityManager.find(Recipient.class, recipientVO.getId());
                    recipient.setProcessed(true);
                    entityManager.merge(recipient);
                    log.info("Sent to queue(fallback): "+recipientVO.getId());
                });
    }
}
