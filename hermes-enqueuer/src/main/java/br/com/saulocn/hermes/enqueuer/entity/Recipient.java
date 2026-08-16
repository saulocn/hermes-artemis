package br.com.saulocn.hermes.enqueuer.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@NamedQuery(name = Recipient.FIND_NOT_PROCESSED, query = "select r from Recipient r where r.processed = false")
@NamedQuery(name = Recipient.FIND_NOT_SENT_MINUTES, query = "select r from Recipient r where r.createdAt < :dateLimitToSend AND r.sent = false")
@Table(schema = "hermes", name = "recipient")
public class Recipient {

    public static final String FIND_NOT_PROCESSED = "Recipient.FindNotProcessed";
    public static final String FIND_NOT_SENT_MINUTES = "Recipient.FindNotSentMinutes";

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

    /**
     * Written by the mailer, read here.
     *
     * <p>Mapped even though this module never writes it, because the three modules all run
     * {@code drop-and-create} against their own test database: a column missing from one of them
     * means that module builds a different recipient table than the other two, and the difference
     * only shows up when a query written against the real schema runs there.
     *
     * <p>It is also what a republish policy would have to consult — today the fallback job puts a
     * recipient that has failed ten times back on the queue exactly like one that has never been
     * tried, and it could not tell them apart even if it wanted to.
     */
    @Column(name = "recipient_attempts", columnDefinition = "int not null default 0")
    private int attempts;

    @Column(name = "created_on", insertable = false, updatable = false,
            columnDefinition = "timestamp not null default now()")
    private LocalDateTime createdAt;


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

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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
