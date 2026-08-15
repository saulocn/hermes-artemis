package br.com.saulocn.hermes.enqueuer.batch.fallback;

import br.com.saulocn.hermes.enqueuer.batch.AbstractMailWriter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Republishes recipients the mailer has not delivered yet.
 *
 * <p>Note that this sets {@code processed}, while {@link MailFallbackReader} selects on
 * {@code sent = false}. That is deliberate, not an oversight: the flag written here does not
 * narrow this job's own selection, so a recipient keeps being republished every cycle until the
 * mailer marks it sent. That repetition is the safety net — it is what recovers messages the
 * broker dropped.
 */
@Dependent
@Named
public class MailFallbackWriter extends AbstractMailWriter {

    /** Same reasoning as MailWriter: the default buffer of 128 is smaller than a chunk needs. */
    @Inject
    @Channel("mail-fallback-request")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1024)
    Emitter<String> emitter;

    @Override
    protected Emitter<String> emitter() {
        return emitter;
    }

    @Override
    protected String logPrefix() {
        return "Sent to queue(fallback): ";
    }
}
