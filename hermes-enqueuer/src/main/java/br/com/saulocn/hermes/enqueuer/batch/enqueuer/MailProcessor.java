package br.com.saulocn.hermes.enqueuer.batch.enqueuer;

import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import org.jboss.logging.Logger;

import javax.batch.api.chunk.ItemProcessor;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;


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
