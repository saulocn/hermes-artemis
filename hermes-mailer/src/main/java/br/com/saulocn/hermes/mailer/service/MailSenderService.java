package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class MailSenderService {


    @Inject
    Logger log;


    @ConfigProperty(name = "quarkus.mailer.from")
    String mailFrom;

    @Inject
    ReactiveMailer reactiveMailer;


    @Inject
    Mailer mailer;


    public void sendHtmlMail(MailVO mailVO) {
        log.info("Sending email:" + mailVO);
        mailer.send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                .addTo(mailVO.getTo()));
        log.info("Sucesso: "+ mailVO.getRecipientId());
    }

    public void sendAsyncHtmlMail(MailVO mailVO) {
        log.info("Sending email:" + mailVO);
        reactiveMailer
                .send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                        .addTo(mailVO.getTo())
                )
                .subscribe().with(
                        success -> log.info("Sucesso: "+ mailVO.getRecipientId()),
                        error -> {
                            throw new RuntimeException(error);
                        });
    }

    public void sendMail(MailVO mailVO) {
        log.info("Sending email:" + mailVO);
        reactiveMailer
                .send(Mail.withText(mailFrom, mailVO.getSubject(), mailVO.getText())
                                .addTo(mailVO.getTo())
                        //.addAttachment("my-file.txt", "content of my file".getBytes(), "text/plain")
                )
                .subscribe().with(
                        success -> log.info("Sucesso"),
                        error -> {
                            throw new RuntimeException(error);
                        });
    }


}