package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;

import java.util.List;

/**
 * Publishes a batch of recipients and returns only once the broker has taken all of them.
 *
 * <p>The whole point of this interface is the word "only". Returning normally is a promise that
 * every payload was acknowledged; anything else throws. That promise is what lets the caller
 * record {@code processed = true} safely — discarding the acknowledgement and flagging the rows
 * anyway is what left orphan rows under load, with the enqueuer claiming messages the broker
 * never took.
 *
 * <p>Behind this: fan-out over the channel, a single wait for all acknowledgements rather than
 * one per message (the broker acknowledges concurrently, so awaiting in turn would serialise
 * what is parallel), and the timeout that has to stay below the chunk's transaction timeout.
 *
 * <p>It is an interface because two things satisfy it: the AMQP adapter in production, and a
 * refusing adapter in the test that pins the invariant. Before the seam existed that test needed
 * a whole RabbitMQ container configured to reject, because there was nowhere to substitute.
 */
public interface RecipientPublisher {

    /**
     * @throws Exception if the broker refuses any payload, or does not acknowledge all of them
     *                   within the configured timeout. The caller is expected to let this
     *                   propagate so the chunk rolls back and the next cycle retries.
     */
    void publishAll(List<RecipientVO> payloads) throws Exception;
}
