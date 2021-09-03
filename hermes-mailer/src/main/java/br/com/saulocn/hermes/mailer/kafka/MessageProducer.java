package br.com.saulocn.hermes.mailer.kafka;

import br.com.saulocn.hermes.mailer.model.Message;
import br.com.saulocn.hermes.mailer.service.MailSenderService;
import br.com.saulocn.hermes.mailer.service.MessageService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.smallrye.mutiny.groups.UniSubscribe;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class MessageProducer {

    AtomicInteger counter = new AtomicInteger();

    @ConfigProperty(name = "quarkus.mailer.from")
    String mailFrom;

    @Inject
    ReactiveMailer reactiveMailer;
    @Inject
    @Channel("mailer")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 10000)
    Emitter<MailVO> emitter;
    @Inject
    MessageService messageService;
    @Inject
    MailSenderService mailSenderService;

    @Incoming("init")
    @Outgoing("messages-out")
    public String sendMessage(String message) {
        return message;
    }

    @Incoming("mailer")
    @Outgoing("mail-out")
    public String sendMessage(MailVO mailVO) {
        System.out.println("Enviando mailVO: " + mailVO);
        return mailVO.toJSON();
    }

    @Incoming("messages-in")
    @Blocking(value = "message-sender-pool")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public void receiveMessage(String incomingMessage) {
        Message message = messageService.findById(incomingMessage);
        message.getRecipients().forEach(
                recipient -> emitter.send(
                        MailVO.of(message.getTitle(),
                                message.getText(),
                                message.getContentType(),
                                recipient
                        )));
    }

    @Incoming("mail-in")
    @Blocking(value = "mail-sender-pool")
    @Acknowledgment(Acknowledgment.Strategy.POST_PROCESSING)
    public CompletableFuture<Void> receiveMail(org.eclipse.microprofile.reactive.messaging.Message<String> jsonMail) throws InterruptedException {
        MailVO mailVO = MailVO.fromJSON(jsonMail.getPayload());
        UniSubscribe<Void> subscribe = reactiveMailer
                .send(Mail.withHtml(mailFrom, mailVO.getSubject(), mailVO.getText())
                        .addTo(mailVO.getTo())
                )
                .subscribe();
        subscribe.with(success -> {
                    System.out.println("Mail sent! " + counter.incrementAndGet());
                },
                error -> {
                    System.out.println("error! " + error.getMessage());
                });
        return subscribe.asCompletionStage();
    }


}
