package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import jakarta.batch.api.chunk.AbstractItemReader;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.io.Serializable;
import java.util.Iterator;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
@Named
public class MailReader extends AbstractItemReader {

    Iterator<Recipient> iterator;

    @Inject
    EntityManager entityManager;

    @Inject
    Logger log;

    /**
     * Capped because this used to load every unprocessed row at once — with a large backlog that
     * meant hundreds of thousands of entities in a 1 GB container. Whatever is left over is
     * picked up by the next scheduled run.
     *
     * <p>The cap divided by the scheduler interval is a hard ceiling on delivery rate, so the two
     * have to be read together: at 30s, a cap of 1000 held the system to ~33 recipients/s —
     * measured, and a fraction of what the pipeline does. The default 30000 puts the ceiling near
     * 1000/s.
     *
     * <p>That default is now the binding constraint, not a safety margin: with the JVM tuned, a
     * 100k benchmark delivers 1190/s (Artemis) and 1429/s (RabbitMQ), both above the ceiling.
     * Raise it to go faster and watch the enqueuer's memory, because this is exactly the number
     * that bounds it.
     */
    @ConfigProperty(name = "hermes.enqueuer.max-recipients-per-run", defaultValue = "30000")
    int maxRecipientsPerRun;

    @Override
    public Object readItem() throws Exception {
        log.info("Reading recipients");
        if(iterator.hasNext()){
            Recipient recipient = iterator.next();
            return recipient;
        }
        return null;
    }

    @Override
    public void open(Serializable checkpoint) throws Exception {
        var recipients = entityManager.createNamedQuery(Recipient.FIND_NOT_PROCESSED, Recipient.class)
                .setMaxResults(maxRecipientsPerRun)
                .getResultList();
        iterator = recipients.iterator();
    }
}
