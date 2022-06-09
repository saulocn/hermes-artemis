package br.com.saulocn.hermes.enqueuer.batch.fallback;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import javax.batch.api.chunk.AbstractItemReader;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Iterator;

@Dependent
@Named
public class MailFallbackReader extends AbstractItemReader {

    public static final int TEN_MINUTES = 10;
    Iterator<Recipient> iterator;

    @Inject
    EntityManager entityManager;

    @Inject
    Logger log;

    @Override
    public Object readItem() throws Exception {
        log.info("Reading recipients fallback");
        if(iterator.hasNext()){
            Recipient recipient = iterator.next();
            return recipient;
        }
        return null;
    }

    @Override
    public void open(Serializable checkpoint) throws Exception {
        var recipients = entityManager.createNamedQuery(Recipient.FIND_NOT_SENT_MINUTES, Recipient.class)
                .setParameter("dateLimitToSend", LocalDateTime.now().minusMinutes(TEN_MINUTES))
                .getResultList();
        iterator = recipients.iterator();
    }
}
