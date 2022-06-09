package br.com.saulocn.hermes.enqueuer.batch.vo;

import javax.json.bind.JsonbBuilder;
import java.util.Objects;

public class RecipientVO {

    private Long id;
    private String email;
    private Long messageId;

    public RecipientVO(Long id, String email, Long messageId) {
        this.id = id;
        this.email = email;
        this.messageId = messageId;
    }


    public RecipientVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecipientVO that = (RecipientVO) o;
        return Objects.equals(email, that.email) && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(email, messageId);
    }

    @Override
    public String toString() {
        return "RecipientVO{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", messageId=" + messageId +
                '}';
    }

    public String toJSON() {
        return JsonbBuilder.create().toJson(this);
    }

    public static RecipientVO fromJSON(String json) {
        return JsonbBuilder.create().fromJson(json, RecipientVO.class);
    }
}
