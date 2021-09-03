package br.com.saulocn.hermes.mailer.kafka;


import io.vertx.core.json.JsonObject;

import javax.json.bind.JsonbBuilder;

public class MailVO extends JsonObject {

    public String subject;
    public String text;
    public String contentType;
    public String to;

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

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public static MailVO of(String subject, String text, String contentType, String to) {
        MailVO mailVO = new MailVO();
        mailVO.setSubject(subject);
        mailVO.setText(text);
        mailVO.setTo(to);
        mailVO.setContentType(contentType);
        return mailVO;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("MailVO{");
        sb.append("subject='").append(subject).append('\'');
        sb.append(", text='").append(text).append('\'');
        sb.append(", contentType='").append(contentType).append('\'');
        sb.append(", to='").append(to).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String toJSON() {
        return JsonbBuilder.create().toJson(this);
    }

    public static MailVO fromJSON(String json) {
        return JsonbBuilder.create().fromJson(json, MailVO.class);
    }
}
