package br.com.saulocn.hermes.api.service.vo;

import br.com.saulocn.hermes.api.entity.Message;
import br.com.saulocn.hermes.api.resource.request.MessageVO;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.util.Objects;

public class MailVO {

    // One instance, not one per call. JsonbBuilder.create() builds a whole provider — discovery,
    // class introspection, model construction — and Jsonb is documented as thread-safe and meant to
    // be reused. This sits on the delivery path, which runs at ~1000/s. Do NOT close it.
    private static final Jsonb JSONB = JsonbBuilder.create();

    private Long messageId;
    private String subject;
    private String text;
    private String contentType;

    public MailVO() {
    }

    public static MailVO of(MessageVO messageVO) {
        MailVO mailVO = new MailVO();
        mailVO.setMessageId(messageVO.getId());
        mailVO.setText(messageVO.getText());
        mailVO.setSubject(messageVO.getTitle());
        mailVO.setContentType(messageVO.getContentType());
        return mailVO;
    }

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
}
