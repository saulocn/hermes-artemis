package br.com.saulocn.hermes.mailer.entity;

import javax.persistence.*;
import java.util.Objects;

@Entity
@NamedQuery(name = Recipient.FIND_NOT_PROCESSED, query = "select r from Recipient r where r.processed = false")
@Table(schema = "hermes", name = "recipient")
public class Recipient {

    public static final String FIND_NOT_PROCESSED = "Message.FindNotProcessed";

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
