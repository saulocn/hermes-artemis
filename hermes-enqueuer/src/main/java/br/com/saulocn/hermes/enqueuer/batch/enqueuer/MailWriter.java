package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import br.com.saulocn.hermes.enqueuer.batch.AbstractMailWriter;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Dependent
@Named
public class MailWriter extends AbstractMailWriter {

    /**
     * The buffer has to clear the chunk size (item-count=100) with room to spare: the default of
     * 128 overflowed under a large backlog and killed the whole job with SRMSG00034.
     */
    @Inject
    @Channel("mail-requests")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1024)
    Emitter<String> emitter;

    @Override
    protected Emitter<String> emitter() {
        return emitter;
    }

    @Override
    protected String logPrefix() {
        return "Sent to queue: ";
    }
}
