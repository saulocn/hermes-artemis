package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.entity.Message;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The invariant: a recipient is never flagged {@code processed} unless the broker took its
 * message.
 *
 * <p>Breaking it is what produced the orphan rows measured under load ({@code processed=true,
 * sent=false}) — see the "Capacidade" section of the README.
 *
 * <p>The refusal comes from a swapped RecipientPublisher, not from a broker. This used to need a
 * whole RabbitMQ container aimed at a queue that did not exist, because the emitter was injected
 * straight into the writer and there was nowhere to substitute.
 */
@QuarkusTest
@TestProfile(AckFailureTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class MailWriterAckIT {

    @Inject
    BatchFixtures fixtures;

    @Test
    void doesNotFlagProcessedWhenTheBrokerRefusesThePublish() {
        Message message = fixtures.createMessage();
        List<Long> ids = fixtures.createRecipients(message.getId(), 3, false, false, LocalDateTime.now());

        // Expected to fail — what matters is the state it leaves behind, so the terminal status
        // itself is not asserted.
        BatchJobs.runToTerminalStatus(JobLauncher.Job.ENQUEUE);

        ids.forEach(id -> assertFalse(fixtures.isProcessed(id),
                "recipient " + id + " was flagged processed even though the broker refused it"));

        // Restates the loss-prevention invariant in the new column: published_on is null
        // after a refused publish, confirming the row was never marked as sent to the broker.
        ids.forEach(id -> assertFalse(fixtures.publishedAtOf(id) != null,
                "recipient " + id + " has a non-null publishedAt even though the broker refused it"));
    }
}
