package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * Publishes a chunk of recipients and only then records that they were processed.
 *
 * <p>Both batch jobs use this one writer. They used to have a writer each, differing in the
 * channel name and a log prefix — and the two channels resolved to the same address.
 */
@Dependent
@Named
public class MailWriter extends AbstractItemWriter {

    @Inject
    Logger log;

    @Inject
    EntityManager entityManager;

    @Inject
    RecipientPublisher publisher;

    @Override
    @SuppressWarnings("unchecked")
    public void writeItems(List<Object> list) throws Exception {
        List<Recipient> recipients = (List<Recipient>) (List<?>) list;

        List<RecipientVO> payloads = recipients.stream()
                .map(r -> new RecipientVO(r.getId(), r.getEmail(), r.getMessageId()))
                .toList();

        // Throws unless the broker took every one; JBeret then rolls the chunk back and the
        // update below never runs. That ordering is the whole invariant.
        publisher.publishAll(payloads);

        // A targeted update, not find()+merge(). The reader loads these entities at the start of
        // the chunk, and the mailer can flip `sent` on the same rows while we wait for acks;
        // merging the entity would write the whole row back from a stale first-level cache and
        // reset `sent` to false. That lost update made rows look undelivered after the mail had
        // gone out, and the fallback job would then send a duplicate.
        List<Long> ids = payloads.stream().map(RecipientVO::getId).toList();
        entityManager.createQuery("update Recipient r set r.processed = true where r.id in :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        ids.forEach(id -> log.info("Sent to queue: " + id));
    }
}
