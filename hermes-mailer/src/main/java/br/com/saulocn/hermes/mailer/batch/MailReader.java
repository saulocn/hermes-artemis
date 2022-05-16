package br.com.saulocn.hermes.mailer.batch;

import javax.batch.api.chunk.AbstractItemReader;
import java.io.Serializable;

public class MailReader extends AbstractItemReader {
    @Override
    public Object readItem() throws Exception {
        return null;
    }

    @Override
    public void open(Serializable checkpoint) throws Exception {

    }
}
