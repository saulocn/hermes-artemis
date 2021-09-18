package br.com.saulocn.hermes.api.messaging;

import br.com.saulocn.hermes.api.service.MessageService;
import br.com.saulocn.hermes.api.vo.MessageVO;
import io.smallrye.common.annotation.Blocking;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class MessageConsumer {

    @Inject
    MessageService messageService;

    @Incoming("message-in")
    @Transactional
    public void receiveMessage(String incomingMessage) {
        MessageVO messageVO = MessageVO.fromJSON(incomingMessage);
        System.out.println("Chegou! "+ messageVO.getId());
        messageService.processMessage(messageVO.getId());
    }


    @Incoming("init-request")
    @Outgoing("message-out")
    public String sendMessage(MessageVO message) {
        System.out.println(message);
        return message.toJSON();
    }
}
