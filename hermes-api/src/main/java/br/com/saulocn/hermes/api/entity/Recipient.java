package br.com.saulocn.hermes.api.entity;

import javax.persistence.*;
import java.util.Objects;

@Entity
public class Recipient {


    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "sq_recipient")
    @SequenceGenerator(name = "sq_recipient", sequenceName = "sq_recipient")
    private Long id;
    private String email;
    private Long messageId;

    public Recipient() {
    }

    public Recipient(String email) {
        this.email = email;
    }

    public Recipient(String email, Long messageId) {
        this.email = email;
        this.messageId = messageId;
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
