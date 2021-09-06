package br.com.saulocn.hermes.api.vo;

import javax.json.bind.JsonbBuilder;
import java.util.List;

public class MessageVO {

    private Long id;
    private String title;
    private String text;
    private List<String> recipients;



    public static MessageVO fromJSON(String json) {
        return JsonbBuilder.create().fromJson(json, MessageVO.class);
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

    public List<String> getRecipients() {
        return recipients;
    }

    public byte[] getJsonRecipients() {
        return JsonbBuilder.create().toJson(recipients).getBytes();
    }

    public void setRecipients(List<String> recipients) {
        this.recipients = recipients;
    }

    public String toJSON() {
        return JsonbBuilder.create().toJson(this);
    }
}
