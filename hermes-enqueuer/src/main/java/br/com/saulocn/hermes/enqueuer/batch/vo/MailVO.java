package br.com.saulocn.hermes.enqueuer.batch.vo;

import java.util.Objects;

public class MailVO {

    private Long recipientId;
    private Long messageId;
    private String subject;
    private String text;
    private String to;

    public MailVO(Long recipientId, Long messageId, String subject, String text, String to) {
        this.recipientId=recipientId;
        this.messageId = messageId;
        this.subject = subject;
        this.text = text;
        this.to = to;
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
