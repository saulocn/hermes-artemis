package br.com.saulocn.hermes.api.entity;


import br.com.saulocn.hermes.api.resource.request.MessageVO;

import javax.persistence.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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


    public static Message of(MessageVO messageVO) {
        Message message = new Message();
        message.setText(messageVO.getText());
        message.setTitle(messageVO.getTitle());
        message.setContentType(messageVO.getContentType());
        return message;
    }

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
