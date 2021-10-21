package br.com.saulocn.hermes.mailer.service.vo;

import javax.json.bind.JsonbBuilder;

public class MailVO {

    private Long id;
    private String title;
    private String text;
    private String contentType;
    private String recipient;

    public static MailVO of(String title, String text, String contentType, String recipient) {
        MailVO mail = new MailVO();
        mail.setTitle(title);
        mail.setText(text);
        mail.setContentType(contentType);
        mail.setRecipient(recipient);
        return mail;
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

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }


    public String toJSON() {
        return JsonbBuilder.create().toJson(this);
    }
}
