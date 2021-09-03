package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.kafka.MailVO;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import io.quarkus.mailer.reactive.ReactiveMailer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class MailSenderService {

    @ConfigProperty(name = "quarkus.mailer.from")
    String mailFrom;

    @Inject
    ReactiveMailer reactiveMailer;

    @Inject
    Mailer mailer;

    public void sendHtmlMail(MailVO mailVO) {
        System.out.println("Sending email:" + mailVO);
        mailer.send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                .addTo(mailVO.getTo())
        );
        System.out.println("sent");
    }

    public void sendAsyncHtmlMail(MailVO mailVO) {
        System.out.println("Sending email:" + mailVO);
        reactiveMailer
                .send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                        .addTo(mailVO.getTo())
                )
                .subscribe().with(
                success -> System.out.println("Sucesso"),
                error -> {
                    throw new RuntimeException(error);
                });
    }

    public void sendMail(MailVO mailVO) {
        System.out.println("Sending email:" + mailVO);
        reactiveMailer
                .send(Mail.withText(mailFrom, mailVO.getSubject(), mailVO.getText())
                                .addTo(mailVO.getTo())
                        //.addAttachment("my-file.txt", "content of my file".getBytes(), "text/plain")
                )
                .subscribe().with(
                success -> System.out.println("Sucesso"),
                error -> {
                    throw new RuntimeException(error);
                });
    }


}
