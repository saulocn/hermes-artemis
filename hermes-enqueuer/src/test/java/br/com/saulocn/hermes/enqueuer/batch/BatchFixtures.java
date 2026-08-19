package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.entity.Message;
import br.com.saulocn.hermes.enqueuer.entity.Recipient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class BatchFixtures {

    @Inject
    EntityManager entityManager;

    @Transactional
    public Message createMessage() {
        Message message = new Message();
        message.setTitle("Hermes batch");
        message.setText("<p>hello</p>");
        message.setContentType("text/html");
        entityManager.persist(message);
        return message;
    }

    /**
     * @param processed  what MailReader (FIND_NOT_PROCESSED) filters on
     * @param sent       what MailFallbackReader (FIND_NOT_SENT_MINUTES) filters on
     * @param createdAt  the fallback window is relative to this
     */
    @Transactional
    public List<Long> createRecipients(Long messageId, int count, boolean processed, boolean sent,
                                       LocalDateTime createdAt) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Recipient recipient = new Recipient("batch-" + java.util.UUID.randomUUID() + "@hermes.test", messageId);
            recipient.setProcessed(processed);
            recipient.setSent(sent);
            entityManager.persist(recipient);
            ids.add(recipient.getId());
        }
        entityManager.flush();
        // created_on is database-defaulted and mapped read-only (insertable=false, updatable=false);
        // backdating must be done via an explicit UPDATE, not through the mapped field.
        for (Long id : ids) {
            entityManager.createNativeQuery("update hermes.recipient set created_on = :createdAt where recipient_id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", id)
                    .executeUpdate();
        }
        return ids;
    }

    @Transactional
    public boolean isProcessed(Long recipientId) {
        Recipient recipient = entityManager.find(Recipient.class, recipientId);
        return recipient != null && recipient.isProcessed();
    }

    @Transactional
    public LocalDateTime publishedAtOf(Long recipientId) {
        Recipient recipient = entityManager.find(Recipient.class, recipientId);
        return recipient != null ? recipient.getPublishedAt() : null;
    }
}
