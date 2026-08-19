package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.util.List;

/**
 * A broker that takes nothing.
 *
 * <p>Enabled only for {@link AckFailureTestProfile}, via getEnabledAlternatives. Before
 * RecipientPublisher existed, expressing "the broker refuses" needed a real RabbitMQ container
 * pointed at a queue that did not exist, because the emitter was injected directly into the
 * writer with nowhere to substitute.
 */
// No @Priority on purpose: that would enable the alternative application-wide and the
// happy-path tests would refuse too. Without it, only getEnabledAlternatives selects it.
@Alternative
@ApplicationScoped
public class RefusingPublisher implements RecipientPublisher {

    @Override
    public void publishAll(List<RecipientVO> payloads) {
        throw new IllegalStateException("broker refused " + payloads.size() + " payload(s)");
    }
}
