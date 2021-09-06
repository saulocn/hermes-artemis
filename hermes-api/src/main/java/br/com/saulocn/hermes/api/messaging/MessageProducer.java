package br.com.saulocn.hermes.api.messaging;

import br.com.saulocn.hermes.api.vo.MessageVO;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.*;

@ApplicationScoped
public class MessageProducer {

    @Inject
    ConnectionFactory connectionFactory;

    public void send(MessageVO messageVO) {
        try (JMSContext context = connectionFactory.createContext(Session.CLIENT_ACKNOWLEDGE)) {
            JMSProducer producer = context.createProducer();
            producer.send(context.createQueue("MessageQueue"), messageVO.toJSON());
        }
    }
}
