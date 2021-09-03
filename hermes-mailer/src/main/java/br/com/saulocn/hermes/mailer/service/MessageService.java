package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.model.Message;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.types.ObjectId;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@ApplicationScoped
public class MessageService {

    @Inject
    MongoClient mongoClient;

    public List<Message> list() {
        List<Message> messages = new ArrayList<>();
        MongoCursor<Message> cursor = getCollection().find().iterator();
        try {
            while (cursor.hasNext()) {
                messages.add(cursor.next());
            }
        } finally {
            cursor.close();
        }

        return messages;
    }

    public Message findById(String id) {
        return (Message) getCollection().find(eq("_id", new ObjectId(id))).first();
    }


    private MongoCollection getCollection() {
        return mongoClient.getDatabase("hermes").getCollection("messages", Message.class);
    }
}
