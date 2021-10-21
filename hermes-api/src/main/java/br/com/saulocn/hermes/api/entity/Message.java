package br.com.saulocn.hermes.api.entity;


import br.com.saulocn.hermes.api.resource.request.MessageVO;

import javax.persistence.*;
import java.util.List;

@Entity
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "message_sequence")
    @SequenceGenerator(name = "message_sequence", sequenceName = "sq_message")
    private Long id;
    private String title;
    private String text;
    private boolean processed;
    @Lob
    private byte[] recipients;

    public static Message of(MessageVO messageVO) {
        Message message = new Message();
        message.setText(messageVO.getText());
        message.setTitle(messageVO.getTitle());
        message.setRecipients(messageVO.getJsonRecipients());
        return message;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
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

    public void addRecipients(List<String> recipients) {
        recipients.addAll(recipients);
    }

    public byte[] getRecipients() {
        return recipients;
    }

    public void setRecipients(byte[] recipients) {
        this.recipients = recipients;
    }
}
