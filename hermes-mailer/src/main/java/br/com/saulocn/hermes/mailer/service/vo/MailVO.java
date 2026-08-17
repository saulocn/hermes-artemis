package br.com.saulocn.hermes.mailer.service.vo;

import br.com.saulocn.hermes.mailer.entity.Message;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.Objects;

public class MailVO {

    // One instance, not one per call. JsonbBuilder.create() builds a whole provider — discovery,
    // class introspection, model construction — and Jsonb is documented as thread-safe and meant to
    // be reused. This sits on the delivery path, which runs at ~1000/s. Do NOT close it.
    private static final Jsonb JSONB = JsonbBuilder.create();

    private Long recipientId;
    private Long messageId;
    private String subject;
    private String text;
    private String to;
    private String contentType;

    public MailVO(Long recipientId, Long messageId, String subject, String text, String to) {
        this.recipientId=recipientId;
        this.messageId = messageId;
        this.subject = subject;
        this.text = text;
        this.to = to;
    }

    public MailVO() {}

    public static MailVO fromMessage(Message message) {
        MailVO mailVO = new MailVO();
        mailVO.setMessageId(message.getId());
        mailVO.setText(message.getText());
        mailVO.setSubject(message.getTitle());
        mailVO.setContentType(message.getContentType());
        return mailVO;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String toJSON() {
        return JSONB.toJson(this);
    }

    public static MailVO fromJSON(String json) {
        return JSONB.fromJson(json, MailVO.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MailVO mailVO = (MailVO) o;
        return Objects.equals(messageId, mailVO.messageId) && Objects.equals(subject, mailVO.subject) && Objects.equals(text, mailVO.text) && Objects.equals(to, mailVO.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, subject, text, to);
    }

    @Override
    public String toString() {
        return "MailVO{" +
                "messageId=" + messageId +
                ", subject='" + subject + '\'' +
                ", text='" + text + '\'' +
                ", to='" + to + '\'' +
                '}';
    }
}
