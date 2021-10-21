package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.MailVO;
import br.com.saulocn.hermes.mailer.service.vo.MessageVO;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import javax.inject.Inject;
import javax.transaction.Transactional;

public class MessageService {

    @Inject
    @OnOverflow(OnOverflow.Strategy.UNBOUNDED_BUFFER)
    @Channel("mail-requests")
    Emitter<String> emitter;

    @Incoming("message-requests")
    public void sendMails(String jsonMessageVO) {
        var messageVO = MessageVO.fromJSON(jsonMessageVO);
        messageVO.getRecipients().forEach(recipient -> sendMail(messageVO, recipient));
        System.out.println(messageVO);
    }

    private void sendMail(MessageVO messageVO, String recipient) {
        var mail = MailVO.of(messageVO.getTitle(), messageVO.getText(), messageVO.getContentType(), recipient);
        emitter.send(mail.toJSON());
    }


    @Incoming("mail")
    @Transactional
    public void mailConsumer(String jsonMessageVO) {
        try {
            Thread.sleep(2000);
            System.out.println("sending email" + jsonMessageVO);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
