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
     * <p>The cap divided by the scheduler interval is a theoretical ceiling on throughput. At
     * 100,000 ÷ 30s, that is ~3,300/s — well above what the consumer drains (~1,200/s with one
     * mailer, ~2,300/s with two), so the enqueuer is not the bottleneck. What this number really
     * governs is memory: the reader loads that many entities in one go, and at 100,000 the enqueuer
     * uses ~690 MiB of 1 GiB. See hermes-enqueuer/application.properties for full explanation.
     *
     * <p>The {@code defaultValue = "30000"} here is only the fallback when the property is absent;
     * the shipped value in application.properties is 100,000.
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
