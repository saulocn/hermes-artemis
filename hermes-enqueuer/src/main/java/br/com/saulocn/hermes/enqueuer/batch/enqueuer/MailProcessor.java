package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import jakarta.batch.api.chunk.ItemProcessor;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;


@Dependent
@Named
public class MailProcessor implements ItemProcessor {

    @Inject
    Logger log;

    @Override
    public Object processItem(Object o) throws Exception {
        Recipient recipient = (Recipient) o;
        log.info("Processing " + recipient.getId());
        return recipient;
    }
}
