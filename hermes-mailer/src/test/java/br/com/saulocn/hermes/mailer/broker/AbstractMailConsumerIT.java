package br.com.saulocn.hermes.mailer.broker;

import br.com.saulocn.hermes.mailer.entity.Recipient;
import br.com.saulocn.hermes.mailer.service.vo.RecipientVO;
import io.quarkus.mailer.MockMailbox;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker-agnostic contract for the mail consumer: publish over AMQP 1.0, the mailer consumes,
 * sends the (mocked) mail and flags the recipient as sent. Subclasses only pick the broker,
 * which is what keeps Artemis and RabbitMQ behaviourally pinned to the same expectations.
 */
abstract class AbstractMailConsumerIT {

    @Inject
    MailFixtures fixtures;

    @Inject
    MockMailbox mailbox;

    @Inject
    @Channel("mail-requests")
    Emitter<String> emitter;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    /**
     * The same recipient published twice must produce one mail, not two.
     *
     * <p>Duplicates are a normal condition, not an anomaly: MailFallbackJob republishes anything
     * still unsent, and when consumption falls behind it re-publishes messages that are already
     * sitting on the queue. Under load that produced six figures of duplicates. Suppressing them
     * has to happen where they are consumed — the broker cannot know they are the same.
     */
    @Test
    void doesNotSendTwiceForADuplicatedMessage() {
        String email = "dup-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        emitter.send(payload.toJSON());
        emitter.send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertTrue(fixtures.isSent(recipient.getId())));

        // Give the second copy time to be consumed before concluding it was suppressed —
        // asserting immediately would pass even if the duplicate were merely slow.
        await().during(Duration.ofSeconds(3))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertEquals(1, mailbox.getMailsSentTo(email).size(),
                        "duplicate message must not produce a second mail"));
    }

    @Test
    void consumesFromBrokerAndSendsMail() {
        String email = "parity-" + UUID.randomUUID() + "@hermes.test";
        Recipient recipient = fixtures.createPendingRecipient(email);

        RecipientVO payload = new RecipientVO();
        payload.setId(recipient.getId());
        payload.setEmail(email);
        payload.setMessageId(recipient.getMessageId());

        emitter.send(payload.toJSON());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals(1, mailbox.getMailsSentTo(email).size(),
                    "expected exactly one mail delivered to " + email);
            assertTrue(fixtures.isSent(recipient.getId()),
                    "recipient should be flagged as sent after consumption");
        });
    }
}
