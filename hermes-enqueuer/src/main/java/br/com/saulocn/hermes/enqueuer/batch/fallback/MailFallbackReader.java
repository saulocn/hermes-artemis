package br.com.saulocn.hermes.enqueuer.batch.fallback;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Iterator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
@Named
public class MailFallbackReader extends AbstractItemReader {

    public static final int TEN_MINUTES = 10;
    Iterator<Recipient> iterator;

    @Inject
    EntityManager entityManager;

    @Inject
    Logger log;

    /** Same cap and same reasoning as MailReader.MAX_RECIPIENTS_PER_RUN. */
    @ConfigProperty(name = "hermes.enqueuer.max-recipients-per-run", defaultValue = "30000")
    int maxRecipientsPerRun;

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
                .setMaxResults(maxRecipientsPerRun)
                .getResultList();
        iterator = recipients.iterator();
    }
}
