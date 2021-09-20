package br.com.saulocn.hermes.api.messaging;

import br.com.saulocn.hermes.api.service.MessageService;
import br.com.saulocn.hermes.api.vo.MessageVO;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class MessageConsumer implements Runnable {

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    MessageService messageService;

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();


    void onStart(@Observes StartupEvent ev) {
        scheduler.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.shutdown();
    }

    @Override
    public void run() {
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            JMSConsumer consumer = context.createConsumer(context.createQueue("MessageQueue"));
            while (true) {
                Message message = consumer.receive();
                String mensagem = message.getBody(String.class);
                System.out.println(mensagem);
                messageService.process(MessageVO.fromJSON(mensagem));
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }
}
