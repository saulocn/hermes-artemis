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
 * The invariant this pins down: a recipient is never flagged {@code processed} unless the broker
 * actually accepted its message.
 *
 * <p>Breaking that invariant is what produced the orphan rows measured under load
 * ({@code processed=true, sent=false}) — see the "Capacidade" section of the README. The broker
 * here refuses every publish, so after the job runs nothing may be marked.
 */
@QuarkusTest
@TestProfile(BrokerAckTestProfile.class)
@WithTestResource(PostgresTestResource.class)
@WithTestResource(RejectingBrokerTestResource.class)
class MailWriterAckIT {

    @Inject
    BatchFixtures fixtures;

    @Test
    void doesNotFlagProcessedWhenBrokerRefusesThePublish() {
        Message message = fixtures.createMessage();
        List<Long> ids = fixtures.createRecipients(message.getId(), 3, false, false, LocalDateTime.now());

        // The job is expected to fail here — the broker refuses. What matters is the DB state
        // it leaves behind, so the terminal status itself is not asserted.
        BatchJobs.runToTerminalStatus("mail-enqueuer-chunk");

        ids.forEach(id -> assertFalse(fixtures.isProcessed(id),
                "recipient " + id + " was flagged processed even though the broker refused the message"));
    }
}
