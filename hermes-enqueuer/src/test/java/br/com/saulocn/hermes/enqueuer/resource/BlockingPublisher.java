package br.com.saulocn.hermes.enqueuer.resource;

import br.com.saulocn.hermes.enqueuer.batch.RecipientPublisher;
import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A publisher the test can hold open, so the 409 branch can be pinned against a run genuinely in
 * flight instead of a sleep-and-hope guess at timing.
 *
 * <p>Enabled only for {@link SlowJobTestProfile}. Disarmed by default — {@code publishAll}
 * returns immediately, like a broker that always accepts — so tests that only care about the
 * 200/404 branches are not slowed down. {@link #armAndBlockNextPublish()} makes the next call
 * park until {@link #release()}, and hands back a latch that opens the instant the writer is
 * actually inside the blocked call; awaiting that latch is how the test knows a run is in flight
 * for real, without polling on a timer.
 */
@Alternative
@ApplicationScoped
public class BlockingPublisher implements RecipientPublisher {

    private volatile CountDownLatch entered;
    private volatile CountDownLatch release;

    /** Arms the next {@code publishAll} to block. Returns a latch that opens once it has. */
    public CountDownLatch armAndBlockNextPublish() {
        CountDownLatch enteredLatch = new CountDownLatch(1);
        CountDownLatch releaseLatch = new CountDownLatch(1);
        entered = enteredLatch;
        release = releaseLatch;
        return enteredLatch;
    }

    /** Lets a blocked (or future) publish through. Safe to call even when nothing is armed. */
    public void release() {
        CountDownLatch releaseLatch = release;
        if (releaseLatch != null) {
            releaseLatch.countDown();
        }
        release = null;
        entered = null;
    }

    @Override
    public void publishAll(List<RecipientVO> payloads) throws InterruptedException {
        CountDownLatch releaseLatch = release;
        if (releaseLatch == null) {
            return;
        }
        entered.countDown();
        if (!releaseLatch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("BlockingPublisher: test never called release()");
        }
    }
}
