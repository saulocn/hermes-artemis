package br.com.saulocn.hermes.mailer.service;

import br.com.saulocn.hermes.mailer.broker.InfraTestResource;
import br.com.saulocn.hermes.mailer.broker.MailFixtures;
import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A send that throws has to leave two traces: the claim rolled back, and the attempt counted.
 *
 * <p>The counter cannot be written inside the delivery transaction, because the rollback that
 * requeues the message would undo it. It also cannot be written from a second transaction while
 * the first is still open: the delivery holds a row lock on that recipient from the claim until
 * commit, and a second transaction on the same thread would wait for a lock only that thread can
 * release. Postgres sees no deadlock — one side of the cycle is application code — so it unwinds
 * through the transaction timeout, at 2s in production and 30s here.
 *
 * <p>That is what the ten-second budget below is for. It is not a performance assertion; it is
 * the distance between "counted after the delivery transaction ended" and "counted by waiting out
 * a timeout", which no amount of machine speed can close.
 *
 * <p>Checked against the arrangement it replaces: moving the {@code recordFailure} call back
 * inside {@code deliver} makes this test error out in about five seconds with
 * {@code Unable to acquire JDBC Connection [Sorry, acquisition timeout!]} — it never even reaches
 * the row lock, because the second transaction wants a second connection out of a pool of two
 * while the first still holds one. That is the production failure mode, at thirty workers.
 */
@QuarkusTest
@TestProfile(FailingSendTestProfile.class)
@WithTestResource(InfraTestResource.class)
class DeliveryFailureIT {

    @Inject
    MailFixtures fixtures;

    @Inject
    @Any
    InMemoryConnector connector;

    @Test
    void aFailedSendIsCountedWithoutWaitingOnItsOwnRowLock() {
        String email = "fail-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        connector.source("mail").send(payload.toJSON());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(
                () -> assertEquals(1, fixtures.attemptsOf(recipient.getId()),
                        "the failed attempt should be recorded"));

        // The claim went back with the transaction, which is what puts the message on the queue
        // again. A counted failure that also left the row claimed would be a lost mail.
        assertFalse(fixtures.isSent(recipient.getId()),
                "a failed send must not leave the recipient claimed");

        // Rollback of the delivery transaction also rolled back the claim timestamp,
        // proving the invariant: claimedAt is null only when the send failed and rolled back.
        assertNull(fixtures.claimedAtOf(recipient.getId()),
                "claimedAt must be null after a failed send (the rollback took it)");
    }
}
