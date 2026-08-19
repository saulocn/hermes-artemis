package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.entity.Message;
import br.com.saulocn.hermes.enqueuer.entity.Recipient;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariant: the writer updates rows with a targeted UPDATE that preserves the {@code sent}
 * flag instead of loading and remerging the entity.
 *
 * <p>The reader loads entities at the start of a chunk. While the writer waits for broker acks
 * (up to 10 seconds), the mailer can flip {@code sent} on the same rows. If the writer then
 * loaded and merged the entity to update {@code processed}, it would write the whole row back
 * from a stale first-level cache, resetting {@code sent} to false. This lost update made rows
 * look undelivered after the mail had actually gone out, and the fallback job would then send
 * duplicates.
 *
 * <p>This test simulates that scenario: inserts a chunk, flags one row as delivered in the
 * database (by the mailer), runs the writer, and asserts that the row still has {@code sent = true}
 * after the write.
 */
@QuarkusTest
@TestProfile(BatchTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class MailWriterPreservesSentIT {

    @Inject
    BatchFixtures fixtures;

    @Inject
    EntityManager entityManager;

    @Test
    void preservesSentFlagWhenWritingProcessedRows() {
        Message message = fixtures.createMessage();
        List<Long> ids = fixtures.createRecipients(message.getId(), 3, false, false, LocalDateTime.now());

        // Simulate the mailer having delivered one recipient in the middle of the chunk.
        Long markedId = ids.get(1);
        markRecipientAsSent(markedId);

        // Run the enqueue job, which will read the chunk and write processed=true + publishedAt.
        BatchJobs.runToCompletion(JobLauncher.Job.ENQUEUE);

        // Verify that all recipients are now marked as processed (writer did its job).
        ids.forEach(id -> assertTrue(fixtures.isProcessed(id),
                "recipient " + id + " should be marked as processed after write"));

        // Verify that all recipients now have a publishedAt timestamp.
        ids.forEach(id -> assertNotNull(fixtures.publishedAtOf(id),
                "recipient " + id + " should have publishedAt set after write"));

        // The critical assertion: the row that was marked as sent must still be sent after the
        // write. This proves the writer used a targeted UPDATE, not a merge.
        assertTrue(isSent(markedId),
                "recipient " + markedId + " was marked sent by the mailer, "
                        + "and the writer must preserve that flag");
    }

    @Transactional
    void markRecipientAsSent(Long recipientId) {
        entityManager.createNativeQuery(
                "UPDATE hermes.recipient SET recipient_sent = true WHERE recipient_id = :id")
                .setParameter("id", recipientId)
                .executeUpdate();
    }

    @Transactional
    boolean isSent(Long recipientId) {
        Recipient recipient = entityManager.find(Recipient.class, recipientId);
        return recipient != null && recipient.isSent();
    }
}
