package br.com.saulocn.hermes.api.resource.request;

import javax.json.bind.JsonbBuilder;
import java.util.List;

public class MessageVO {

    private Long id;
    private String title;
    private String text;
    private String contentType;
    private List<String> recipients;

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

    public List<String> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public byte[] getJsonRecipients() {
        return JsonbBuilder.create().toJson(recipients).getBytes();
    }


    public static MessageVO fromJSON(String json) {
        return JsonbBuilder.create().fromJson(json, MessageVO.class);
    }

    public String toJSON() {
        return JsonbBuilder.create().toJson(this);
    }
}
