package br.com.saulocn.hermes.mailer.model;

import com.mongodb.MongoClientSettings;
import org.bson.*;
import org.bson.codecs.Codec;
import org.bson.codecs.CollectibleCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

import java.util.List;
import java.util.UUID;

public class MessagesCodec implements CollectibleCodec<Message> {

    private final Codec<Document> documentCodec;

    public MessagesCodec() {
        this.documentCodec = MongoClientSettings.getDefaultCodecRegistry().get(Document.class);
    }

    @Override
    public Message generateIdIfAbsentFromDocument(Message message) {

        if (!documentHasId(message)) {
            message.setId(UUID.randomUUID().toString());
        }
        return message;
    }

    @Override
    public boolean documentHasId(Message message) {
        return message.getId() != null;
    }

    @Override
    public BsonValue getDocumentId(Message message) {
        return new BsonString(message.getId());
    }

    @Override
    public Message decode(BsonReader bsonReader, DecoderContext decoderContext) {
        Document document = documentCodec.decode(bsonReader, decoderContext);
        Message message = new Message();
        if (document.getObjectId("_id") != null) {
            message.setId(document.getObjectId("_id").toString());
        }
        List<String> list = document.getList("recipients", String.class);
        message.setRecipients(list);
        message.setContentType(document.getString("content-type"));
        message.setText(document.getString("text"));
        message.setTitle(document.getString("title"));
        return message;
    }

    @Override
    public void encode(BsonWriter bsonWriter, Message message, EncoderContext encoderContext) {
        Document doc = new Document();
        doc.put("text", message.getText());
        doc.put("title", message.getTitle());
        doc.put("recipients", message.getRecipients());
        documentCodec.encode(bsonWriter, doc, encoderContext);
    }

    @Override
    public Class<Message> getEncoderClass() {
        return Message.class;
    }
}
