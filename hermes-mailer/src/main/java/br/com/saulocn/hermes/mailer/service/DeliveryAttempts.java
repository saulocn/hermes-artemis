package br.com.saulocn.hermes.mailer.service;

import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Records that a delivery was attempted and did not stick.
 *
 * <p>This exists in its own transaction for one reason: the consumer's transaction is what rolls
 * back when a send fails, and it would take the counter with it. Writing the attempt in a
 * separate transaction is what makes the failure outlive the rollback that hides it.
 *
 * <p>Without this, a recipient whose send keeps throwing is indistinguishable from one waiting in
 * the queue — both read {@code processed = true, sent = false} — so the dashboard counted a
 * permanently failing message as "in flight" forever.
 *
 * <p>Counting here rather than reading the broker's own redelivery count is deliberate: reaching
 * that metadata means consuming a {@code Message<String>}, and SmallRye then requires the
 * consumer to return {@code CompletionStage<Void>} and acknowledge by hand. That is surgery on
 * the one path that already lost messages once, for a number this side can simply count.
 */
@ApplicationScoped
public class DeliveryAttempts {

    @Inject
    Logger log;

    @Inject
    EntityManager entityManager;

    /**
     * Called only when a send throws, so the happy path writes nothing — an extra transaction
     * per message would be real cost at ~1200/s.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordFailure(Long recipientId, Exception cause) {
        entityManager.createQuery(
                        "update Recipient r set r.attempts = r.attempts + 1 where r.id = :id")
                .setParameter("id", recipientId)
                .executeUpdate();
        log.warnf("Delivery to recipient %d failed: %s", recipientId, cause.toString());
    }
}
