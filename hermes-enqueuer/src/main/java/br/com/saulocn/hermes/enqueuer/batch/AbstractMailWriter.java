package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

import jakarta.batch.api.chunk.AbstractItemWriter;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Publishes a chunk of recipients and only then records that they were processed.
 *
 * <p>The ordering matters. {@code Emitter.send(String)} returns a {@link java.util.concurrent
 * .CompletionStage} that completes when the broker acknowledges the message; discarding it and
 * flagging {@code processed} straight away is what left orphan rows ({@code processed=true,
 * sent=false}) under load — the enqueuer claimed messages the broker never took. Waiting for the
 * acks first means a chunk commit reflects only what the broker actually accepted, and a refusal
 * or timeout rolls the whole chunk back so the next cycle retries it.
 *
 * <p>The wait is one join for the whole chunk rather than one per item: the broker acknowledges
 * concurrently, so awaiting each message in turn would serialise what is otherwise parallel.
 */
public abstract class AbstractMailWriter extends AbstractItemWriter {

    @Inject
    Logger log;

    @Inject
    EntityManager entityManager;

    /**
     * Must stay below quarkus.transaction-manager.default-transaction-timeout, otherwise the
     * chunk transaction expires before this gives up and the failure is reported as the wrong
     * thing.
     */
    @ConfigProperty(name = "hermes.enqueuer.ack-timeout-seconds", defaultValue = "10")
    long ackTimeoutSeconds;

    /** The channel this writer publishes on. */
    protected abstract Emitter<String> emitter();

    /** Prefix for the per-recipient log line, so the two writers stay distinguishable. */
    protected abstract String logPrefix();

    @Override
    @SuppressWarnings("unchecked")
    public void writeItems(List<Object> list) throws Exception {
        List<Recipient> recipients = (List<Recipient>) (List<?>) list;

        List<RecipientVO> payloads = recipients.stream()
                .map(recipient -> new RecipientVO(recipient.getId(), recipient.getEmail(), recipient.getMessageId()))
                .toList();

        List<CompletableFuture<Void>> acks = new ArrayList<>(payloads.size());
        for (RecipientVO payload : payloads) {
            acks.add(emitter().send(payload.toJSON()).toCompletableFuture());
        }

        // Throws on refusal or timeout; JBeret then rolls the chunk back and nothing below runs.
        CompletableFuture.allOf(acks.toArray(new CompletableFuture[0]))
                .get(ackTimeoutSeconds, TimeUnit.SECONDS);

        // A targeted update, not find()+merge(). The reader loads these entities at the start of
        // the chunk, and the mailer can flip `sent` on the same rows while we wait for acks
        // above; merging the entity would write the whole row back from a stale first-level
        // cache and reset `sent` to false. That lost update made rows look undelivered after
        // the mail had gone out, and the fallback job would then send a duplicate.
        List<Long> ids = payloads.stream().map(RecipientVO::getId).toList();
        entityManager.createQuery("update Recipient r set r.processed = true where r.id in :ids")
                .setParameter("ids", ids)
                .executeUpdate();

        ids.forEach(id -> log.info(logPrefix() + id));
    }
}
