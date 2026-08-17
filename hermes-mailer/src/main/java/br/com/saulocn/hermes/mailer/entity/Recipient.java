package br.com.saulocn.hermes.mailer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(schema = "hermes", name = "recipient")
public class Recipient {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "recipient_seq")
    @SequenceGenerator(name = "recipient_seq", sequenceName = "recipient_seq", allocationSize = 1)
    @Column(name = "recipient_id")
    private Long id;

    @Column(name = "recipient_mail")
    private String email;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "recipient_sent")
    private boolean sent;

    @Column(name = "recipient_processed")
    private boolean processed;

    // Written by the consumer in its own transaction, so a failed send leaves a trace even
    // though the rollback undoes everything else. Zero means "never failed", not "never tried".
    @Column(name = "recipient_attempts", columnDefinition = "int not null default 0")
    private int attempts;

    @Column(name = "created_on", insertable = false, updatable = false,
            columnDefinition = "timestamp not null default now()")
    private LocalDateTime createdAt;

    // When the recipient was published (moved to a queue). Written by bulk JPQL UPDATE, never through the entity.
    @Column(name = "published_on")
    private LocalDateTime publishedAt;

    // When the recipient was claimed (in the delivery transaction). Written by bulk JPQL UPDATE, never through the entity.
    @Column(name = "claimed_on")
    private LocalDateTime claimedAt;

    public Recipient() {
    }

    public Recipient(String email) {
        this.email = email;
    }

    public Recipient(String email, Long messageId) {
        this.email = email;
        this.messageId = messageId;
    }

    public boolean isSent() {
        return sent;
    }

    public void setSent(boolean sent) {
        this.sent = sent;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Recipient recipient = (Recipient) o;
        return Objects.equals(email, recipient.email) && Objects.equals(messageId, recipient.messageId);
    }


    @Override
    public int hashCode() {
        return Objects.hash(email, messageId);
    }

    @Override
    public String toString() {
        return "Recipient{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", messageId=" + messageId +
                '}';
    }
}
