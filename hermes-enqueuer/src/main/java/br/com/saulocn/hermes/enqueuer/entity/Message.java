package br.com.saulocn.hermes.enqueuer.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * The message this module never sends, only counts rows against.
 *
 * <p>Columns it does not use are mapped anyway: each module runs {@code drop-and-create} against
 * its own test database, so a column missing here builds a different message table than the api
 * and the mailer build, and the schemas only diverge where nobody is looking.
 */
@Entity
@Table(schema = "hermes", name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_seq")
    @SequenceGenerator(name = "message_seq", sequenceName = "message_seq", allocationSize = 1)
    @Column(name = "message_id")
    private Long id;

    @Column(name = "message_title")
    private String title;

    @Column(name = "message_text")
    private String text;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "created_on", insertable = false, updatable = false,
            columnDefinition = "timestamp not null default now()")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
