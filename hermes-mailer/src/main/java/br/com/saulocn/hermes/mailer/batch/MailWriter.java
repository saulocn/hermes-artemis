package br.com.saulocn.hermes.mailer.batch;

import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.MessageService;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
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
public class MailWriter extends AbstractItemWriter {

    @Inject
    Logger log;

    @Inject
    @Channel("mail-requests")
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
                    log.info("Sent: "+recipientVO.getId());
                });
    }
}
