package br.com.saulocn.hermes.mailer.batch.enqueuer;

import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.MessageService;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import org.jboss.logging.Logger;

import javax.batch.api.chunk.AbstractItemWriter;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.inject.Named;
import javax.transaction.Transactional;
import java.util.List;


@Dependent
@Named
public class MailWriter extends AbstractItemWriter {

    @Inject
    Logger log;

    @Inject
    MessageService messageService;

    @Override
    @Transactional
    public void writeItems(List<Object> list) throws Exception {
        List<Recipient> recipients = (List<Recipient>)(List<?>) list;
        recipients.stream()
                .map(recipient -> new RecipientVO(recipient.getId(), recipient.getEmail(), recipient.getMessageId()))
                .forEach(recipientVO -> messageService.sentToMailQueue(recipientVO));
    }
}
