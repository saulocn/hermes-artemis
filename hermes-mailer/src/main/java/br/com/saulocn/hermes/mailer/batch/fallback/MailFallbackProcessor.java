package br.com.saulocn.hermes.mailer.batch.fallback;

import br.com.saulocn.hermes.mailer.entity.Recipient;
import org.jboss.logging.Logger;

import javax.batch.api.chunk.ItemProcessor;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;


@Dependent
@Named
public class MailFallbackProcessor implements ItemProcessor {

    @Inject
    Logger log;

    @Override
    public Object processItem(Object o) throws Exception {
        Recipient recipient = (Recipient) o;
        log.info("Processing fallback: " + recipient.getId());
        return recipient;
    }
}
