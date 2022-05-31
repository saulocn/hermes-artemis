package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Stateless
public class MessageService {

    @Inject
    Logger log;

    @Inject
    MailSenderService mailSenderService;


    @Inject
    EntityManager entityManager;


    @Incoming("mail")
    @Blocking("mail-sender-pool")
    @Transactional
    public void mailConsumer(String jsonMessageVO) {
        RecipientVO recipientVO = RecipientVO.fromJSON(jsonMessageVO);
        Message message = entityManager.find(Message.class, recipientVO.getMessageId());
        Recipient recipient = entityManager.find(Recipient.class, recipientVO.getId());
        MailVO mailVO = new MailVO(recipientVO.getMessageId(), message.getTitle(), message.getText(), recipientVO.getEmail());
        mailSenderService.sendAsyncHtmlMail(mailVO);
        recipient.setSent(true);
        entityManager.merge(recipient);
        log.info("Sendint email: "+jsonMessageVO);
    }

}
