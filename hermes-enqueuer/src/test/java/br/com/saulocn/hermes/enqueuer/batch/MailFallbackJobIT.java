package br.com.saulocn.hermes.enqueuer.batch;

import br.com.saulocn.hermes.enqueuer.batch.vo.RecipientVO;
import br.com.saulocn.hermes.enqueuer.entity.Message;
import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the safety net: recipients already published but still unsent after the 10 minute
 * window get republished, while anything inside the window is left alone.
 */
@QuarkusTest
@TestProfile(BatchTestProfile.class)
@WithTestResource(PostgresTestResource.class)
class MailFallbackJobIT {

    @Inject
    BatchFixtures fixtures;

    @Inject
    @Any
    InMemoryConnector connector;

    private InMemorySink<String> sink;

    @BeforeEach
    void resetSink() {
        sink = connector.sink("mail-fallback-request");
        sink.clear();
    }

    @Test
    void republishesOnlyRecipientsOlderThanTheWindow() {
        Message message = fixtures.createMessage();
        List<Long> stale = fixtures.createRecipients(message.getId(), 2, true, false,
                LocalDateTime.now().minusMinutes(30));
        List<Long> fresh = fixtures.createRecipients(message.getId(), 2, true, false,
                LocalDateTime.now());

        BatchJobs.runToCompletion("mail-fallback-chunk");

        Set<Long> published = sink.received().stream()
                .map(m -> RecipientVO.fromJSON(m.getPayload()).getId())
                .collect(Collectors.toSet());

        assertTrue(published.containsAll(stale),
                "stale recipients should be republished, published=" + published + " stale=" + stale);
        fresh.forEach(id -> assertFalse(published.contains(id),
                "recipient " + id + " is inside the 10 minute window and should not be republished"));
    }
}
