package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class MailSenderService {


    @Inject
    Logger log;


    @ConfigProperty(name = "quarkus.mailer.from")
    String mailFrom;


    @Inject
    Mailer mailer;


    @Transactional
    public void sendHtmlMail(MailVO mailVO) {
        log.info("Sending email:" + mailVO);
        mailer.send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                .addTo(mailVO.getTo()));
        log.info("Success: "+ mailVO.getRecipientId());
    }

}
