package br.com.saulocn.hermes.mailer.broker;

import br.com.saulocn.hermes.mailer.entity.Message;
import br.com.saulocn.hermes.mailer.entity.Recipient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;

/**
 * Transactional helpers so the tests can seed and read state without managing a
 * transaction by hand around every assertion.
 */
@ApplicationScoped
public class MailFixtures {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Recipient createPendingRecipient(String email) {
        Message message = new Message();
        message.setTitle("Hermes broker parity");
        message.setText("<p>hello</p>");
        message.setContentType("text/html");
        entityManager.persist(message);

        Recipient recipient = new Recipient(email, message.getId());
        recipient.setProcessed(true);
        recipient.setSent(false);
        recipient.setCreatedAt(LocalDateTime.now());
        entityManager.persist(recipient);

        return recipient;
    }

    @Transactional
    public boolean isSent(Long recipientId) {
        Recipient recipient = entityManager.find(Recipient.class, recipientId);
        return recipient != null && recipient.isSent();
    }
}
