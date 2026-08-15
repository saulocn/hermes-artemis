package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Publishes over AMQP and waits for the broker to acknowledge the whole batch. */
@ApplicationScoped
public class AmqpRecipientPublisher implements RecipientPublisher {

    /**
     * One channel for both the enqueue job and the fallback job. They used to have one each,
     * but both resolved to the same address, so the second channel was a Java-side name for a
     * queue that was already there.
     *
     * <p>The buffer has to clear the chunk size (item-count=100) with room to spare: the default
     * of 128 overflowed under a large backlog and killed the whole job with SRMSG00034.
     */
    @Inject
    @Channel("mail-requests")
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 1024)
    Emitter<String> emitter;

    /**
     * Must stay below quarkus.transaction-manager.default-transaction-timeout, otherwise the
     * chunk transaction expires before this gives up and the failure is reported as the wrong
     * thing.
     */
    @ConfigProperty(name = "hermes.enqueuer.ack-timeout-seconds", defaultValue = "10")
    long ackTimeoutSeconds;

    @Override
    public void publishAll(List<RecipientVO> payloads) throws Exception {
        List<CompletableFuture<Void>> acks = new ArrayList<>(payloads.size());
        for (RecipientVO payload : payloads) {
            acks.add(emitter.send(payload.toJSON()).toCompletableFuture());
        }

        CompletableFuture.allOf(acks.toArray(new CompletableFuture[0]))
                .get(ackTimeoutSeconds, TimeUnit.SECONDS);
    }
}
