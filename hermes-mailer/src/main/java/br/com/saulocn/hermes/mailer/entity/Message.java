package br.com.saulocn.hermes.mailer.entity;


import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    // Not read here. Mapped so this module's drop-and-create builds the same message table the
    // api and the enqueuer build — a column present in one schema and absent in another is a
    // difference that only surfaces when a query written elsewhere runs here.
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
