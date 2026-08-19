package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * The queue end of the mailer: takes one message off the broker and decides what its outcome
 * means.
 *
 * <p>This is a separate module from {@link MessageService} for one reason, and it is not
 * layering. The delivery runs inside a transaction that holds a row lock on the recipient from
 * the claim until commit. Counting a failure has to happen in a <em>different</em> transaction,
 * or the rollback that requeues the message would take the counter with it — and a different
 * transaction cannot touch a row the caller still has locked without waiting for a lock the
 * caller will only release once this returns. The wait unwinds only through the 2s transaction
 * timeout, on a pool of two connections shared by thirty workers.
 *
 * <p>So the failure is counted <em>after</em> the delivery transaction has rolled back, which
 * means it cannot be counted from inside it. Splitting the entry point out is what makes that
 * ordering expressible: everything below {@code deliver} is one transaction, everything here is
 * outside it.
 */
@ApplicationScoped
public class MailConsumer {

    @Inject
    MessageService messageService;

    @Inject
    DeliveryAttempts deliveryAttempts;

    // JDBC and the SMTP client both block, so this must not run on the event loop. The named
    // pool is what smallrye.messaging.worker.mail-sender-pool.max-concurrency sizes.
    @Incoming("mail")
    @Blocking("mail-sender-pool")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public void consume(String jsonMessageVO) {
        RecipientVO recipientVO = RecipientVO.fromJSON(jsonMessageVO);

        try {
            messageService.deliver(recipientVO);
        } catch (RuntimeException e) {
            // The transaction is already rolled back by the time this runs — the interceptor
            // does that before rethrowing — so the row lock is gone and the counter can be
            // written without waiting on ourselves.
            try {
                deliveryAttempts.recordFailure(recipientVO.getId(), e);
            } catch (RuntimeException bookkeeping) {
                // Never let the accounting replace what actually went wrong: the send failure is
                // what decides whether the message is redelivered, and it is what an operator
                // reads. A counter that could not be written is a footnote to it.
                e.addSuppressed(bookkeeping);
            }
            throw e;
        }
    }
}
