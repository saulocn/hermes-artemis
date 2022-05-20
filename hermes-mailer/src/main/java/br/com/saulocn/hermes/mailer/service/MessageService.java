package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.smallrye.reactive.messaging.annotations.Blocking;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.transaction.Transactional;

@Stateless
public class MessageService {

    @Inject
    Logger log;


    @Incoming("mail")
    @Blocking("mail-sender-pool")
    @Transactional
    public void mailConsumer(String jsonMessageVO) {
        try {
            Thread.sleep(1000);
            System.out.println("sending email" + jsonMessageVO);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
