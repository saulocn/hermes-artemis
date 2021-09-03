package br.com.saulocn.hermes.mailer.model;

import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecProvider;
import org.bson.codecs.configuration.CodecRegistry;

public class MessageCodecProvider implements CodecProvider {
    @Override
    public <T> Codec<T> get(Class<T> aClass, CodecRegistry codecRegistry) {
        if (aClass == Message.class) {
            return (Codec<T>) new MessagesCodec();
        }
        return null;
    }
}
